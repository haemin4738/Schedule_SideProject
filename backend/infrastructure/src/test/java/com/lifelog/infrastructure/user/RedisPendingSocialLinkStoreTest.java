package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.social.PendingSocialLink;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RedisPendingSocialLinkStore 통합 테스트 — 실제 Redis(REDIS_HOST/REDIS_PORT) 사용.
 * 발급한 키는 각 테스트 후 삭제한다.
 */
class RedisPendingSocialLinkStoreTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private RedisPendingSocialLinkStore store;
    private final List<String> issuedTokens = new ArrayList<>();

    private static final PendingSocialLink LINK = new PendingSocialLink(42L, SocialProvider.KAKAO, "kakao-777");

    @BeforeAll
    static void connect() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        store = new RedisPendingSocialLinkStore(redis);
    }

    @AfterEach
    void cleanUp() {
        issuedTokens.forEach(t -> redis.delete(RedisPendingSocialLinkStore.key(t)));
    }

    private String issue(Duration ttl) {
        String token = store.issue(LINK, ttl);
        issuedTokens.add(token);
        return token;
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void issue_whenValid_returnsOpaqueBase64UrlTokenAndFindReturnsLink() {
        String token = issue(Duration.ofMinutes(10));

        // 32바이트 base64url(패딩 없음) = 43자
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(store.find(token)).contains(LINK);
    }

    @Test
    void issue_whenCalledTwice_returnsDifferentTokens() {
        assertThat(issue(Duration.ofMinutes(10))).isNotEqualTo(issue(Duration.ofMinutes(10)));
    }

    @Test
    void issue_whenStored_keyIsSha256OfTokenAndRawTokenNotStored() throws Exception {
        String token = issue(Duration.ofMinutes(10));
        String expectedKey = "auth:social-link:" + sha256Hex(token);

        assertThat(RedisPendingSocialLinkStore.key(token)).isEqualTo(expectedKey);
        assertThat(redis.hasKey(expectedKey)).isTrue();
        assertThat(redis.hasKey("auth:social-link:" + token)).isFalse();

        Map<Object, Object> fields = redis.opsForHash().entries(expectedKey);
        assertThat(fields).containsOnly(
                Map.entry("userId", "42"),
                Map.entry("provider", "KAKAO"),
                Map.entry("providerUserId", "kakao-777"),
                Map.entry("attempts", "0"));
        assertThat(fields.values()).doesNotContain(token);

        Set<String> keys = redis.keys("*" + token + "*");
        assertThat(keys).isEmpty();
    }

    @Test
    void issue_whenTtlGiven_setsExpireOnKey() {
        String token = issue(Duration.ofMinutes(10));

        Long ttlMillis = redis.getExpire(RedisPendingSocialLinkStore.key(token), TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isBetween(Duration.ofMinutes(9).toMillis(), Duration.ofMinutes(10).toMillis());
    }

    @Test
    void find_whenTtlElapsed_returnsEmpty() throws Exception {
        String token = issue(Duration.ofMillis(150));

        Thread.sleep(400);

        assertThat(store.find(token)).isEmpty();
    }

    @Test
    void issue_whenInvalidArguments_throwsIllegalArgument() {
        assertThatThrownBy(() -> store.issue(null, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issue(new PendingSocialLink(null, SocialProvider.KAKAO, "x"), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issue(new PendingSocialLink(1L, null, "x"), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issue(new PendingSocialLink(1L, SocialProvider.KAKAO, null), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issue(LINK, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issue(LINK, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issue(LINK, Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void operations_whenTokenBlank_returnEmptyWithoutRedisCall(String token) {
        assertThat(store.find(token)).isEmpty();
        assertThat(store.incrementAttempts(token)).isZero();
        assertThat(store.consume(token)).isFalse();
    }

    @Test
    void find_whenUnknownToken_returnsEmpty() {
        assertThat(store.find("unknown-token-" + System.nanoTime())).isEmpty();
    }

    @Test
    void find_whenStoredValueCorrupted_returnsEmpty() {
        String token = issue(Duration.ofMinutes(10));
        redis.opsForHash().put(RedisPendingSocialLinkStore.key(token), "provider", "APPLE");

        assertThat(store.find(token)).isEmpty();
    }

    @Test
    void find_whenProviderUserIdMissing_returnsEmpty() {
        String token = issue(Duration.ofMinutes(10));
        redis.opsForHash().delete(RedisPendingSocialLinkStore.key(token), "providerUserId");

        assertThat(store.find(token)).isEmpty();
    }

    @Test
    void incrementAttempts_whenTokenExists_incrementsSequentially() {
        String token = issue(Duration.ofMinutes(10));

        assertThat(store.incrementAttempts(token)).isEqualTo(1);
        assertThat(store.incrementAttempts(token)).isEqualTo(2);
        assertThat(store.incrementAttempts(token)).isEqualTo(3);
        assertThat(redis.opsForHash().get(RedisPendingSocialLinkStore.key(token), "attempts")).isEqualTo("3");
        // 증가해도 TTL 은 유지된다
        assertThat(redis.getExpire(RedisPendingSocialLinkStore.key(token), TimeUnit.SECONDS)).isPositive();
    }

    @Test
    void incrementAttempts_whenTokenMissing_returnsZeroAndDoesNotCreateKey() {
        String token = "missing-" + System.nanoTime();
        issuedTokens.add(token);

        assertThat(store.incrementAttempts(token)).isZero();
        assertThat(redis.hasKey(RedisPendingSocialLinkStore.key(token))).isFalse();
    }

    @Test
    void consume_whenCalledTwice_returnsTrueOnlyOnce() {
        String token = issue(Duration.ofMinutes(10));

        assertThat(store.consume(token)).isTrue();
        assertThat(store.consume(token)).isFalse();
        assertThat(store.find(token)).isEmpty();
        assertThat(store.incrementAttempts(token)).isZero();
    }

    @Test
    void consume_whenConcurrent_onlyOneCallerSucceeds() throws Exception {
        String token = issue(Duration.ofMinutes(10));
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(() -> store.consume(token));
            }
            long successes = 0;
            for (Future<Boolean> f : executor.invokeAll(calls)) {
                if (f.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void operations_whenRedisUnavailable_throwServiceUnavailable() {
        LettuceConnectionFactory down = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        down.afterPropertiesSet();
        down.start();
        try {
            RedisPendingSocialLinkStore brokenStore = new RedisPendingSocialLinkStore(new StringRedisTemplate(down));

            assertThatThrownBy(() -> brokenStore.issue(LINK, Duration.ofMinutes(1)))
                    .isInstanceOf(SocialAuthException.class)
                    .extracting(e -> ((SocialAuthException) e).getReason())
                    .isEqualTo(SocialAuthException.Reason.SERVICE_UNAVAILABLE);
            assertThatThrownBy(() -> brokenStore.find("token"))
                    .isInstanceOf(SocialAuthException.class);
            assertThatThrownBy(() -> brokenStore.incrementAttempts("token"))
                    .isInstanceOf(SocialAuthException.class);
            assertThatThrownBy(() -> brokenStore.consume("token"))
                    .isInstanceOf(SocialAuthException.class);
        } finally {
            down.destroy();
        }
    }
}
