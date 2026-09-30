package com.lifelog.auth;

import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.common.exception.ErrorCode;
import com.lifelog.domain.user.session.AuthSessionUnavailableException;
import com.lifelog.domain.user.session.RefreshRotationOutcome;
import com.lifelog.domain.user.session.RefreshRotationResult;
import com.lifelog.domain.user.session.RefreshSession;
import com.lifelog.domain.user.session.RefreshSessionStore;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.RefreshTokenClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AuthTokenService 단위 테스트 — 저장소는 Mock, JwtTokenProvider 는 고정 Clock 을 쓰는 실제 구현.
 * 발급된 토큰의 클레임과 저장소에 전달된 세션/TTL 이 서로 일치하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final String SECRET = "test-secret-key-for-auth-token-service-0123456789";
    private static final Duration ACCESS = Duration.ofMinutes(15);
    private static final Duration IDLE = Duration.ofDays(15);
    private static final Duration ABSOLUTE = Duration.ofDays(30);
    // 밀리초가 섞인 시각 — 서비스가 초 단위로 절삭하는지 확인
    private static final Instant NOW_WITH_MILLIS = Instant.parse("2026-09-30T03:00:00.789Z");
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final Long USER_ID = 42L;

    @Mock
    private RefreshSessionStore store;

    private JwtTokenProvider tokenProvider;
    private AuthTokenService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW_WITH_MILLIS, ZoneOffset.UTC);
        tokenProvider = new JwtTokenProvider(SECRET, ACCESS.toMillis(), IDLE.toMillis(), ABSOLUTE.toMillis(), clock);
        service = new AuthTokenService(tokenProvider, store, clock);
    }

    private RefreshTokenClaims parse(String refreshToken) {
        return tokenProvider.parseRefreshToken(refreshToken).orElseThrow();
    }

    /** authTime 기준, 지정 만료 시각의 refresh 토큰 */
    private String refreshToken(String sid, String jti, Instant authTime, Instant expiresAt) {
        return tokenProvider.createRefreshToken(USER_ID, sid, jti, authTime, expiresAt);
    }

    /** 저장소가 서비스가 넘긴 새 jti 로 회전했다고 응답 */
    private static Answer<RefreshRotationOutcome> rotated() {
        return inv -> new RefreshRotationOutcome(RefreshRotationResult.ROTATED, inv.getArgument(3));
    }

    private static void assertBusiness(Throwable thrown, HttpStatus status, ErrorCode code, String message) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException e = (BusinessException) thrown;
        assertThat(e.getStatus()).isEqualTo(status);
        assertThat(e.getCode()).isEqualTo(code);
        assertThat(e.getMessage()).isEqualTo(message);
    }

    // ---------- issue ----------

    @Test
    void issue_whenCalled_storesSessionMatchingRefreshTokenClaims() {
        TokenResponse tokens = service.issue(USER_ID);

        ArgumentCaptor<RefreshSession> session = ArgumentCaptor.forClass(RefreshSession.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(store).create(session.capture(), ttl.capture());

        RefreshTokenClaims claims = parse(tokens.refreshToken());
        assertThat(session.getValue().userId()).isEqualTo(USER_ID);
        assertThat(session.getValue().sessionId()).isEqualTo(claims.sessionId()).isNotBlank();
        assertThat(session.getValue().tokenId()).isEqualTo(claims.tokenId()).isNotBlank();
        assertThat(session.getValue().sessionId()).isNotEqualTo(session.getValue().tokenId());
        assertThat(session.getValue().authTime()).isEqualTo(claims.authTime()).isEqualTo(NOW);
    }

    @Test
    void issue_whenCalled_ttlEqualsExpMinusNowTruncatedToSeconds() {
        TokenResponse tokens = service.issue(USER_ID);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(store).create(any(), ttl.capture());

        RefreshTokenClaims claims = parse(tokens.refreshToken());
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(IDLE));
        assertThat(ttl.getValue()).isEqualTo(Duration.between(NOW, claims.expiresAt())).isEqualTo(IDLE);
    }

    @Test
    void issue_whenCalled_returnsBearerAccessTokenForUser() {
        TokenResponse tokens = service.issue(USER_ID);

        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokenProvider.resolveAccessUserId(tokens.accessToken())).contains(USER_ID);
    }

    @Test
    void issue_whenCalledTwice_createsDistinctSessions() {
        RefreshTokenClaims first = parse(service.issue(USER_ID).refreshToken());
        RefreshTokenClaims second = parse(service.issue(USER_ID).refreshToken());

        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(first.tokenId()).isNotEqualTo(second.tokenId());
    }

    @Test
    void issue_whenStoreUnavailable_propagatesException() {
        AuthSessionUnavailableException failure =
                new AuthSessionUnavailableException("down", new QueryTimeoutException("timeout"));
        doThrow(failure).when(store).create(any(), any());

        assertThatThrownBy(() -> service.issue(USER_ID)).isSameAs(failure);
    }

    // ---------- rotate ----------

    @Test
    void rotate_whenRotated_keepsSidAndAuthTimeWithNewJti() {
        Instant authTime = NOW.minus(Duration.ofDays(3));
        String presented = refreshToken("sid-1", "jti-old", authTime, NOW.plus(Duration.ofDays(1)));
        when(store.rotate(eq(USER_ID), eq("sid-1"), eq("jti-old"), anyString(), any(), eq(NOW), eq(AuthTokenService.ROTATION_OVERLAP)))
                .thenAnswer(rotated());

        TokenResponse tokens = service.rotate(presented);

        ArgumentCaptor<String> newJti = ArgumentCaptor.forClass(String.class);
        verify(store).rotate(eq(USER_ID), eq("sid-1"), eq("jti-old"), newJti.capture(), any(), eq(NOW),
                eq(Duration.ofSeconds(30)));
        RefreshTokenClaims claims = parse(tokens.refreshToken());
        assertThat(claims.sessionId()).isEqualTo("sid-1");
        assertThat(claims.authTime()).isEqualTo(authTime);
        assertThat(claims.tokenId()).isEqualTo(newJti.getValue()).isNotEqualTo("jti-old").isNotBlank();
        assertThat(tokenProvider.resolveAccessUserId(tokens.accessToken())).contains(USER_ID);
    }

    @Test
    void rotate_whenRotatedWithinIdleWindow_ttlIsIdleAndMatchesNewExp() {
        Instant authTime = NOW.minus(Duration.ofDays(3));
        String presented = refreshToken("sid-1", "jti-old", authTime, NOW.plus(Duration.ofHours(1)));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(rotated());

        TokenResponse tokens = service.rotate(presented);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(store).rotate(anyLong(), anyString(), anyString(), anyString(), ttl.capture(), any(), any());
        RefreshTokenClaims claims = parse(tokens.refreshToken());
        // 회전 시 idle 수명만큼 연장된다(절대 기한 이전이므로)
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(IDLE));
        assertThat(ttl.getValue()).isEqualTo(Duration.between(NOW, claims.expiresAt())).isEqualTo(IDLE);
    }

    @Test
    void rotate_whenNearAbsoluteDeadline_ttlCappedToAbsoluteExpiry() {
        // 최초 로그인 29일 전 → 남은 절대 수명 1일(< idle 15일)
        Instant authTime = NOW.minus(Duration.ofDays(29));
        String presented = refreshToken("sid-1", "jti-old", authTime, NOW.plus(Duration.ofHours(1)));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(rotated());

        TokenResponse tokens = service.rotate(presented);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(store).rotate(anyLong(), anyString(), anyString(), anyString(), ttl.capture(), any(), any());
        RefreshTokenClaims claims = parse(tokens.refreshToken());
        assertThat(claims.expiresAt()).isEqualTo(authTime.plus(ABSOLUTE));
        assertThat(ttl.getValue()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void rotate_whenOneSecondBeforeAbsoluteDeadline_ttlIsOneSecond() {
        Instant authTime = NOW.minus(ABSOLUTE).plusSeconds(1);
        String presented = refreshToken("sid-1", "jti-old", authTime, NOW.plusSeconds(1));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(rotated());

        service.rotate(presented);

        verify(store).rotate(anyLong(), anyString(), anyString(), anyString(), eq(Duration.ofSeconds(1)), any(), any());
    }

    @Test
    void rotate_whenAbsoluteDeadlineReachedButTokenNotExpired_throwsInvalidWithoutStoreCall() {
        // exp 는 남았지만 authTime+절대수명 == now → ttl 0 방어
        Instant authTime = NOW.minus(ABSOLUTE);
        String presented = refreshToken("sid-1", "jti-old", authTime, NOW.plus(Duration.ofHours(1)));

        Throwable thrown = catchThrowable(() -> service.rotate(presented));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
        verifyNoInteractions(store);
    }

    @Test
    void rotate_whenAbsoluteDeadlinePassed_throwsInvalidWithoutStoreCall() {
        Instant authTime = NOW.minus(ABSOLUTE).minusSeconds(1);
        String presented = refreshToken("sid-1", "jti-old", authTime, NOW.plus(Duration.ofHours(1)));

        Throwable thrown = catchThrowable(() -> service.rotate(presented));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
        verifyNoInteractions(store);
    }

    @Test
    void rotate_whenReissued_issuesTokenWithStoreCurrentJtiAndSameSidAuthTime() {
        Instant authTime = NOW.minus(Duration.ofDays(3));
        String presented = refreshToken("sid-1", "jti-prev", authTime, NOW.plus(Duration.ofDays(1)));
        when(store.rotate(eq(USER_ID), eq("sid-1"), eq("jti-prev"), anyString(), any(), eq(NOW),
                eq(AuthTokenService.ROTATION_OVERLAP)))
                .thenReturn(new RefreshRotationOutcome(RefreshRotationResult.REISSUED, "jti-current"));

        TokenResponse tokens = service.rotate(presented);

        RefreshTokenClaims claims = parse(tokens.refreshToken());
        assertThat(claims.tokenId()).isEqualTo("jti-current");
        assertThat(claims.sessionId()).isEqualTo("sid-1");
        assertThat(claims.authTime()).isEqualTo(authTime);
        assertThat(tokenProvider.resolveAccessUserId(tokens.accessToken())).contains(USER_ID);
    }

    @Test
    void rotate_whenReissued_expRecalculatedFromNow() {
        Instant authTime = NOW.minus(Duration.ofDays(3));
        String presented = refreshToken("sid-1", "jti-prev", authTime, NOW.plus(Duration.ofHours(1)));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new RefreshRotationOutcome(RefreshRotationResult.REISSUED, "jti-current"));

        TokenResponse tokens = service.rotate(presented);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(store).rotate(anyLong(), anyString(), anyString(), anyString(), ttl.capture(), any(), any());
        RefreshTokenClaims claims = parse(tokens.refreshToken());
        // 제시한 토큰의 exp(1시간 뒤)가 아니라 이번 요청 시각 기준 idle 수명
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(IDLE));
        assertThat(ttl.getValue()).isEqualTo(IDLE);
    }

    @Test
    void rotate_whenPreviousAfterGrace_throwsInvalidRefreshTokenWithoutSessionRevokedCode() {
        String presented = refreshToken("sid-1", "jti-prev", NOW, NOW.plus(IDLE));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(RefreshRotationOutcome.of(RefreshRotationResult.PREVIOUS_AFTER_GRACE));

        Throwable thrown = catchThrowable(() -> service.rotate(presented));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
        assertThat(((BusinessException) thrown).getCode()).isNotEqualTo(ErrorCode.SESSION_REVOKED);
    }

    @ParameterizedTest
    @EnumSource(value = RefreshRotationResult.class, names = {"ROTATED", "REISSUED"})
    void rotate_whenRotatedOrReissuedWithNullTokenId_throwsIllegalState(RefreshRotationResult result) {
        String presented = refreshToken("sid-1", "jti-1", NOW, NOW.plus(IDLE));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(RefreshRotationOutcome.of(result));

        assertThatThrownBy(() -> service.rotate(presented)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RefreshRotationResult.class, names = {"REUSE_DETECTED", "REVOKED"})
    void rotate_whenReuseDetectedOrRevoked_throwsUnauthorizedSessionRevoked(RefreshRotationResult result) {
        String presented = refreshToken("sid-1", "jti-x", NOW, NOW.plus(IDLE));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(RefreshRotationOutcome.of(result));

        Throwable thrown = catchThrowable(() -> service.rotate(presented));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.SESSION_REVOKED,
                "보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해 주세요.");
    }

    @Test
    void rotate_whenSessionNotFound_throwsUnauthorizedInvalidRefreshToken() {
        String presented = refreshToken("sid-1", "jti-x", NOW, NOW.plus(IDLE));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(RefreshRotationOutcome.of(RefreshRotationResult.NOT_FOUND));

        Throwable thrown = catchThrowable(() -> service.rotate(presented));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-jwt", "a.b.c"})
    void rotate_whenTokenUnparseable_throwsInvalidWithoutStoreCall(String token) {
        Throwable thrown = catchThrowable(() -> service.rotate(token));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
        verifyNoInteractions(store);
    }

    @Test
    void rotate_whenAccessTokenPresented_throwsInvalidWithoutStoreCall() {
        String access = tokenProvider.createAccessToken(USER_ID);

        Throwable thrown = catchThrowable(() -> service.rotate(access));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
        verifyNoInteractions(store);
    }

    @Test
    void rotate_whenTokenExpired_throwsInvalidWithoutStoreCall() {
        String expired = refreshToken("sid-1", "jti-1", NOW.minus(Duration.ofDays(16)), NOW.minusSeconds(1));

        Throwable thrown = catchThrowable(() -> service.rotate(expired));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
        verifyNoInteractions(store);
    }

    @Test
    void rotate_whenLegacyTokenWithoutJtiAndSid_throwsInvalidWithoutStoreCall() {
        String legacy = legacyRefreshToken();

        Throwable thrown = catchThrowable(() -> service.rotate(legacy));

        assertBusiness(thrown, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE);
        verifyNoInteractions(store);
    }

    @Test
    void rotate_whenStoreUnavailable_propagatesException() {
        String presented = refreshToken("sid-1", "jti-1", NOW, NOW.plus(IDLE));
        AuthSessionUnavailableException failure =
                new AuthSessionUnavailableException("down", new QueryTimeoutException("timeout"));
        when(store.rotate(anyLong(), anyString(), anyString(), anyString(), any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.rotate(presented)).isSameAs(failure);
    }

    // ---------- revoke ----------

    @Test
    void revoke_whenValidToken_passesUserIdAndSid() {
        String token = refreshToken("sid-9", "jti-9", NOW, NOW.plus(IDLE));
        when(store.revoke(USER_ID, "sid-9")).thenReturn(true);

        service.revoke(token);

        verify(store).revoke(USER_ID, "sid-9");
    }

    @Test
    void revoke_whenSessionAlreadyGone_completesWithoutException() {
        String token = refreshToken("sid-9", "jti-9", NOW, NOW.plus(IDLE));
        when(store.revoke(USER_ID, "sid-9")).thenReturn(false);

        assertThatCode(() -> service.revoke(token)).doesNotThrowAnyException();
        verify(store).revoke(USER_ID, "sid-9");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-jwt"})
    void revoke_whenTokenInvalid_isNoOp(String token) {
        assertThatCode(() -> service.revoke(token)).doesNotThrowAnyException();
        verifyNoInteractions(store);
    }

    @Test
    void revoke_whenLegacyOrExpiredToken_isNoOp() {
        String expired = refreshToken("sid-1", "jti-1", NOW.minus(Duration.ofDays(16)), NOW.minusSeconds(1));

        assertThatCode(() -> service.revoke(legacyRefreshToken())).doesNotThrowAnyException();
        assertThatCode(() -> service.revoke(expired)).doesNotThrowAnyException();
        verifyNoInteractions(store);
    }

    @Test
    void revoke_whenStoreUnavailable_propagatesException() {
        String token = refreshToken("sid-9", "jti-9", NOW, NOW.plus(IDLE));
        AuthSessionUnavailableException failure =
                new AuthSessionUnavailableException("down", new QueryTimeoutException("timeout"));
        when(store.revoke(USER_ID, "sid-9")).thenThrow(failure);

        assertThatThrownBy(() -> service.revoke(token)).isSameAs(failure);
    }

    /** 이전 형식(jti/sid/auth_time 없음)의 refresh 토큰 — 서명·만료는 유효 */
    private static String legacyRefreshToken() {
        return Jwts.builder()
                .subject(USER_ID.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(Duration.ofDays(7))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
