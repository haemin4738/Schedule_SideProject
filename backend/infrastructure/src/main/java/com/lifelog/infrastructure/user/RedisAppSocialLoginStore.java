package com.lifelog.infrastructure.user;

import com.lifelog.domain.user.social.AppLoginTicket;
import com.lifelog.domain.user.social.AppSocialLoginStore;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 앱 소셜 로그인 state·ticket 을 Redis String 으로 저장한다. 소비는 GETDEL 로 원자적 1회.
 * 키: {@code auth:social-app:state:{sha256hex(state)}} → challenge,
 * {@code auth:social-app:ticket:{sha256hex(ticket)}} → JSON(challenge + 사용자 정보).
 * ticket 값에는 이메일·이름이 담기므로 TTL 을 짧게 둔다(호출자 결정). 원문 state·ticket 은 저장하지 않는다.
 */
@Slf4j
@Component
public class RedisAppSocialLoginStore implements AppSocialLoginStore {

    static final String STATE_KEY_PREFIX = "auth:social-app:state:";
    static final String TICKET_KEY_PREFIX = "auth:social-app:ticket:";

    private static final int TOKEN_BYTES = 32;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Redis 에 저장하는 ticket 값 형식 */
    record StoredTicket(String codeChallenge, String provider, String providerUserId,
                        String email, boolean emailVerified, String name) {}

    private final StringRedisTemplate redis;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisAppSocialLoginStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String issueState(String codeChallenge, Duration ttl) {
        requireText(codeChallenge, "codeChallenge");
        requirePositive(ttl);
        String state = newToken();
        execute(() -> redis.opsForValue().set(stateKey(state), codeChallenge, ttl));
        return state;
    }

    @Override
    public Optional<String> consumeState(String state) {
        if (isBlank(state)) {
            return Optional.empty();
        }
        return Optional.ofNullable(execute(() -> redis.opsForValue().getAndDelete(stateKey(state))));
    }

    @Override
    public String issueTicket(AppLoginTicket ticket, Duration ttl) {
        if (ticket == null || isBlank(ticket.codeChallenge()) || ticket.userInfo() == null
                || ticket.userInfo().provider() == null || isBlank(ticket.userInfo().providerUserId())) {
            throw new IllegalArgumentException("ticket fields are required");
        }
        requirePositive(ttl);
        SocialUserInfo info = ticket.userInfo();
        String value = JSON.writeValueAsString(new StoredTicket(ticket.codeChallenge(), info.provider().name(),
                info.providerUserId(), info.email(), info.emailVerified(), info.name()));
        String raw = newToken();
        execute(() -> redis.opsForValue().set(ticketKey(raw), value, ttl));
        return raw;
    }

    @Override
    public Optional<AppLoginTicket> consumeTicket(String ticket) {
        if (isBlank(ticket)) {
            return Optional.empty();
        }
        String value = execute(() -> redis.opsForValue().getAndDelete(ticketKey(ticket)));
        if (value == null) {
            return Optional.empty();
        }
        try {
            StoredTicket stored = JSON.readValue(value, StoredTicket.class);
            if (isBlank(stored.codeChallenge()) || isBlank(stored.providerUserId())) {
                throw new IllegalStateException("missing field");
            }
            return Optional.of(new AppLoginTicket(stored.codeChallenge(), new SocialUserInfo(
                    SocialProvider.valueOf(stored.provider()), stored.providerUserId(),
                    stored.email(), stored.emailVerified(), stored.name())));
        } catch (RuntimeException e) {
            // 손상된 값은 없는 ticket 으로 취급 (값에 개인정보가 있으므로 예외 타입만 기록)
            log.warn("앱 소셜 로그인 ticket 형식 오류: type={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static String stateKey(String state) {
        return STATE_KEY_PREFIX + sha256Hex(state);
    }

    static String ticketKey(String ticket) {
        return TICKET_KEY_PREFIX + sha256Hex(ticket);
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
            // 응답(503)과 로그는 호출 측(GlobalExceptionHandler·콜백)이 담당한다 — 여기서는 중복 ERROR 를 피해 WARN 만 남긴다
            log.warn("앱 소셜 로그인 저장소 오류: type={}", e.getClass().getSimpleName());
            throw new SocialAuthException(Reason.SERVICE_UNAVAILABLE, "app social login store unavailable", e);
        }
    }

    private static void execute(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    private static void requireText(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void requirePositive(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
