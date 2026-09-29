package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.social.PendingSocialLink;
import com.lifelog.domain.user.social.PendingSocialLinkStore;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 계정 통합 대기 정보를 Redis Hash 로 저장한다. 키: {@code auth:social-link:{sha256hex(token)}}.
 * 원문 토큰은 저장하지 않는다. 캐시용 RedisTemplate/직렬화기와 분리하기 위해 StringRedisTemplate 을 사용한다.
 */
@Slf4j
@Component
public class RedisPendingSocialLinkStore implements PendingSocialLinkStore {

    static final String KEY_PREFIX = "auth:social-link:";
    static final String FIELD_USER_ID = "userId";
    static final String FIELD_PROVIDER = "provider";
    static final String FIELD_PROVIDER_USER_ID = "providerUserId";
    static final String FIELD_ATTEMPTS = "attempts";

    private static final int TOKEN_BYTES = 32;

    // 필드 저장과 TTL 설정을 원자적으로 (TTL 없는 키가 남지 않도록)
    private static final RedisScript<Long> ISSUE_SCRIPT = RedisScript.of("""
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5], ARGV[6], ARGV[7], '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[8])
            return 1
            """, Long.class);

    // HINCRBY 는 없는 키를 새로 만들므로, 키가 있을 때만 증가시킨다 (만료·삭제된 토큰이면 0)
    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisPendingSocialLinkStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String issue(PendingSocialLink link, Duration ttl) {
        if (link == null || link.userId() == null || link.provider() == null || link.providerUserId() == null) {
            throw new IllegalArgumentException("link fields are required");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String token = newToken();
        execute(() -> redis.execute(ISSUE_SCRIPT, List.of(key(token)),
                FIELD_USER_ID, String.valueOf(link.userId()),
                FIELD_PROVIDER, link.provider().name(),
                FIELD_PROVIDER_USER_ID, link.providerUserId(),
                FIELD_ATTEMPTS,
                String.valueOf(ttl.toMillis())));
        return token;
    }

    @Override
    public Optional<PendingSocialLink> find(String token) {
        if (isBlank(token)) {
            return Optional.empty();
        }
        Map<Object, Object> fields = execute(() -> redis.opsForHash().entries(key(token)));
        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PendingSocialLink(
                    Long.valueOf((String) fields.get(FIELD_USER_ID)),
                    SocialProvider.valueOf((String) fields.get(FIELD_PROVIDER)),
                    requireValue((String) fields.get(FIELD_PROVIDER_USER_ID))));
        } catch (RuntimeException e) {
            // 손상된 값은 없는 토큰으로 취급
            log.warn("소셜 연결 대기 정보 형식 오류: type={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public int incrementAttempts(String token) {
        if (isBlank(token)) {
            return 0;
        }
        Long attempts = execute(() -> redis.execute(INCREMENT_SCRIPT, List.of(key(token)), FIELD_ATTEMPTS));
        return attempts == null ? 0 : attempts.intValue();
    }

    @Override
    public boolean consume(String token) {
        if (isBlank(token)) {
            return false;
        }
        return Boolean.TRUE.equals(execute(() -> redis.delete(key(token))));
    }

    static String key(String token) {
        return KEY_PREFIX + sha256Hex(token);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            log.error("소셜 연결 대기 저장소 오류: type={}", e.getClass().getSimpleName());
            throw new SocialAuthException(Reason.SERVICE_UNAVAILABLE, "pending social link store unavailable", e);
        }
    }

    private static String requireValue(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("missing field");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
