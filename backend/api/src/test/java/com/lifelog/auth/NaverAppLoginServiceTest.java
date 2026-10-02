package com.lifelog.auth;

import com.lifelog.auth.dto.NaverAppStartResponse;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.common.exception.ErrorCode;
import com.lifelog.domain.user.social.AppLoginTicket;
import com.lifelog.domain.user.social.AppSocialLoginStore;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialIdentityVerifier;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NaverAppLoginServiceTest {

    // RFC 7636 부록 B 테스트 벡터
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    private static final String APP_REDIRECT = "http://192.168.0.10:18080/api/v1/auth/social/naver/app-callback";
    private static final String TARGET = "lifelog://oauth/naver";
    private static final SocialUserInfo INFO =
            new SocialUserInfo(SocialProvider.NAVER, "naver-uid", "n@naver.com", true, "네이버");

    @Mock
    private AppSocialLoginStore store;
    @Mock
    private SocialIdentityVerifier verifier;
    @Mock
    private SocialAuthService socialAuthService;

    private NaverAppLoginService service;

    @BeforeEach
    void setUp() {
        service = new NaverAppLoginService(store, verifier, socialAuthService, "naver-id", APP_REDIRECT, TARGET);
    }

    private static UriComponents parse(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build();
    }

    private static void assertRedirect(URI location, String name, String value) {
        UriComponents parsed = parse(location);
        assertThat(parsed.getScheme()).isEqualTo("lifelog");
        assertThat(parsed.getHost()).isEqualTo("oauth");
        assertThat(parsed.getPath()).isEqualTo("/naver");
        assertThat(parsed.getQueryParams()).containsOnlyKeys(name);
        assertThat(parsed.getQueryParams().getFirst(name)).isEqualTo(value);
    }

    // ---------- start ----------

    @Test
    void start_whenConfigured_issuesStateBoundToChallengeAndReturnsAuthorizeUrl() {
        when(store.issueState(CHALLENGE, NaverAppLoginService.STATE_TTL)).thenReturn("state-1");

        NaverAppStartResponse response = service.start(CHALLENGE);

        UriComponents url = UriComponentsBuilder.fromUriString(response.authorizeUrl()).build();
        assertThat(url.getScheme() + "://" + url.getHost() + url.getPath()).isEqualTo(NaverAppLoginService.AUTHORIZE_URI);
        assertThat(url.getQueryParams().toSingleValueMap()).containsOnlyKeys("response_type", "client_id", "redirect_uri", "state");
        assertThat(url.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(url.getQueryParams().getFirst("client_id")).isEqualTo("naver-id");
        assertThat(url.getQueryParams().getFirst("state")).isEqualTo("state-1");
        // redirect_uri 는 인코딩돼 들어간다 (네이버가 디코딩해 대조)
        assertThat(url.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("http%3A%2F%2F192.168.0.10%3A18080%2Fapi%2Fv1%2Fauth%2Fsocial%2Fnaver%2Fapp-callback");
        assertThat(response.expiresInSeconds()).isEqualTo(600);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void start_whenClientIdOrRedirectNotConfigured_throwsServiceUnavailable(String blank) {
        NaverAppLoginService noClient = new NaverAppLoginService(store, verifier, socialAuthService, blank, APP_REDIRECT, TARGET);
        NaverAppLoginService noRedirect = new NaverAppLoginService(store, verifier, socialAuthService, "naver-id", blank, TARGET);

        for (NaverAppLoginService unconfigured : new NaverAppLoginService[]{noClient, noRedirect}) {
            assertThatThrownBy(() -> unconfigured.start(CHALLENGE))
                    .isInstanceOf(SocialAuthException.class)
                    .extracting(e -> ((SocialAuthException) e).getReason()).isEqualTo(Reason.SERVICE_UNAVAILABLE);
        }
        verifyNoInteractions(store);
    }

    // ---------- callback ----------

    @Test
    void callback_whenValid_exchangesCodeOnServerAndRedirectsWithTicketOnly() {
        when(store.consumeState("state-1")).thenReturn(Optional.of(CHALLENGE));
        when(verifier.verify(SocialProvider.NAVER, new SocialCredential.ServerCallbackCode("code-1", "state-1")))
                .thenReturn(INFO);
        when(store.issueTicket(new AppLoginTicket(CHALLENGE, INFO), NaverAppLoginService.TICKET_TTL)).thenReturn("ticket-1");

        URI location = service.callback("code-1", "state-1", null);

        assertRedirect(location, "ticket", "ticket-1");
        assertThat(location.toString()).doesNotContain("code-1").doesNotContain("state-1");
    }

    @Test
    void callback_whenTicketHasReservedCharacters_encodesThem() {
        when(store.consumeState("s")).thenReturn(Optional.of(CHALLENGE));
        when(verifier.verify(any(), any())).thenReturn(INFO);
        when(store.issueTicket(any(), any())).thenReturn("a&b=c+d#e");

        URI location = service.callback("c", "s", null);

        assertThat(location.toString()).isEqualTo("lifelog://oauth/naver?ticket=a%26b%3Dc%2Bd%23e");
    }

    @Test
    void callback_whenProviderReturnsError_redirectsAccessDeniedAndDiscardsState() {
        URI location = service.callback(null, "state-1", "access_denied");

        assertRedirect(location, "error", "ACCESS_DENIED");
        verify(store).consumeState("state-1");
        verifyNoInteractions(verifier);
    }

    @Test
    void callback_whenErrorAndStateCleanupFails_stillRedirectsAccessDenied() {
        doThrow(new SocialAuthException(Reason.SERVICE_UNAVAILABLE, "down")).when(store).consumeState("state-1");

        assertRedirect(service.callback(null, "state-1", "access_denied"), "error", "ACCESS_DENIED");
    }

    @Test
    void callback_whenStateUnknownOrReused_redirectsInvalidStateWithoutExchange() {
        when(store.consumeState("state-1")).thenReturn(Optional.empty());

        assertRedirect(service.callback("code-1", "state-1", null), "error", "INVALID_STATE");
        verifyNoInteractions(verifier);
        verify(store, never()).issueTicket(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void callback_whenCodeMissing_redirectsProviderErrorAfterConsumingState(String code) {
        when(store.consumeState("state-1")).thenReturn(Optional.of(CHALLENGE));

        assertRedirect(service.callback(code, "state-1", null), "error", "PROVIDER_ERROR");
        verifyNoInteractions(verifier);
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void callback_whenExchangeFails_redirectsProviderErrorWithoutTicket(Reason reason) {
        when(store.consumeState("state-1")).thenReturn(Optional.of(CHALLENGE));
        when(verifier.verify(any(), any())).thenThrow(new SocialAuthException(reason, "NAVER token rejected"));

        assertRedirect(service.callback("code-1", "state-1", null), "error", "PROVIDER_ERROR");
        verify(store, never()).issueTicket(any(), any());
    }

    @Test
    void callback_whenStoreUnavailable_redirectsProviderError() {
        when(store.consumeState("state-1")).thenThrow(new SocialAuthException(Reason.SERVICE_UNAVAILABLE, "down"));

        assertRedirect(service.callback("code-1", "state-1", null), "error", "PROVIDER_ERROR");
    }

    // ---------- complete ----------

    @Test
    void complete_whenVerifierMatches_resolvesLikeWebLogin() {
        SocialLoginResponse expected = SocialLoginResponse.loggedIn(TokenResponse.of("a", "r"), false);
        when(store.consumeTicket("ticket-1")).thenReturn(Optional.of(new AppLoginTicket(CHALLENGE, INFO)));
        when(socialAuthService.resolve(INFO)).thenReturn(expected);

        assertThat(service.complete("ticket-1", VERIFIER)).isSameAs(expected);
    }

    @Test
    void complete_whenTicketUnknownExpiredOrUsed_throws401TicketInvalid() {
        when(store.consumeTicket("ticket-1")).thenReturn(Optional.empty());

        assertTicketInvalid(() -> service.complete("ticket-1", VERIFIER));
        verifyNoInteractions(socialAuthService);
    }

    @Test
    void complete_whenVerifierDoesNotMatch_throws401TicketInvalidWithoutResolving() {
        when(store.consumeTicket("ticket-1")).thenReturn(Optional.of(new AppLoginTicket(CHALLENGE, INFO)));

        assertTicketInvalid(() -> service.complete("ticket-1", VERIFIER.replace('d', 'e')));
        verifyNoInteractions(socialAuthService);
    }

    private static void assertTicketInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .hasMessage(NaverAppLoginService.TICKET_INVALID_MESSAGE)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SOCIAL_APP_TICKET_INVALID)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    // ---------- matches ----------

    @Test
    void matches_whenRfc7636Vector_returnsTrue() {
        assertThat(NaverAppLoginService.matches(VERIFIER, CHALLENGE)).isTrue();
    }

    @Test
    void matches_whenDifferentOrNull_returnsFalse() {
        assertThat(NaverAppLoginService.matches(VERIFIER + "x", CHALLENGE)).isFalse();
        // 평문 verifier 를 challenge 자리에 그대로 넣어도(plain 방식) 통과하지 않는다
        assertThat(NaverAppLoginService.matches(VERIFIER, VERIFIER)).isFalse();
        assertThat(NaverAppLoginService.matches(null, CHALLENGE)).isFalse();
        assertThat(NaverAppLoginService.matches(VERIFIER, null)).isFalse();
    }
}
