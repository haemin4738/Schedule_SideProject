package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialCredential.AccessToken;
import com.lifelog.domain.user.social.SocialCredential.AuthorizationCode;
import com.lifelog.domain.user.social.SocialCredential.IdToken;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialIdentityVerifierImplTest {

    @Mock private KakaoIdentityClient kakao;
    @Mock private NaverIdentityClient naver;
    @Mock private GoogleIdentityClient google;

    private SocialIdentityVerifierImpl verifier;

    private static final AuthorizationCode CODE = new AuthorizationCode("c", "http://cb", null, null, "s");
    private static final AccessToken ACCESS = new AccessToken("at");
    private static final IdToken ID = new IdToken("jwt", null);

    @BeforeEach
    void setUp() {
        verifier = new SocialIdentityVerifierImpl(kakao, naver, google);
    }

    private static SocialUserInfo info(SocialProvider provider) {
        return new SocialUserInfo(provider, "uid", "e@test.com", true, "n");
    }

    private static void assertInvalidRequest(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(SocialAuthException.class)
                .extracting(e -> ((SocialAuthException) e).getReason())
                .isEqualTo(Reason.INVALID_REQUEST);
    }

    // ---------- 허용 조합 ----------

    @Test
    void verify_whenKakaoCode_routesToKakao() {
        when(kakao.verify(CODE)).thenReturn(info(SocialProvider.KAKAO));
        assertThat(verifier.verify(SocialProvider.KAKAO, CODE)).isEqualTo(info(SocialProvider.KAKAO));
    }

    @Test
    void verify_whenKakaoAccessToken_routesToKakao() {
        when(kakao.verify(ACCESS)).thenReturn(info(SocialProvider.KAKAO));
        assertThat(verifier.verify(SocialProvider.KAKAO, ACCESS)).isEqualTo(info(SocialProvider.KAKAO));
    }

    @Test
    void verify_whenNaverCode_routesToNaver() {
        when(naver.verify(CODE)).thenReturn(info(SocialProvider.NAVER));
        assertThat(verifier.verify(SocialProvider.NAVER, CODE)).isEqualTo(info(SocialProvider.NAVER));
    }

    @Test
    void verify_whenGoogleCode_routesToGoogle() {
        when(google.verify(CODE)).thenReturn(info(SocialProvider.GOOGLE));
        assertThat(verifier.verify(SocialProvider.GOOGLE, CODE)).isEqualTo(info(SocialProvider.GOOGLE));
    }

    @Test
    void verify_whenGoogleIdToken_routesToGoogle() {
        when(google.verify(ID)).thenReturn(info(SocialProvider.GOOGLE));
        assertThat(verifier.verify(SocialProvider.GOOGLE, ID)).isEqualTo(info(SocialProvider.GOOGLE));
    }

    // ---------- 거부 조합 ----------

    @Test
    void verify_whenKakaoIdToken_throwsInvalidRequest() {
        assertInvalidRequest(() -> verifier.verify(SocialProvider.KAKAO, ID));
        verifyNoInteractions(kakao, naver, google);
    }

    @Test
    void verify_whenNaverAccessToken_throwsInvalidRequest() {
        assertInvalidRequest(() -> verifier.verify(SocialProvider.NAVER, ACCESS));
        verifyNoInteractions(kakao, naver, google);
    }

    @Test
    void verify_whenNaverIdToken_throwsInvalidRequest() {
        assertInvalidRequest(() -> verifier.verify(SocialProvider.NAVER, ID));
        verifyNoInteractions(kakao, naver, google);
    }

    @Test
    void verify_whenGoogleAccessToken_throwsInvalidRequest() {
        assertInvalidRequest(() -> verifier.verify(SocialProvider.GOOGLE, ACCESS));
        verifyNoInteractions(kakao, naver, google);
    }

    @Test
    void verify_whenProviderOrCredentialNull_throwsInvalidRequest() {
        assertInvalidRequest(() -> verifier.verify(null, CODE));
        assertInvalidRequest(() -> verifier.verify(SocialProvider.KAKAO, (SocialCredential) null));
    }

    @Test
    void verify_whenClientThrows_rethrowsSameException() {
        SocialAuthException failure = new SocialAuthException(Reason.PROVIDER_UNAVAILABLE, "down", new RuntimeException());
        when(kakao.verify(CODE)).thenThrow(failure);

        assertThatThrownBy(() -> verifier.verify(SocialProvider.KAKAO, CODE)).isSameAs(failure);
    }

    // ---------- 실제 클라이언트와 조합: redirectUri 허용 목록 / 미설정 ----------

    @Test
    void verify_withRealClients_whenRedirectUriOutsideAllowList_throwsInvalidRequest() {
        OAuthProviderProperties props = new OAuthProviderProperties(null,
                new OAuthProviderProperties.Kakao("k", "s", "1", List.of("http://allowed/kakao")),
                new OAuthProviderProperties.Naver("n", "s", List.of("http://allowed/naver"), null),
                new OAuthProviderProperties.Google("g", "s", List.of("g"), List.of("http://allowed/google")));
        SocialIdentityVerifierImpl real = realVerifier(props);
        AuthorizationCode outside = new AuthorizationCode("c", "http://evil/cb", null, null, "s");

        for (SocialProvider provider : SocialProvider.values()) {
            assertInvalidRequest(() -> real.verify(provider, outside));
        }
    }

    @Test
    void verify_withRealClients_whenNothingConfigured_throwsServiceUnavailable() {
        SocialIdentityVerifierImpl real = realVerifier(new OAuthProviderProperties(null, null, null, null));

        assertServiceUnavailable(() -> real.verify(SocialProvider.KAKAO, CODE));
        assertServiceUnavailable(() -> real.verify(SocialProvider.KAKAO, ACCESS));
        assertServiceUnavailable(() -> real.verify(SocialProvider.NAVER, CODE));
        assertServiceUnavailable(() -> real.verify(SocialProvider.GOOGLE, CODE));
        assertServiceUnavailable(() -> real.verify(SocialProvider.GOOGLE, ID));
    }

    private static SocialIdentityVerifierImpl realVerifier(OAuthProviderProperties props) {
        RestClient restClient = RestClient.create();
        return new SocialIdentityVerifierImpl(
                new KakaoIdentityClient(restClient, props),
                new NaverIdentityClient(restClient, props),
                new GoogleIdentityClient(restClient, token -> {
                    throw new AssertionError("decoder must not be called");
                }, props));
    }

    private static void assertServiceUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(SocialAuthException.class)
                .extracting(e -> ((SocialAuthException) e).getReason())
                .isEqualTo(Reason.SERVICE_UNAVAILABLE);
    }
}
