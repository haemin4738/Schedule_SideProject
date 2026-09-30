package com.lifelog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        return create(secret, accessExpiration, refreshExpiration, Clock.systemUTC());
    }

    static JwtTokenProvider create(String secret, long accessExpiration, long refreshExpiration, Clock clock) {
        return new JwtTokenProvider(secret, accessExpiration, refreshExpiration, refreshExpiration, clock);
    }

    /** 테스트용 refresh 토큰 — 현재 시각 기준 수명 내에서 발급 */
    static String refreshToken(JwtTokenProvider provider, Long userId) {
        Instant now = Instant.now();
        return provider.createRefreshToken(userId, "sid-1", "jti-1", now, provider.refreshExpiresAt(now, now));
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
    void parseRefreshToken_withAccessToken_returnsEmpty() {
        String token = provider.createAccessToken(USER_ID);

        assertThat(provider.parseRefreshToken(token)).isEmpty();
    }

    @Test
    void parseRefreshToken_withRefreshToken_returnsClaims() {
        Instant authTime = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String token = provider.createRefreshToken(USER_ID, "sid-1", "jti-1", authTime, expiresAt);

        assertThat(provider.parseRefreshToken(token)).contains(
                new RefreshTokenClaims(USER_ID, "sid-1", "jti-1", authTime, expiresAt));
    }

    @Test
    void resolveAccessUserId_withRefreshToken_returnsEmpty() {
        String token = refreshToken(provider, USER_ID);

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
        Claims claims = parse(refreshToken(provider, USER_ID));

        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getId()).isEqualTo("jti-1");
        assertThat(claims.get("sid", String.class)).isEqualTo("sid-1");
        assertThat(claims.get("auth_time")).isInstanceOf(Number.class);
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
        assertThat(provider.parseRefreshToken(legacy)).isEmpty();
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
        assertThat(provider.parseRefreshToken(token)).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenSignedWithDifferentKey_returnsEmpty() {
        JwtTokenProvider otherProvider = create(OTHER_SECRET, ONE_HOUR, ONE_HOUR);

        assertThat(provider.resolveAccessUserId(otherProvider.createAccessToken(USER_ID))).isEmpty();
        assertThat(provider.parseRefreshToken(refreshToken(otherProvider, USER_ID))).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenTokenExpired_returnsEmpty() {
        // 2시간 전 시각으로 발급한 1시간짜리 토큰은 현재 시각 기준 만료
        Instant past = Instant.now().minusSeconds(7_200);
        JwtTokenProvider pastProvider = create(SECRET, ONE_HOUR, ONE_HOUR, Clock.fixed(past, ZoneOffset.UTC));
        String expiredRefresh = pastProvider.createRefreshToken(
                USER_ID, "sid-1", "jti-1", past, pastProvider.refreshExpiresAt(past, past));

        assertThat(provider.resolveAccessUserId(pastProvider.createAccessToken(USER_ID))).isEmpty();
        assertThat(provider.parseRefreshToken(expiredRefresh)).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenTokenMalformed_returnsEmpty() {
        assertThat(provider.resolveAccessUserId("not.a.jwt")).isEmpty();
        assertThat(provider.parseRefreshToken("garbage")).isEmpty();
    }

    @Test
    void resolveAccessUserId_whenTokenEmptyOrNull_returnsEmpty() {
        assertThat(provider.resolveAccessUserId("")).isEmpty();
        assertThat(provider.parseRefreshToken("")).isEmpty();
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

    @Test
    void parseRefreshToken_whenLegacyRefreshWithoutSessionClaims_returnsEmpty() {
        // jti/sid/auth_time 이 없는 기존 refresh 토큰은 거부
        String legacy = Jwts.builder()
                .subject("42")
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ONE_HOUR))
                .signWith(key(SECRET))
                .compact();

        assertThat(provider.parseRefreshToken(legacy)).isEmpty();
    }

    @Test
    void refreshExpiresAt_whenAbsoluteLimitEarlier_returnsAbsoluteLimit() {
        JwtTokenProvider p = new JwtTokenProvider(SECRET, ONE_HOUR, 10 * ONE_HOUR, 30 * ONE_HOUR, Clock.systemUTC());
        Instant authTime = Instant.parse("2026-09-01T00:00:00Z");

        assertThat(p.refreshExpiresAt(authTime, authTime)).isEqualTo(authTime.plusMillis(10 * ONE_HOUR));
        Instant late = authTime.plusMillis(25 * ONE_HOUR);
        assertThat(p.refreshExpiresAt(authTime, late)).isEqualTo(authTime.plusMillis(30 * ONE_HOUR));
    }

    @Test
    void constructor_whenIdleExceedsAbsolute_throwsException() {
        assertThatThrownBy(() -> new JwtTokenProvider(SECRET, ONE_HOUR, 2 * ONE_HOUR, ONE_HOUR, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenProvider(SECRET, ONE_HOUR, 0, ONE_HOUR, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 필수 클레임을 하나씩 바꿔 끼울 수 있는 refresh 토큰 빌더 (null 이면 해당 클레임 생략) */
    private static String customRefresh(String subject, String jti, Object sid, Object authTime, Date expiration) {
        var builder = Jwts.builder().claim("type", "refresh").issuedAt(new Date());
        if (subject != null) builder.subject(subject);
        if (jti != null) builder.id(jti);
        if (sid != null) builder.claim("sid", sid);
        if (authTime != null) builder.claim("auth_time", authTime);
        if (expiration != null) builder.expiration(expiration);
        return builder.signWith(key(SECRET)).compact();
    }

    private static Date inOneHour() {
        return new Date(System.currentTimeMillis() + ONE_HOUR);
    }

    @Test
    void parseRefreshToken_whenAllCustomClaimsPresent_returnsClaims() {
        // customRefresh 빌더 자체가 유효한 토큰을 만든다는 기준선
        long authTime = Instant.now().getEpochSecond();
        String token = customRefresh("42", "jti-1", "sid-1", authTime, inOneHour());

        assertThat(provider.parseRefreshToken(token)).hasValueSatisfying(c -> {
            assertThat(c.userId()).isEqualTo(42L);
            assertThat(c.sessionId()).isEqualTo("sid-1");
            assertThat(c.tokenId()).isEqualTo("jti-1");
            assertThat(c.authTime()).isEqualTo(Instant.ofEpochSecond(authTime));
        });
    }

    @Test
    void parseRefreshToken_whenSubjectNotNumeric_returnsEmpty() {
        String token = customRefresh("abc", "jti-1", "sid-1", Instant.now().getEpochSecond(), inOneHour());

        assertThat(provider.parseRefreshToken(token)).isEmpty();
    }

    @Test
    void parseRefreshToken_whenOnlyJtiMissingOrBlank_returnsEmpty() {
        long authTime = Instant.now().getEpochSecond();

        assertThat(provider.parseRefreshToken(customRefresh("42", null, "sid-1", authTime, inOneHour()))).isEmpty();
        assertThat(provider.parseRefreshToken(customRefresh("42", "  ", "sid-1", authTime, inOneHour()))).isEmpty();
    }

    @Test
    void parseRefreshToken_whenOnlySidMissingBlankOrNotString_returnsEmpty() {
        long authTime = Instant.now().getEpochSecond();

        assertThat(provider.parseRefreshToken(customRefresh("42", "jti-1", null, authTime, inOneHour()))).isEmpty();
        assertThat(provider.parseRefreshToken(customRefresh("42", "jti-1", " ", authTime, inOneHour()))).isEmpty();
        assertThat(provider.parseRefreshToken(customRefresh("42", "jti-1", 123, authTime, inOneHour()))).isEmpty();
    }

    @Test
    void parseRefreshToken_whenOnlyAuthTimeMissingOrNotNumber_returnsEmpty() {
        assertThat(provider.parseRefreshToken(customRefresh("42", "jti-1", "sid-1", null, inOneHour()))).isEmpty();
        assertThat(provider.parseRefreshToken(customRefresh("42", "jti-1", "sid-1", "yesterday", inOneHour()))).isEmpty();
    }

    @Test
    void parseRefreshToken_whenExpirationMissing_returnsEmpty() {
        String token = customRefresh("42", "jti-1", "sid-1", Instant.now().getEpochSecond(), null);

        assertThat(provider.parseRefreshToken(token)).isEmpty();
    }

    @Test
    void parseRefreshToken_whenInjectedClockPastExpiration_returnsEmpty() {
        // 만료 판정이 시스템 시계가 아니라 주입된 Clock 을 따른다
        Instant issuedAt = Instant.parse("2026-09-01T00:00:00Z");
        JwtTokenProvider issuer = create(SECRET, ONE_HOUR, ONE_HOUR, Clock.fixed(issuedAt, ZoneOffset.UTC));
        String token = issuer.createRefreshToken(USER_ID, "sid-1", "jti-1", issuedAt, issuedAt.plusMillis(ONE_HOUR));

        assertThat(issuer.parseRefreshToken(token)).isPresent();
        JwtTokenProvider later = create(SECRET, ONE_HOUR, ONE_HOUR,
                Clock.fixed(issuedAt.plusMillis(ONE_HOUR).plusSeconds(1), ZoneOffset.UTC));
        assertThat(later.parseRefreshToken(token)).isEmpty();
    }
}
