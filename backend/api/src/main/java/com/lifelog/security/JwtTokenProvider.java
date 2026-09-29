package com.lifelog.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey key;

    @PostConstruct
    private void init() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        return createToken(userId.toString(), ACCESS_TYPE, accessExpiration);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId.toString(), REFRESH_TYPE, refreshExpiration);
    }

    private String createToken(String subject, String type, long expiration) {
        return Jwts.builder()
                .subject(subject)
                .claim(TYPE_CLAIM, type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    /** 유효한 access 토큰이면 userId, 아니면(서명/만료/타입 불일치) empty */
    public Optional<Long> resolveAccessUserId(String token) {
        return resolveUserId(token, ACCESS_TYPE);
    }

    /** 유효한 refresh 토큰이면 userId, 아니면(서명/만료/타입 불일치) empty */
    public Optional<Long> resolveRefreshUserId(String token) {
        return resolveUserId(token, REFRESH_TYPE);
    }

    private Optional<Long> resolveUserId(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .require(TYPE_CLAIM, expectedType)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // 토큰·클레임 값이 메시지에 포함될 수 있어 예외 타입만 기록
            log.debug("JWT 거부: expectedType={}, reason={}", expectedType, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
