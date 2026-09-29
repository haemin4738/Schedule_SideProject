package com.lifelog.auth;

import com.lifelog.auth.dto.SocialGrantType;
import com.lifelog.auth.dto.SocialLinkRequest;
import com.lifelog.auth.dto.SocialLoginRequest;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.domain.user.social.PendingSocialLink;
import com.lifelog.domain.user.social.PendingSocialLinkStore;
import com.lifelog.domain.user.social.SocialAccount;
import com.lifelog.domain.user.social.SocialAccountRepository;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialIdentityVerifier;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import com.lifelog.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTest {

    private static final String LINK_TOKEN = "opaque-link-token";

    @Mock private SocialIdentityVerifier verifier;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private PendingSocialLinkStore pendingSocialLinkStore;
    @Mock private SocialAccountRegistrar registrar;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;

    private SocialAuthService service;

    @BeforeEach
    void setUp() {
        service = new SocialAuthService(verifier, socialAccountRepository, userRepository,
                pendingSocialLinkStore, registrar, passwordEncoder, tokenProvider);
    }

    // ---------- fixtures ----------

    private static SocialLoginRequest codeRequest() {
        return new SocialLoginRequest(SocialGrantType.AUTHORIZATION_CODE, "auth-code",
                "http://localhost:5173/oauth/callback/kakao", null, null, null, null, null);
    }

    private static SocialUserInfo info(String email, boolean verified, String name) {
        return new SocialUserInfo(SocialProvider.KAKAO, "kakao-1", email, verified, name);
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static User localUser(long id) {
        return withId(User.create("leeheamin@gmail.com", "encoded-pw", "로컬"), id);
    }

    private void givenTokens(long userId) {
        when(tokenProvider.createAccessToken(userId)).thenReturn("access-" + userId);
        when(tokenProvider.createRefreshToken(userId)).thenReturn("refresh-" + userId);
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((BusinessException) e).getStatus();
    }

    // ---------- login: 판정 흐름 (설계 4.1) ----------

    @Test
    void login_whenSocialAccountAlreadyLinked_returnsLoggedInWithoutNewUser() {
        User user = localUser(7L);
        when(verifier.verify(any(), any())).thenReturn(info("leeheamin@gmail.com", true, "닉"));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.of(SocialAccount.create(user, SocialProvider.KAKAO, "kakao-1")));
        givenTokens(7L);

        SocialLoginResponse response = service.login("kakao", codeRequest());

        assertThat(response.status()).isEqualTo(SocialLoginResponse.Status.LOGGED_IN);
        assertThat(response.newUser()).isFalse();
        assertThat(response.token()).isEqualTo(TokenResponse.of("access-7", "refresh-7"));
        assertThat(response.link()).isNull();
        verifyNoInteractions(userRepository, registrar, pendingSocialLinkStore);
    }

    @Test
    void login_whenLinkedAccountHasNoEmail_stillLogsIn() {
        // 이미 연결된 계정은 providerUserId 로 매칭 — 이메일 동의가 철회돼도 로그인 가능 (이메일 검사보다 먼저)
        User user = localUser(3L);
        when(verifier.verify(any(), any())).thenReturn(info(null, false, null));
        when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.of(SocialAccount.create(user, SocialProvider.KAKAO, "kakao-1")));
        givenTokens(3L);

        assertThat(service.login("KAKAO", codeRequest()).status()).isEqualTo(SocialLoginResponse.Status.LOGGED_IN);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, true",
            "'', true",
            "'   ', true",
            "user@test.com, false"
    }, nullValues = "NULL")
    void login_whenEmailMissingOrUnverified_throwsBadRequest(String email, boolean verified) {
        when(verifier.verify(any(), any())).thenReturn(info(email, verified, "닉"));
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("kakao", codeRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.EMAIL_REQUIRED_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository, registrar, pendingSocialLinkStore, tokenProvider);
    }

    @Test
    void login_whenNewEmail_registersUserAndReturnsNewUserTrue() {
        SocialUserInfo info = info("new@test.com", true, "새회원");
        when(verifier.verify(any(), any())).thenReturn(info);
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(registrar.registerNewUser(info))
                .thenReturn(withId(User.createSocial("new@test.com", "새회원", SocialProvider.KAKAO), 11L));
        givenTokens(11L);

        SocialLoginResponse response = service.login("kakao", codeRequest());

        assertThat(response.status()).isEqualTo(SocialLoginResponse.Status.LOGGED_IN);
        assertThat(response.newUser()).isTrue();
        assertThat(response.token().accessToken()).isEqualTo("access-11");
        verify(registrar).registerNewUser(info);
        verifyNoInteractions(pendingSocialLinkStore);
    }

    @Test
    void login_whenLocalUserAlreadyLinkedSameProvider_throwsConflict() {
        User user = localUser(5L);
        when(verifier.verify(any(), any())).thenReturn(info("leeheamin@gmail.com", true, "닉"));
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("leeheamin@gmail.com")).thenReturn(Optional.of(user));
        when(socialAccountRepository.existsByUserIdAndProvider(5L, SocialProvider.KAKAO)).thenReturn(true);

        assertThatThrownBy(() -> service.login("kakao", codeRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.PROVIDER_ALREADY_LINKED_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(pendingSocialLinkStore, registrar, tokenProvider);
    }

    @Test
    void login_whenLocalUserWithSameEmail_returnsLinkRequiredWithMaskedEmail() {
        User user = localUser(5L);
        when(verifier.verify(any(), any())).thenReturn(info("leeheamin@gmail.com", true, "닉"));
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("leeheamin@gmail.com")).thenReturn(Optional.of(user));
        when(socialAccountRepository.existsByUserIdAndProvider(5L, SocialProvider.KAKAO)).thenReturn(false);
        when(pendingSocialLinkStore.issue(any(), any())).thenReturn(LINK_TOKEN);

        SocialLoginResponse response = service.login("kakao", codeRequest());

        assertThat(response.status()).isEqualTo(SocialLoginResponse.Status.LINK_REQUIRED);
        assertThat(response.newUser()).isFalse();
        assertThat(response.token()).isNull();
        assertThat(response.link().linkToken()).isEqualTo(LINK_TOKEN);
        assertThat(response.link().provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(response.link().maskedEmail()).isEqualTo("le***@gmail.com");
        assertThat(response.link().expiresInSeconds()).isEqualTo(600L);

        ArgumentCaptor<PendingSocialLink> captor = ArgumentCaptor.forClass(PendingSocialLink.class);
        verify(pendingSocialLinkStore).issue(captor.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));
        assertThat(captor.getValue()).isEqualTo(new PendingSocialLink(5L, SocialProvider.KAKAO, "kakao-1"));
        verifyNoInteractions(registrar, tokenProvider);
    }

    @Test
    void login_whenSocialOnlyUserWithSameEmail_throwsConflictNamingSignupProvider() {
        User naverUser = withId(User.createSocial("leeheamin@gmail.com", "네이버회원", SocialProvider.NAVER), 9L);
        when(verifier.verify(any(), any())).thenReturn(info("leeheamin@gmail.com", true, "닉"));
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("leeheamin@gmail.com")).thenReturn(Optional.of(naverUser));

        assertThatThrownBy(() -> service.login("kakao", codeRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 네이버 로그인으로 가입된 이메일입니다.")
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(pendingSocialLinkStore, registrar, tokenProvider);
        verify(socialAccountRepository, never()).existsByUserIdAndProvider(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"KAKAO, 카카오", "GOOGLE, 구글"})
    void login_whenSocialOnlyUserOfOtherProviders_messageUsesDisplayName(SocialProvider signup, String display) {
        User socialUser = withId(User.createSocial("a@test.com", "n", signup), 9L);
        when(verifier.verify(any(), any())).thenReturn(
                new SocialUserInfo(SocialProvider.NAVER, "naver-1", "a@test.com", true, "닉"));
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(socialUser));

        assertThatThrownBy(() -> service.login("naver", codeRequest()))
                .hasMessage("이미 " + display + " 로그인으로 가입된 이메일입니다.");
    }

    @Test
    void login_whenVerifierThrows_propagatesWithoutTouchingDb() {
        when(verifier.verify(any(), any()))
                .thenThrow(new SocialAuthException(SocialAuthException.Reason.INVALID_CREDENTIAL, "bad"));

        assertThatThrownBy(() -> service.login("kakao", codeRequest()))
                .isInstanceOf(SocialAuthException.class);
        verifyNoInteractions(socialAccountRepository, userRepository, registrar, pendingSocialLinkStore);
    }

    @Test
    void login_whenCalled_verifiesBeforeAnyDbAccessAndPassesParsedCredential() {
        when(verifier.verify(any(), any())).thenReturn(info("new@test.com", true, null));
        when(socialAccountRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(registrar.registerNewUser(any()))
                .thenReturn(withId(User.createSocial("new@test.com", "new", SocialProvider.KAKAO), 1L));
        givenTokens(1L);

        service.login("Kakao", codeRequest());

        InOrder order = inOrder(verifier, socialAccountRepository, userRepository, registrar);
        order.verify(verifier).verify(org.mockito.ArgumentMatchers.eq(SocialProvider.KAKAO),
                org.mockito.ArgumentMatchers.eq(new SocialCredential.AuthorizationCode(
                        "auth-code", "http://localhost:5173/oauth/callback/kakao", null, null, null)));
        order.verify(socialAccountRepository).findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-1");
        order.verify(userRepository).findByEmail("new@test.com");
        order.verify(registrar).registerNewUser(any());
    }

    @Test
    void login_whenGrantTypeFieldMissing_throwsBadRequestBeforeVerify() {
        SocialLoginRequest request = new SocialLoginRequest(SocialGrantType.AUTHORIZATION_CODE, null,
                "http://x", null, null, null, null, null);

        assertThatThrownBy(() -> service.login("kakao", request))
                .isInstanceOf(BusinessException.class)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(verifier);
    }

    // ---------- 트랜잭션 경계 ----------

    @Test
    void socialAuthService_hasNoTransactionalSoVerifierRunsOutsideTransaction() {
        assertThat(SocialAuthService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(Arrays.stream(SocialAuthService.class.getDeclaredMethods())
                .noneMatch(m -> m.isAnnotationPresent(Transactional.class))).isTrue();
    }

    @Test
    void socialAccountRegistrar_writeMethodsAreTransactional() throws NoSuchMethodException {
        Method register = SocialAccountRegistrar.class.getMethod("registerNewUser", SocialUserInfo.class);
        Method link = SocialAccountRegistrar.class.getMethod("link", PendingSocialLink.class);

        assertThat(register.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(link.isAnnotationPresent(Transactional.class)).isTrue();
    }

    // ---------- provider 파싱 ----------

    @ParameterizedTest
    @CsvSource({"kakao, KAKAO", "KAKAO, KAKAO", "Naver, NAVER", "gOoGlE, GOOGLE"})
    void parseProvider_whenCaseInsensitiveName_returnsProvider(String name, SocialProvider expected) {
        assertThat(SocialAuthService.parseProvider(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "apple", "facebook", "kakao ", "LOCAL"})
    void parseProvider_whenUnsupported_throwsBadRequest(String name) {
        assertThatThrownBy(() -> SocialAuthService.parseProvider(name))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지원하지 않는 소셜 로그인 제공자입니다.")
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_whenProviderUnsupported_doesNotCallVerifier() {
        assertThatThrownBy(() -> service.login("apple", codeRequest())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(verifier);
    }

    // ---------- maskEmail ----------

    @ParameterizedTest
    @CsvSource({
            "leeheamin@gmail.com, le***@gmail.com",
            "abc@test.com, ab***@test.com",
            "ab@test.com, a***@test.com",
            "a@test.com, a***@test.com",
            "@test.com, ***",
            "no-at-sign, ***"
    })
    void maskEmail_whenVariousLocalParts_masksExpectedly(String email, String expected) {
        assertThat(SocialAuthService.maskEmail(email)).isEqualTo(expected);
    }

    // ---------- link (설계 4.3) ----------

    private static final PendingSocialLink PENDING = new PendingSocialLink(5L, SocialProvider.KAKAO, "kakao-1");

    @Test
    void link_whenPasswordMatches_consumesTokenLinksAndIssuesTokens() {
        User user = localUser(5L);
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "encoded-pw")).thenReturn(true);
        when(pendingSocialLinkStore.consume(LINK_TOKEN)).thenReturn(true);
        when(registrar.link(PENDING)).thenReturn(user);
        givenTokens(5L);

        TokenResponse response = service.link(new SocialLinkRequest(LINK_TOKEN, "pw"));

        assertThat(response).isEqualTo(TokenResponse.of("access-5", "refresh-5"));
        InOrder order = inOrder(pendingSocialLinkStore, registrar);
        order.verify(pendingSocialLinkStore).consume(LINK_TOKEN);
        order.verify(registrar).link(PENDING);
        verify(pendingSocialLinkStore, never()).incrementAttempts(any());
    }

    @Test
    void link_whenWrongPassword_incrementsAttemptsAndThrowsUnauthorized() {
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(localUser(5L)));
        when(passwordEncoder.matches("wrong", "encoded-pw")).thenReturn(false);
        when(pendingSocialLinkStore.incrementAttempts(LINK_TOKEN)).thenReturn(1);

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "wrong")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.LINK_WRONG_PASSWORD_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(pendingSocialLinkStore, never()).consume(any());
        verifyNoInteractions(registrar, tokenProvider);
    }

    @Test
    void link_whenFourthWrongAttempt_keepsToken() {
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(localUser(5L)));
        when(pendingSocialLinkStore.incrementAttempts(LINK_TOKEN)).thenReturn(4);

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "wrong")))
                .hasMessage(SocialAuthService.LINK_WRONG_PASSWORD_MESSAGE);
        verify(pendingSocialLinkStore, never()).consume(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6})
    void link_whenFifthWrongAttempt_discardsTokenAndThrowsUnauthorized(int attempts) {
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(localUser(5L)));
        when(pendingSocialLinkStore.incrementAttempts(LINK_TOKEN)).thenReturn(attempts);

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "wrong")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.LINK_TOO_MANY_ATTEMPTS_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(pendingSocialLinkStore).consume(LINK_TOKEN);
        verifyNoInteractions(registrar, tokenProvider);
    }

    @Test
    void link_whenTokenExpiredOrUnknown_throwsUnauthorized() {
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "pw")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.LINK_EXPIRED_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userRepository, passwordEncoder, registrar, tokenProvider);
    }

    @Test
    void link_whenTokenAlreadyConsumedConcurrently_throwsUnauthorizedWithoutLinking() {
        User user = localUser(5L);
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "encoded-pw")).thenReturn(true);
        when(pendingSocialLinkStore.consume(LINK_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "pw")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.LINK_EXPIRED_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(registrar, tokenProvider);
    }

    @Test
    void link_whenTokenExpiredBetweenFindAndIncrement_throwsExpired() {
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(localUser(5L)));
        when(pendingSocialLinkStore.incrementAttempts(LINK_TOKEN)).thenReturn(0);

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "wrong")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.LINK_EXPIRED_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(pendingSocialLinkStore, never()).consume(any());
    }

    @Test
    void link_whenUserHasNoPassword_consumesTokenAndThrowsUnauthorized() {
        User socialOnly = withId(User.createSocial("s@test.com", "소셜", SocialProvider.NAVER), 5L);
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(socialOnly));

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "pw")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SocialAuthService.LINK_EXPIRED_MESSAGE)
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(pendingSocialLinkStore).consume(LINK_TOKEN);
        verifyNoInteractions(passwordEncoder, registrar, tokenProvider);
    }

    @Test
    void link_whenUserDeleted_consumesTokenAndThrowsUnauthorized() {
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "pw")))
                .hasMessage(SocialAuthService.LINK_EXPIRED_MESSAGE);
        verify(pendingSocialLinkStore).consume(LINK_TOKEN);
        verifyNoInteractions(passwordEncoder, registrar);
    }

    @Test
    void link_whenRegistrarDetectsDuplicate_propagatesConflict() {
        User user = localUser(5L);
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenReturn(Optional.of(PENDING));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "encoded-pw")).thenReturn(true);
        when(pendingSocialLinkStore.consume(LINK_TOKEN)).thenReturn(true);
        when(registrar.link(PENDING)).thenThrow(BusinessException.conflict(SocialAccountRegistrar.ALREADY_LINKED_MESSAGE));

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "pw")))
                .extracting(SocialAuthServiceTest::statusOf).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void link_whenStoreUnavailable_propagatesServiceUnavailable() {
        when(pendingSocialLinkStore.find(LINK_TOKEN)).thenThrow(
                new SocialAuthException(SocialAuthException.Reason.SERVICE_UNAVAILABLE, "redis down"));

        assertThatThrownBy(() -> service.link(new SocialLinkRequest(LINK_TOKEN, "pw")))
                .isInstanceOf(SocialAuthException.class)
                .extracting(e -> ((SocialAuthException) e).getReason())
                .isEqualTo(SocialAuthException.Reason.SERVICE_UNAVAILABLE);
    }
}
