package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NaverIdentityClientTest {

    private static final String WEB_REDIRECT = "http://localhost:5173/oauth/callback/naver";
    private static final String APP_REDIRECT = "http://localhost:8080/api/v1/auth/social/naver/app-callback";

    private MockRestServiceServer server;
    private NaverIdentityClient client;

    @BeforeEach
    void setUp() {
        client = newClient(new OAuthProviderProperties.Naver("naver-id", "naver-secret",
                List.of(WEB_REDIRECT, APP_REDIRECT), "lifelog://oauth/naver"));
    }

    private NaverIdentityClient newClient(OAuthProviderProperties.Naver naver) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new NaverIdentityClient(builder.build(), new OAuthProviderProperties(null, null, naver, null));
    }

    private static SocialCredential.AuthorizationCode code(String redirectUri) {
        return new SocialCredential.AuthorizationCode("auth-code", redirectUri, null, null, "state-1");
    }

    private void expectToken(String json) {
        server.expect(requestTo(NaverIdentityClient.TOKEN_URI))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectProfile(String json) {
        server.expect(requestTo(NaverIdentityClient.USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer naver-at"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static void assertReason(Runnable call, Reason reason) {
        assertThatThrownBy(call::run)
                .isInstanceOf(SocialAuthException.class)
                .extracting(e -> ((SocialAuthException) e).getReason())
                .isEqualTo(reason);
    }

    @Test
    void verify_whenSuccess_sendsCodeAndStateAndReturnsUserInfo() {
        server.expect(requestTo(NaverIdentityClient.TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "naver-id",
                        "client_secret", "naver-secret",
                        "code", "auth-code",
                        "state", "state-1")))
                .andRespond(withSuccess("{\"access_token\":\"naver-at\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
        expectProfile("""
                {"resultcode":"00","message":"success",
                 "response":{"id":"naver-uid","email":"n@naver.com","name":"네이버이름","nickname":"닉"}}
                """);

        SocialUserInfo info = client.verify(code(WEB_REDIRECT));

        assertThat(info).isEqualTo(new SocialUserInfo(SocialProvider.NAVER, "naver-uid", "n@naver.com", true, "네이버이름"));
        server.verify();
    }

    @Test
    void verify_whenAppCallbackRedirectUri_isAllowed() {
        expectToken("{\"access_token\":\"naver-at\"}");
        expectProfile("{\"resultcode\":\"00\",\"response\":{\"id\":\"u\",\"email\":\"n@naver.com\"}}");

        assertThat(client.verify(code(APP_REDIRECT)).providerUserId()).isEqualTo("u");
    }

    @Test
    void verify_whenNameMissing_fallsBackToNickname() {
        expectToken("{\"access_token\":\"naver-at\"}");
        expectProfile("{\"resultcode\":\"00\",\"response\":{\"id\":\"u\",\"email\":\"n@naver.com\",\"nickname\":\"닉\"}}");

        assertThat(client.verify(code(WEB_REDIRECT)).name()).isEqualTo("닉");
    }

    @Test
    void verify_whenEmailMissing_returnsNullEmailUnverified() {
        expectToken("{\"access_token\":\"naver-at\"}");
        expectProfile("{\"resultcode\":\"00\",\"response\":{\"id\":\"u\"}}");

        SocialUserInfo info = client.verify(code(WEB_REDIRECT));

        assertThat(info.email()).isNull();
        assertThat(info.emailVerified()).isFalse();
        assertThat(info.name()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void verify_whenStateMissing_throwsInvalidRequestWithoutCall(String state) {
        assertReason(() -> client.verify(new SocialCredential.AuthorizationCode("auth-code", WEB_REDIRECT, null, null, state)),
                Reason.INVALID_REQUEST);
        server.verify();
    }

    @Test
    void verify_whenTokenEndpointReturns200WithErrorBody_throwsInvalidCredential() {
        expectToken("{\"error\":\"invalid_request\",\"error_description\":\"no valid data in session\"}");

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.INVALID_CREDENTIAL);
        server.verify();
    }

    @Test
    void verify_whenTokenResponseHasNoAccessToken_throwsProviderUnavailable() {
        expectToken("{}");

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verify_whenProfileResultCodeNotSuccess_throwsInvalidCredential() {
        expectToken("{\"access_token\":\"naver-at\"}");
        expectProfile("{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}");

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verify_whenProfileHasNoId_throwsProviderUnavailable() {
        expectToken("{\"access_token\":\"naver-at\"}");
        expectProfile("{\"resultcode\":\"00\",\"response\":{\"email\":\"n@naver.com\"}}");

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verify_whenTokenEndpoint4xx_throwsInvalidCredential() {
        server.expect(requestTo(NaverIdentityClient.TOKEN_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verify_whenProfile5xx_throwsProviderUnavailable() {
        expectToken("{\"access_token\":\"naver-at\"}");
        server.expect(requestTo(NaverIdentityClient.USER_INFO_URI)).andRespond(withServerError());

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verify_whenTimeout_throwsProviderUnavailable() {
        server.expect(requestTo(NaverIdentityClient.TOKEN_URI))
                .andRespond(withException(new SocketTimeoutException("connect timed out")));

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verify_whenIoError_throwsProviderUnavailable() {
        server.expect(requestTo(NaverIdentityClient.TOKEN_URI))
                .andRespond(withException(new IOException("connection reset")));

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verify_whenUnexpected3xx_throwsProviderUnavailable() {
        server.expect(requestTo(NaverIdentityClient.TOKEN_URI)).andRespond(withStatus(HttpStatus.FOUND));

        assertReason(() -> client.verify(code(WEB_REDIRECT)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verify_whenRedirectUriNotAllowed_throwsInvalidRequest() {
        assertReason(() -> client.verify(code("http://localhost:5173/oauth/callback/kakao")), Reason.INVALID_REQUEST);
        assertReason(() -> client.verify(code(null)), Reason.INVALID_REQUEST);
    }

    @Test
    void verify_whenNotConfigured_throwsServiceUnavailable() {
        NaverIdentityClient unconfigured = newClient(new OAuthProviderProperties.Naver(null, null, List.of(WEB_REDIRECT), null));

        assertReason(() -> unconfigured.verify(code(WEB_REDIRECT)), Reason.SERVICE_UNAVAILABLE);
    }
}
