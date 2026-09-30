package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.session.AuthSessionUnavailableException;
import com.lifelog.domain.user.session.RefreshRotationResult;
import com.lifelog.domain.user.session.RefreshSession;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RedisRefreshSessionStore 통합 테스트 — 실제 Redis(REDIS_HOST/REDIS_PORT) 사용.
 * 테스트마다 고유 userId 를 쓰고, 사용한 userId 의 키를 각 테스트 후 삭제한다.
 */
class RedisRefreshSessionStoreTest {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration GRACE = Duration.ofSeconds(30);
    private static final Instant AUTH_TIME = Instant.parse("2026-09-30T00:00:00Z");

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private RedisRefreshSessionStore store;
    private final List<Long> usedUserIds = new ArrayList<>();

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
        store = new RedisRefreshSessionStore(redis);
    }

    @AfterEach
    void cleanUp() {
        for (Long userId : usedUserIds) {
            Set<String> keys = redis.keys(RedisRefreshSessionStore.userPrefix(userId) + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
    }

    private Long newUserId() {
        Long userId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        usedUserIds.add(userId);
        return userId;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private RefreshSession createSession(Long userId, String sessionId, String jti, Duration ttl) {
        RefreshSession session = new RefreshSession(userId, sessionId, jti, AUTH_TIME);
        store.create(session, ttl);
        return session;
    }

    private RefreshRotationResult rotate(Long userId, String sid, String presented, String newJti, Instant now) {
        return store.rotate(userId, sid, presented, newJti, TTL, now, GRACE);
    }

    private long pttl(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        return ttl == null ? -2 : ttl;
    }

    // ---------- create ----------

    @Test
    void create_whenValid_storesHashAndIndexWithTtl() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);

        String sessionKey = "auth:refresh:{u:" + userId + "}:session:" + sid;
        String indexKey = "auth:refresh:{u:" + userId + "}:sessions";
        assertThat(RedisRefreshSessionStore.sessionKey(userId, sid)).isEqualTo(sessionKey);
        assertThat(RedisRefreshSessionStore.indexKey(userId)).isEqualTo(indexKey);

        Map<Object, Object> fields = redis.opsForHash().entries(sessionKey);
        assertThat(fields).containsOnly(
                Map.entry("jti", "jti-1"),
                Map.entry("authTime", String.valueOf(AUTH_TIME.toEpochMilli())));
        assertThat(redis.opsForSet().members(indexKey)).containsExactly(sid);
        assertThat(pttl(sessionKey)).isBetween(TTL.minusSeconds(5).toMillis(), TTL.toMillis());
        assertThat(pttl(indexKey)).isBetween(TTL.minusSeconds(5).toMillis(), TTL.toMillis());
    }

    @Test
    void create_whenMultipleSessions_indexTtlIsMaxOfSessionTtls() {
        Long userId = newUserId();
        String longSid = newId();
        String shortSid = newId();
        createSession(userId, longSid, "jti-long", Duration.ofMinutes(30));
        createSession(userId, shortSid, "jti-short", Duration.ofMinutes(5));

        String indexKey = RedisRefreshSessionStore.indexKey(userId);
        assertThat(redis.opsForSet().members(indexKey)).containsExactlyInAnyOrder(longSid, shortSid);
        assertThat(pttl(indexKey)).isGreaterThan(Duration.ofMinutes(29).toMillis());
    }

    @Test
    void create_whenIndexHasExpiredMember_removesDeadMember() throws Exception {
        Long userId = newUserId();
        String expiringSid = newId();
        createSession(userId, expiringSid, "jti-old", Duration.ofMillis(150));
        Thread.sleep(400);

        String sid = newId();
        createSession(userId, sid, "jti-new", TTL);

        assertThat(redis.opsForSet().members(RedisRefreshSessionStore.indexKey(userId))).containsExactly(sid);
    }

    @Test
    void create_whenInvalidArguments_throwsIllegalArgument() {
        RefreshSession valid = new RefreshSession(1L, "sid", "jti", AUTH_TIME);

        assertThatThrownBy(() -> store.create(null, TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(new RefreshSession(null, "sid", "jti", AUTH_TIME), TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(new RefreshSession(1L, " ", "jti", AUTH_TIME), TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(new RefreshSession(1L, "sid", "", AUTH_TIME), TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(new RefreshSession(1L, "sid", "jti", null), TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(valid, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(valid, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.create(valid, Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(1L, "sid"))).isFalse();
    }

    // ---------- rotate ----------

    @Test
    void rotate_whenCurrentJti_rotatesAndResetsTtl() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", Duration.ofMinutes(1));
        Instant now = Instant.now();

        assertThat(store.rotate(userId, sid, "jti-1", "jti-2", TTL, now, GRACE))
                .isEqualTo(RefreshRotationResult.ROTATED);

        String sessionKey = RedisRefreshSessionStore.sessionKey(userId, sid);
        Map<Object, Object> fields = redis.opsForHash().entries(sessionKey);
        assertThat(fields).containsOnly(
                Map.entry("jti", "jti-2"),
                Map.entry("prevJti", "jti-1"),
                Map.entry("rotatedAt", String.valueOf(now.toEpochMilli())),
                Map.entry("authTime", String.valueOf(AUTH_TIME.toEpochMilli())));
        assertThat(pttl(sessionKey)).isGreaterThan(Duration.ofMinutes(9).toMillis());
        assertThat(pttl(RedisRefreshSessionStore.indexKey(userId))).isGreaterThan(Duration.ofMinutes(9).toMillis());
    }

    @Test
    void rotate_whenRotatedTwiceInChain_eachCurrentJtiWorks() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);
        Instant now = Instant.now();

        assertThat(rotate(userId, sid, "jti-1", "jti-2", now)).isEqualTo(RefreshRotationResult.ROTATED);
        assertThat(rotate(userId, sid, "jti-2", "jti-3", now.plusSeconds(1))).isEqualTo(RefreshRotationResult.ROTATED);
        assertThat(redis.opsForHash().get(RedisRefreshSessionStore.sessionKey(userId, sid), "jti")).isEqualTo("jti-3");
    }

    @Test
    void rotate_whenPreviousJtiWithinGrace_returnsStaleConcurrentWithoutChange() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);
        Instant now = Instant.now();
        rotate(userId, sid, "jti-1", "jti-2", now);

        assertThat(rotate(userId, sid, "jti-1", "jti-x", now.plus(GRACE)))
                .isEqualTo(RefreshRotationResult.STALE_CONCURRENT);

        Map<Object, Object> fields = redis.opsForHash().entries(RedisRefreshSessionStore.sessionKey(userId, sid));
        assertThat(fields).containsEntry("jti", "jti-2").containsEntry("prevJti", "jti-1");
        // 409 이후 새 토큰으로 정상 회전 가능
        assertThat(rotate(userId, sid, "jti-2", "jti-3", now.plusSeconds(1))).isEqualTo(RefreshRotationResult.ROTATED);
    }

    @Test
    void rotate_whenPreviousJtiAfterGrace_detectsReuseAndDeletesAllSessions() {
        Long userId = newUserId();
        String sid = newId();
        String otherSid = newId();
        createSession(userId, sid, "jti-1", TTL);
        createSession(userId, otherSid, "jti-other", Duration.ofMinutes(5));
        Instant now = Instant.now();
        rotate(userId, sid, "jti-1", "jti-2", now);

        assertThat(rotate(userId, sid, "jti-1", "jti-x", now.plus(GRACE).plusMillis(1)))
                .isEqualTo(RefreshRotationResult.REUSE_DETECTED);

        assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(userId, sid))).isFalse();
        assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(userId, otherSid))).isFalse();
        assertThat(redis.hasKey(RedisRefreshSessionStore.indexKey(userId))).isFalse();
    }

    @Test
    void rotate_whenUnknownJti_detectsReuseAndWritesTombstonesWithRemainingTtl() {
        Long userId = newUserId();
        String sid = newId();
        String otherSid = newId();
        createSession(userId, sid, "jti-1", TTL);
        createSession(userId, otherSid, "jti-other", Duration.ofMinutes(5));

        assertThat(rotate(userId, sid, "forged-jti", "jti-x", Instant.now()))
                .isEqualTo(RefreshRotationResult.REUSE_DETECTED);

        String tombstone = RedisRefreshSessionStore.tombstoneKey(userId, sid);
        String otherTombstone = RedisRefreshSessionStore.tombstoneKey(userId, otherSid);
        assertThat(tombstone).isEqualTo("auth:refresh:{u:" + userId + "}:revoked:" + sid);
        assertThat(redis.opsForValue().get(tombstone)).isEqualTo("REUSE");
        assertThat(redis.opsForValue().get(otherTombstone)).isEqualTo("REUSE");
        assertThat(pttl(tombstone)).isBetween(TTL.minusSeconds(5).toMillis(), TTL.toMillis());
        assertThat(pttl(otherTombstone)).isBetween(Duration.ofMinutes(4).toMillis(), Duration.ofMinutes(5).toMillis());
    }

    @Test
    void rotate_whenSessionRevokedByReuse_returnsRevokedForEverySessionAndKeepsTombstone() {
        Long userId = newUserId();
        String sid = newId();
        String otherSid = newId();
        createSession(userId, sid, "jti-1", TTL);
        createSession(userId, otherSid, "jti-other", TTL);
        rotate(userId, sid, "forged-jti", "jti-x", Instant.now());

        // 다른 기기가 자신의 정상 토큰으로 refresh → REVOKED (알림 대상)
        assertThat(rotate(userId, otherSid, "jti-other", "jti-y", Instant.now()))
                .isEqualTo(RefreshRotationResult.REVOKED);
        assertThat(rotate(userId, sid, "jti-1", "jti-z", Instant.now()))
                .isEqualTo(RefreshRotationResult.REVOKED);
        // tombstone 은 읽어도 삭제되지 않는다
        assertThat(rotate(userId, otherSid, "jti-other", "jti-y", Instant.now()))
                .isEqualTo(RefreshRotationResult.REVOKED);
        assertThat(redis.hasKey(RedisRefreshSessionStore.tombstoneKey(userId, otherSid))).isTrue();
        // 세션이 되살아나지 않는다
        assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(userId, otherSid))).isFalse();
    }

    @Test
    void rotate_whenReuseDetected_newLoginStillWorks() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);
        rotate(userId, sid, "forged-jti", "jti-x", Instant.now());

        String newSid = newId();
        createSession(userId, newSid, "jti-new", TTL);

        assertThat(rotate(userId, newSid, "jti-new", "jti-new-2", Instant.now()))
                .isEqualTo(RefreshRotationResult.ROTATED);
        assertThat(redis.opsForSet().members(RedisRefreshSessionStore.indexKey(userId))).containsExactly(newSid);
    }

    @Test
    void rotate_whenSessionMissing_returnsNotFoundAndCreatesNoKeys() {
        Long userId = newUserId();
        String sid = newId();

        assertThat(rotate(userId, sid, "jti-1", "jti-2", Instant.now())).isEqualTo(RefreshRotationResult.NOT_FOUND);

        assertThat(redis.keys(RedisRefreshSessionStore.userPrefix(userId) + "*")).isEmpty();
    }

    @Test
    void rotate_whenSessionExpired_returnsNotFound() throws Exception {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", Duration.ofMillis(150));
        Thread.sleep(400);

        assertThat(rotate(userId, sid, "jti-1", "jti-2", Instant.now())).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(userId, sid))).isFalse();
    }

    @Test
    void rotate_whenSessionLoggedOut_returnsNotFoundNotRevoked() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);
        store.revoke(userId, sid);

        assertThat(rotate(userId, sid, "jti-1", "jti-2", Instant.now())).isEqualTo(RefreshRotationResult.NOT_FOUND);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rotate_whenStringArgumentBlank_returnsNotFound(String blank) {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);
        Instant now = Instant.now();

        assertThat(store.rotate(userId, blank, "jti-1", "jti-2", TTL, now, GRACE)).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(store.rotate(userId, sid, blank, "jti-2", TTL, now, GRACE)).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(store.rotate(userId, sid, "jti-1", blank, TTL, now, GRACE)).isEqualTo(RefreshRotationResult.NOT_FOUND);
        // 세션은 변경되지 않는다 (재사용 탐지로 오판하지 않음)
        assertThat(redis.opsForHash().get(RedisRefreshSessionStore.sessionKey(userId, sid), "jti")).isEqualTo("jti-1");
    }

    @Test
    void rotate_whenUserIdNullOrTtlNotPositive_returnsNotFoundWithoutChange() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);
        Instant now = Instant.now();

        assertThat(store.rotate(null, sid, "jti-1", "jti-2", TTL, now, GRACE)).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(store.rotate(userId, sid, "jti-1", "jti-2", null, now, GRACE)).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(store.rotate(userId, sid, "jti-1", "jti-2", Duration.ZERO, now, GRACE)).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(store.rotate(userId, sid, "jti-1", "jti-2", Duration.ofSeconds(-1), now, GRACE)).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(redis.opsForHash().get(RedisRefreshSessionStore.sessionKey(userId, sid), "jti")).isEqualTo("jti-1");
    }

    @Test
    void rotate_whenNowOrGraceInvalid_throwsIllegalArgument() {
        assertThatThrownBy(() -> store.rotate(1L, "sid", "a", "b", TTL, null, GRACE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.rotate(1L, "sid", "a", "b", TTL, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.rotate(1L, "sid", "a", "b", TTL, Instant.now(), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotate_whenSameJtiConcurrently_exactlyOneRotated() throws Exception {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);
        int threads = 8;
        Instant now = Instant.now();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<RefreshRotationResult>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String newJti = "jti-2-" + i;
                calls.add(() -> {
                    start.await();
                    return rotate(userId, sid, "jti-1", newJti, now);
                });
            }
            List<Future<RefreshRotationResult>> futures = new ArrayList<>();
            for (Callable<RefreshRotationResult> call : calls) {
                futures.add(executor.submit(call));
            }
            start.countDown();
            List<RefreshRotationResult> results = new ArrayList<>();
            for (Future<RefreshRotationResult> f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(r -> r == RefreshRotationResult.ROTATED).hasSize(1);
            assertThat(results).filteredOn(r -> r != RefreshRotationResult.ROTATED)
                    .containsOnly(RefreshRotationResult.STALE_CONCURRENT);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rotate_whenReuseRacesWithRotation_noLiveSessionRemains() throws Exception {
        for (int round = 0; round < 20; round++) {
            Long userId = newUserId();
            String sid = newId();
            String otherSid = newId();
            createSession(userId, sid, "jti-1", TTL);
            createSession(userId, otherSid, "jti-other", TTL);
            Instant now = Instant.now();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(3);
            try {
                Future<RefreshRotationResult> legit = executor.submit(() -> {
                    start.await();
                    return rotate(userId, sid, "jti-1", "jti-2", now);
                });
                Future<RefreshRotationResult> otherLegit = executor.submit(() -> {
                    start.await();
                    return rotate(userId, otherSid, "jti-other", "jti-other-2", now);
                });
                Future<RefreshRotationResult> attacker = executor.submit(() -> {
                    start.await();
                    return rotate(userId, sid, "stolen-old-jti", "jti-evil", now);
                });
                start.countDown();
                legit.get(10, TimeUnit.SECONDS);
                otherLegit.get(10, TimeUnit.SECONDS);
                assertThat(attacker.get(10, TimeUnit.SECONDS)).isIn(
                        RefreshRotationResult.REUSE_DETECTED, RefreshRotationResult.REVOKED);
            } finally {
                executor.shutdownNow();
            }

            assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(userId, sid))).isFalse();
            assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(userId, otherSid))).isFalse();
            assertThat(redis.hasKey(RedisRefreshSessionStore.indexKey(userId))).isFalse();
            assertThat(rotate(userId, sid, "jti-2", "jti-3", now)).isEqualTo(RefreshRotationResult.REVOKED);
            assertThat(rotate(userId, otherSid, "jti-other-2", "jti-other-3", now)).isEqualTo(RefreshRotationResult.REVOKED);
        }
    }

    @Test
    void rotate_whenReuseDetectedForOneUser_otherUserUnaffected() {
        Long victim = newUserId();
        Long bystander = newUserId();
        String victimSid = newId();
        String bystanderSid = newId();
        createSession(victim, victimSid, "jti-v", TTL);
        createSession(bystander, bystanderSid, "jti-b", TTL);

        assertThat(rotate(victim, victimSid, "forged", "jti-x", Instant.now()))
                .isEqualTo(RefreshRotationResult.REUSE_DETECTED);

        assertThat(redis.hasKey(RedisRefreshSessionStore.tombstoneKey(bystander, bystanderSid))).isFalse();
        assertThat(redis.opsForSet().members(RedisRefreshSessionStore.indexKey(bystander))).containsExactly(bystanderSid);
        assertThat(rotate(bystander, bystanderSid, "jti-b", "jti-b2", Instant.now()))
                .isEqualTo(RefreshRotationResult.ROTATED);
    }

    @Test
    void rotate_whenSidBelongsToOtherUser_returnsNotFound() {
        Long owner = newUserId();
        Long other = newUserId();
        String sid = newId();
        createSession(owner, sid, "jti-1", TTL);

        assertThat(rotate(other, sid, "jti-1", "jti-2", Instant.now())).isEqualTo(RefreshRotationResult.NOT_FOUND);
        assertThat(redis.opsForHash().get(RedisRefreshSessionStore.sessionKey(owner, sid), "jti")).isEqualTo("jti-1");
    }

    // ---------- revoke ----------

    @Test
    void revoke_whenCalledTwice_returnsTrueOnlyOnceAndLeavesNoTombstone() {
        Long userId = newUserId();
        String sid = newId();
        String otherSid = newId();
        createSession(userId, sid, "jti-1", TTL);
        createSession(userId, otherSid, "jti-2", TTL);

        assertThat(store.revoke(userId, sid)).isTrue();
        assertThat(store.revoke(userId, sid)).isFalse();

        assertThat(redis.hasKey(RedisRefreshSessionStore.sessionKey(userId, sid))).isFalse();
        assertThat(redis.hasKey(RedisRefreshSessionStore.tombstoneKey(userId, sid))).isFalse();
        assertThat(redis.opsForSet().members(RedisRefreshSessionStore.indexKey(userId))).containsExactly(otherSid);
        // 다른 세션은 영향 없음
        assertThat(rotate(userId, otherSid, "jti-2", "jti-3", Instant.now())).isEqualTo(RefreshRotationResult.ROTATED);
    }

    @Test
    void revoke_whenLastSession_deletesEmptyIndex() {
        Long userId = newUserId();
        String sid = newId();
        createSession(userId, sid, "jti-1", TTL);

        assertThat(store.revoke(userId, sid)).isTrue();

        assertThat(redis.keys(RedisRefreshSessionStore.userPrefix(userId) + "*")).isEmpty();
    }

    @Test
    void revoke_whenSessionMissing_returnsFalseAndCreatesNoKeys() {
        Long userId = newUserId();

        assertThat(store.revoke(userId, newId())).isFalse();
        assertThat(redis.keys(RedisRefreshSessionStore.userPrefix(userId) + "*")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void revoke_whenSessionIdBlank_returnsFalse(String blank) {
        assertThat(store.revoke(1L, blank)).isFalse();
        assertThat(store.revoke(null, "sid")).isFalse();
    }

    // ---------- 장애 ----------

    @Test
    void operations_whenRedisUnavailable_throwAuthSessionUnavailable() {
        LettuceConnectionFactory down = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        down.afterPropertiesSet();
        down.start();
        try {
            RedisRefreshSessionStore brokenStore = new RedisRefreshSessionStore(new StringRedisTemplate(down));

            assertThatThrownBy(() -> brokenStore.create(new RefreshSession(1L, "sid", "jti", AUTH_TIME), TTL))
                    .isInstanceOf(AuthSessionUnavailableException.class)
                    .hasCauseInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> brokenStore.rotate(1L, "sid", "a", "b", TTL, Instant.now(), GRACE))
                    .isInstanceOf(AuthSessionUnavailableException.class);
            assertThatThrownBy(() -> brokenStore.revoke(1L, "sid"))
                    .isInstanceOf(AuthSessionUnavailableException.class);
        } finally {
            down.destroy();
        }
    }
}
