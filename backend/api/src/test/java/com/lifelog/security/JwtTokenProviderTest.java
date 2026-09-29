package com.lifelog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    static final String SECRET = "test-secret-key-for-jwt-token-provider-0123456789";
    private static final String OTHER_SECRET = "another-secret-key-for-jwt-signature-9876543210";
    private static final long ONE_HOUR = 3_600_000L;
    private static final Long USER_ID = 42L;

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = create(SECRET, ONE_HOUR, ONE_HOUR);
    }

    static JwtTokenProvider create(String secret, long accessExpiration, long refreshExpiration) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", secret);
        ReflectionTestUtils.setField(provider, "accessExpiration", accessExpiration);
        ReflectionTestUtils.setField(provider, "refreshExpiration", refreshExpiration);
        ReflectionTestUtils.invokeMethod(provider, "init");
        return provider;
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Claims parse(String token) {
        return Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void resolveAccessUserId_withAccessToken_returnsUserId() {
        String token = provider.createAccessToken(USER_ID);

        assertThat(provider.resolveAccessUserId(token)).contains(USER_ID);
    }

    @Test
    void resolveRefreshUserId_withAccessToken_returnsEmpty() {
        String token = provider.createAccessToken(USER_ID);

        assertThat(provider.resolveRefreshUserId(token)).isEmpty();
    }

    @Test
    void resolveRefreshUserId_withRefreshToken_returnsUserId() {
        String token = provider.createRefreshToken(USER_ID);

        assertThat(provider.resolveRefreshUserId(token)).contains(USER_ID);
    }

    @Test
    void resolveAccessUserId_withRefreshToken_returnsEmpty() {
        String token = provider.createRefreshToken(USER_ID);

        assertThat(provider.resolveAccessUserId(token)).isEmpty();
    }

    @Test
    void createAccessToken_whenIssued_hasAccessTypeClaim() {
        Claims claims = parse(provider.createAccessToken(USER_ID));

        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    void createRefreshToken_whenIssued_hasRefreshTypeClaim() {
        Claims claims = parse(provider.createRefreshToken(USER_ID));

        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    void resolveAccessUserId_whenTypeClaimMissing_returnsEmpty() {
        // type 클레임이 없는 기존(레거시) 토큰은 access/refresh 모두 거부
        String legacy = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ONE_HOUR))
                .signWith(key(SECRET))
                .compact();

        assertThat(provider.resolveAccessUserId(legacy)).isEmpty();
        assertThat(provider.resolveRefreshUserId(legacy)).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenTypeClaimUnknown_returnsEmpty() {
        String token = Jwts.builder()
                .subject("42")
                .claim("type", "admin")
                .expiration(new Date(System.currentTimeMillis() + ONE_HOUR))
                .signWith(key(SECRET))
                .compact();

        assertThat(provider.resolveAccessUserId(token)).isEmpty();
        assertThat(provider.resolveRefreshUserId(token)).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenSignedWithDifferentKey_returnsEmpty() {
        JwtTokenProvider otherProvider = create(OTHER_SECRET, ONE_HOUR, ONE_HOUR);

        assertThat(provider.resolveAccessUserId(otherProvider.createAccessToken(USER_ID))).isEmpty();
        assertThat(provider.resolveRefreshUserId(otherProvider.createRefreshToken(USER_ID))).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenTokenExpired_returnsEmpty() {
        JwtTokenProvider expiredProvider = create(SECRET, -60_000L, -60_000L);

        assertThat(provider.resolveAccessUserId(expiredProvider.createAccessToken(USER_ID))).isEmpty();
        assertThat(provider.resolveRefreshUserId(expiredProvider.createRefreshToken(USER_ID))).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenTokenMalformed_returnsEmpty() {
        assertThat(provider.resolveAccessUserId("not.a.jwt")).isEmpty();
        assertThat(provider.resolveRefreshUserId("garbage")).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenTokenEmptyOrNull_returnsEmpty() {
        assertThat(provider.resolveAccessUserId("")).isEmpty();
        assertThat(provider.resolveRefreshUserId("")).isEmpty();
        assertThat(provider.resolveAccessUserId(null)).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenSubjectNotNumeric_returnsEmpty() {
        String token = Jwts.builder()
                .subject("not-a-number")
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + ONE_HOUR))
                .signWith(key(SECRET))
                .compact();

        assertThat(provider.resolveAccessUserId(token)).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenSubjectMissing_returnsEmpty() {
        String token = Jwts.builder()
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + ONE_HOUR))
                .signWith(key(SECRET))
                .compact();

        assertThat(provider.resolveAccessUserId(token)).isEmpty();
    }
}
