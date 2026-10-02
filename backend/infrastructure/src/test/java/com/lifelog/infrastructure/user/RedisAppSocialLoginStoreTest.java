package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.social.AppLoginTicket;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RedisAppSocialLoginStore 통합 테스트 — 실제 Redis(REDIS_HOST/REDIS_PORT) 사용.
 * 발급한 키는 각 테스트 후 삭제한다.
 */
class RedisAppSocialLoginStoreTest {

    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final SocialUserInfo INFO =
            new SocialUserInfo(SocialProvider.NAVER, "naver-uid", "n@naver.com", true, "네이버이름");

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private RedisAppSocialLoginStore store;
    private final List<String> usedKeys = new ArrayList<>();

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
        store = new RedisAppSocialLoginStore(redis);
    }

    @AfterEach
    void cleanUp() {
        if (!usedKeys.isEmpty()) {
            redis.delete(usedKeys);
        }
    }

    private String issueState() {
        String state = store.issueState(CHALLENGE, Duration.ofMinutes(10));
        usedKeys.add(RedisAppSocialLoginStore.stateKey(state));
        return state;
    }

    private String issueTicket(AppLoginTicket ticket) {
        String raw = store.issueTicket(ticket, TTL);
        usedKeys.add(RedisAppSocialLoginStore.ticketKey(raw));
        return raw;
    }

    // ---------- state ----------

    @Test
    void issueState_whenValid_storesChallengeUnderHashedKeyWithTtl() {
        String state = issueState();

        String key = RedisAppSocialLoginStore.stateKey(state);
        assertThat(state).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(key).startsWith("auth:social-app:state:").doesNotContain(state);
        assertThat(redis.opsForValue().get(key)).isEqualTo(CHALLENGE);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
                .isBetween(Duration.ofMinutes(9).toMillis(), Duration.ofMinutes(10).toMillis());
    }

    @Test
    void issueState_whenCalledTwice_returnsDistinctStates() {
        assertThat(issueState()).isNotEqualTo(issueState());
    }

    @Test
    void consumeState_whenIssued_returnsChallengeOnlyOnce() {
        String state = issueState();

        assertThat(store.consumeState(state)).contains(CHALLENGE);
        assertThat(store.consumeState(state)).isEmpty();
        assertThat(redis.hasKey(RedisAppSocialLoginStore.stateKey(state))).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown-state"})
    void consumeState_whenBlankOrUnknown_returnsEmpty(String state) {
        assertThat(store.consumeState(state)).isEmpty();
    }

    @Test
    void consumeState_whenExpired_returnsEmpty() throws Exception {
        String state = store.issueState(CHALLENGE, Duration.ofMillis(100));
        usedKeys.add(RedisAppSocialLoginStore.stateKey(state));
        Thread.sleep(300);

        assertThat(store.consumeState(state)).isEmpty();
    }

    @Test
    void consumeState_whenConcurrent_onlyOneSucceeds() throws Exception {
        String state = issueState();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Optional<String>>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> store.consumeState(state));
            }
            long successes = 0;
            for (Future<Optional<String>> future : pool.invokeAll(calls)) {
                if (future.get().isPresent()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void issueState_whenInvalidArguments_throwsIllegalArgument() {
        assertThatThrownBy(() -> store.issueState(null, TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueState(" ", TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueState(CHALLENGE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueState(CHALLENGE, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- ticket ----------

    @Test
    void issueTicket_whenValid_roundTripsChallengeAndUserInfoOnce() {
        String raw = issueTicket(new AppLoginTicket(CHALLENGE, INFO));

        String key = RedisAppSocialLoginStore.ticketKey(raw);
        assertThat(key).startsWith("auth:social-app:ticket:").doesNotContain(raw);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
                .isBetween(TTL.minusSeconds(5).toMillis(), TTL.toMillis());

        assertThat(store.consumeTicket(raw)).contains(new AppLoginTicket(CHALLENGE, INFO));
        assertThat(store.consumeTicket(raw)).isEmpty();
    }

    @Test
    void issueTicket_whenEmailAndNameMissing_roundTripsNulls() {
        SocialUserInfo noEmail = new SocialUserInfo(SocialProvider.NAVER, "uid", null, false, null);
        String raw = issueTicket(new AppLoginTicket(CHALLENGE, noEmail));

        assertThat(store.consumeTicket(raw)).contains(new AppLoginTicket(CHALLENGE, noEmail));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown-ticket"})
    void consumeTicket_whenBlankOrUnknown_returnsEmpty(String ticket) {
        assertThat(store.consumeTicket(ticket)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{\"codeChallenge\":\"c\",\"provider\":\"UNKNOWN\",\"providerUserId\":\"u\"}",
            "{\"codeChallenge\":\"\",\"provider\":\"NAVER\",\"providerUserId\":\"u\"}"})
    void consumeTicket_whenStoredValueCorrupted_returnsEmptyAndDeletes(String value) {
        String raw = "corrupted-" + System.nanoTime();
        String key = RedisAppSocialLoginStore.ticketKey(raw);
        usedKeys.add(key);
        redis.opsForValue().set(key, value, TTL);

        assertThat(store.consumeTicket(raw)).isEmpty();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void issueTicket_whenInvalidArguments_throwsIllegalArgument() {
        assertThatThrownBy(() -> store.issueTicket(null, TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueTicket(new AppLoginTicket(" ", INFO), TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueTicket(new AppLoginTicket(CHALLENGE, null), TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueTicket(new AppLoginTicket(CHALLENGE,
                new SocialUserInfo(SocialProvider.NAVER, "", null, false, null)), TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.issueTicket(new AppLoginTicket(CHALLENGE, INFO), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 장애 ----------

    @Test
    void operations_whenRedisUnavailable_throwServiceUnavailable() {
        LettuceConnectionFactory down = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        down.afterPropertiesSet();
        down.start();
        try {
            RedisAppSocialLoginStore broken = new RedisAppSocialLoginStore(new StringRedisTemplate(down));

            assertServiceUnavailable(() -> broken.issueState(CHALLENGE, TTL));
            assertServiceUnavailable(() -> broken.consumeState("s"));
            assertServiceUnavailable(() -> broken.issueTicket(new AppLoginTicket(CHALLENGE, INFO), TTL));
            assertServiceUnavailable(() -> broken.consumeTicket("t"));
        } finally {
            down.destroy();
        }
    }

    private static void assertServiceUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(SocialAuthException.class)
                .extracting(e -> ((SocialAuthException) e).getReason())
                .isEqualTo(Reason.SERVICE_UNAVAILABLE);
    }
}
