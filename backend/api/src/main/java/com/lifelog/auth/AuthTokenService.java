package com.lifelog.auth;

import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.common.exception.ErrorCode;
import com.lifelog.domain.user.session.RefreshRotationResult;
import com.lifelog.domain.user.session.RefreshSession;
import com.lifelog.domain.user.session.RefreshSessionStore;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.RefreshTokenClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * access/refresh 토큰 발급·회전·폐기의 단일 진입점.
 * refresh 세션(sid)마다 현재 jti 1개만 저장소에 두고, 회전 시 CAS 로 교체한다.
 * 토큰 원문, jti, sid 는 로그에 남기지 않는다.
 * 저장소 장애({@code AuthSessionUnavailableException})는 그대로 전파해 503 으로 응답한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    /** 회전 직후 이전 토큰으로 온 동시 요청을 재사용이 아닌 경합(409)으로 판정하는 유예 시간 */
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    static final String INVALID_REFRESH_TOKEN_MESSAGE = "유효하지 않은 refresh 토큰입니다.";
    static final String SESSION_REVOKED_MESSAGE = "보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해 주세요.";
    static final String REFRESH_IN_PROGRESS_MESSAGE = "다른 요청에서 토큰을 갱신하는 중입니다. 잠시 후 다시 시도해 주세요.";

    private final JwtTokenProvider tokenProvider;
    private final RefreshSessionStore refreshSessionStore;
    private final Clock clock;

    /** 로그인 성공 시 새 세션을 만들고 토큰을 발급한다 */
    public TokenResponse issue(Long userId) {
        // JWT 시각은 초 단위라 저장소 TTL 과 어긋나지 않도록 초 단위로 맞춘다
        Instant now = now();
        String sessionId = newId();
        String tokenId = newId();
        Instant expiresAt = tokenProvider.refreshExpiresAt(now, now);

        refreshSessionStore.create(new RefreshSession(userId, sessionId, tokenId, now), Duration.between(now, expiresAt));
        return TokenResponse.of(
                tokenProvider.createAccessToken(userId),
                tokenProvider.createRefreshToken(userId, sessionId, tokenId, now, expiresAt));
    }

    /** refresh 토큰을 회전한다. 세션(sid)과 최초 인증 시각(auth_time)은 유지된다 */
    public TokenResponse rotate(String refreshToken) {
        RefreshTokenClaims claims = tokenProvider.parseRefreshToken(refreshToken)
                .orElseThrow(() -> {
                    log.warn("refresh 거부: 유효하지 않거나 만료/레거시 refresh 토큰");
                    return invalidRefreshToken();
                });
        Long userId = claims.userId();

        Instant now = now();
        Instant expiresAt = tokenProvider.refreshExpiresAt(claims.authTime(), now);
        Duration ttl = Duration.between(now, expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            // 절대 수명 도달 — 알림 없이 재로그인 유도
            log.warn("refresh 거부: 세션 절대 수명 만료, userId={}", userId);
            throw invalidRefreshToken();
        }

        String newTokenId = newId();
        RefreshRotationResult result = refreshSessionStore.rotate(
                userId, claims.sessionId(), claims.tokenId(), newTokenId, ttl, now, REUSE_GRACE);

        return switch (result) {
            case ROTATED -> {
                log.info("refresh 토큰 회전: userId={}", userId);
                yield TokenResponse.of(
                        tokenProvider.createAccessToken(userId),
                        tokenProvider.createRefreshToken(userId, claims.sessionId(), newTokenId, claims.authTime(), expiresAt));
            }
            case STALE_CONCURRENT -> {
                log.warn("refresh 경합: 직전에 회전된 토큰으로 요청, userId={}", userId);
                throw BusinessException.conflict(REFRESH_IN_PROGRESS_MESSAGE, ErrorCode.REFRESH_IN_PROGRESS);
            }
            case REUSE_DETECTED -> {
                log.warn("refresh 토큰 재사용 탐지: 사용자 전체 세션 폐기, userId={}", userId);
                throw BusinessException.unauthorized(SESSION_REVOKED_MESSAGE, ErrorCode.SESSION_REVOKED);
            }
            case REVOKED -> {
                log.warn("refresh 거부: 보안 폐기된 세션, userId={}", userId);
                throw BusinessException.unauthorized(SESSION_REVOKED_MESSAGE, ErrorCode.SESSION_REVOKED);
            }
            case NOT_FOUND -> {
                log.warn("refresh 거부: 세션 없음(로그아웃/만료), userId={}", userId);
                throw invalidRefreshToken();
            }
        };
    }

    /**
     * 로그아웃 — refresh 토큰의 세션을 폐기한다. 멱등:
     * 무효/만료/레거시 토큰이나 이미 폐기된 세션이어도 예외 없이 끝난다.
     */
    public void revoke(String refreshToken) {
        Optional<RefreshTokenClaims> claims = tokenProvider.parseRefreshToken(refreshToken);
        if (claims.isEmpty()) {
            log.warn("로그아웃: 유효하지 않은 refresh 토큰 — 폐기할 세션 없음");
            return;
        }
        Long userId = claims.get().userId();
        boolean revoked = refreshSessionStore.revoke(userId, claims.get().sessionId());
        log.info("로그아웃: userId={}, sessionRevoked={}", userId, revoked);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static BusinessException invalidRefreshToken() {
        return BusinessException.unauthorized(INVALID_REFRESH_TOKEN_MESSAGE, ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
