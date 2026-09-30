package com.lifelog.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "type";
    private static final String SESSION_ID_CLAIM = "sid";
    private static final String AUTH_TIME_CLAIM = "auth_time";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey key;
    private final Duration accessExpiration;
    private final Duration refreshIdleExpiration;
    private final Duration refreshAbsoluteExpiration;
    private final Clock clock;
    private final io.jsonwebtoken.Clock jwtClock;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-expiration}") long accessExpirationMillis,
                            @Value("${jwt.refresh-idle-expiration}") long refreshIdleExpirationMillis,
                            @Value("${jwt.refresh-absolute-expiration}") long refreshAbsoluteExpirationMillis,
                            Clock clock) {
        // 기동 시 설정 검증 — 잘못된 수명 설정으로 뜨지 않도록 한다
        if (refreshIdleExpirationMillis <= 0 || refreshIdleExpirationMillis > refreshAbsoluteExpirationMillis) {
            throw new IllegalStateException(
                    "jwt.refresh-idle-expiration 은 0보다 크고 jwt.refresh-absolute-expiration 이하여야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = Duration.ofMillis(accessExpirationMillis);
        this.refreshIdleExpiration = Duration.ofMillis(refreshIdleExpirationMillis);
        this.refreshAbsoluteExpiration = Duration.ofMillis(refreshAbsoluteExpirationMillis);
        this.clock = clock;
        this.jwtClock = () -> Date.from(clock.instant());
    }

    public String createAccessToken(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessExpiration)))
                .signWith(key)
                .compact();
    }

    /** 세션의 다음 refresh 만료 시각 = min(now + 비활성 수명, 최초 인증 시각 + 절대 수명) */
    public Instant refreshExpiresAt(Instant authTime, Instant now) {
        Instant idle = now.plus(refreshIdleExpiration);
        Instant absolute = authTime.plus(refreshAbsoluteExpiration);
        return idle.isBefore(absolute) ? idle : absolute;
    }

    public String createRefreshToken(Long userId, String sessionId, String tokenId, Instant authTime, Instant expiresAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId)
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(AUTH_TIME_CLAIM, authTime.getEpochSecond())
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /** 유효한 access 토큰이면 userId, 아니면(서명/만료/타입 불일치) empty */
    public Optional<Long> resolveAccessUserId(String token) {
        return parseClaims(token, ACCESS_TYPE).flatMap(claims -> parseUserId(claims.getSubject()));
    }

    /**
     * 유효한 refresh 토큰이면 클레임, 아니면 empty.
     * 서명/만료/타입 불일치뿐 아니라 jti·sid·auth_time 중 하나라도 없는 토큰(레거시)도 empty.
     */
    public Optional<RefreshTokenClaims> parseRefreshToken(String token) {
        return parseClaims(token, REFRESH_TYPE).flatMap(claims -> {
            Optional<Long> userId = parseUserId(claims.getSubject());
            String tokenId = claims.getId();
            String sessionId = claims.get(SESSION_ID_CLAIM) instanceof String s ? s : null;
            Instant authTime = claims.get(AUTH_TIME_CLAIM) instanceof Number n ? Instant.ofEpochSecond(n.longValue()) : null;
            if (userId.isEmpty() || isBlank(tokenId) || isBlank(sessionId) || authTime == null
                    || claims.getExpiration() == null) {
                log.debug("refresh JWT 거부: 필수 클레임 누락");
                return Optional.empty();
            }
            return Optional.of(new RefreshTokenClaims(
                    userId.get(), sessionId, tokenId, authTime, claims.getExpiration().toInstant()));
        });
    }

    private Optional<Claims> parseClaims(String token, String expectedType) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .clock(jwtClock)
                    .require(TYPE_CLAIM, expectedType)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            // 토큰·클레임 값이 메시지에 포함될 수 있어 예외 타입만 기록
            log.debug("JWT 거부: expectedType={}, reason={}", expectedType, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<Long> parseUserId(String subject) {
        try {
            return Optional.of(Long.parseLong(subject));
        } catch (NumberFormatException e) {
            log.debug("JWT 거부: subject 형식 오류");
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
