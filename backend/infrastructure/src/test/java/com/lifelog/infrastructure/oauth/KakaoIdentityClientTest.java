package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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

class KakaoIdentityClientTest {

    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback/kakao";
    private static final String USER_ME = """
            {"id": 123456789,
             "kakao_account": {"email": "kakao@test.com", "is_email_valid": true, "is_email_verified": true,
                               "email_needs_agreement": false, "profile": {"nickname": "카카오닉"}}}
            """;

    private MockRestServiceServer server;
    private KakaoIdentityClient client;

    @BeforeEach
    void setUp() {
        client = newClient(new OAuthProviderProperties.Kakao("rest-key", "secret", "1234", List.of(REDIRECT_URI)));
    }

    private KakaoIdentityClient newClient(OAuthProviderProperties.Kakao kakao) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new KakaoIdentityClient(builder.build(), new OAuthProviderProperties(null, kakao, null, null));
    }

    private static SocialCredential.AuthorizationCode code() {
        return new SocialCredential.AuthorizationCode("auth-code", REDIRECT_URI, null, null, null);
    }

    private void expectToken(String json) {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectUserMe(String accessToken, String json) {
        server.expect(requestTo(KakaoIdentityClient.USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + accessToken))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static void assertReason(Runnable call, Reason reason) {
        assertThatThrownBy(call::run)
                .isInstanceOf(SocialAuthException.class)
                .extracting(e -> ((SocialAuthException) e).getReason())
                .isEqualTo(reason);
    }

    // ---------- authorization code ----------

    @Test
    void verifyCode_whenSuccess_exchangesCodeAndReturnsUserInfo() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "rest-key",
                        "client_secret", "secret",
                        "redirect_uri", REDIRECT_URI,
                        "code", "auth-code")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-at\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
        expectUserMe("kakao-at", USER_ME);

        SocialUserInfo info = client.verify(code());

        assertThat(info).isEqualTo(new SocialUserInfo(SocialProvider.KAKAO, "123456789", "kakao@test.com", true, "카카오닉"));
        server.verify();
    }

    @Test
    void verifyCode_whenCodeVerifierGiven_sendsIt() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_URI))
                .andExpect(content().formDataContains(Map.of("code_verifier", "pkce-verifier")))
                .andRespond(withSuccess("{\"access_token\":\"at\"}", MediaType.APPLICATION_JSON));
        expectUserMe("at", USER_ME);

        client.verify(new SocialCredential.AuthorizationCode("auth-code", REDIRECT_URI, "pkce-verifier", null, null));
        server.verify();
    }

    @Test
    void verifyCode_whenTokenEndpoint4xx_throwsInvalidCredential() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\"}"));

        assertReason(() -> client.verify(code()), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyCode_whenTokenEndpoint5xx_throwsProviderUnavailable() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_URI)).andRespond(withServerError());

        assertReason(() -> client.verify(code()), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenTimeout_throwsProviderUnavailable() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_URI))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertReason(() -> client.verify(code()), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenTokenResponseHasNoAccessToken_throwsProviderUnavailable() {
        expectToken("{\"token_type\":\"bearer\"}");

        assertReason(() -> client.verify(code()), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenTokenResponseNotJsonObject_throwsProviderUnavailable() {
        expectToken("[]");

        assertReason(() -> client.verify(code()), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenTokenResponseMalformed_throwsProviderUnavailable() {
        expectToken("{not-json");

        assertReason(() -> client.verify(code()), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenUserMeReturnsKakaoInternalError_throwsProviderUnavailable() {
        expectToken("{\"access_token\":\"at\"}");
        server.expect(requestTo(KakaoIdentityClient.USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"internal error\",\"code\":-1}"));

        assertReason(() -> client.verify(code()), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenUserMeReturns401_throwsInvalidCredential() {
        expectToken("{\"access_token\":\"at\"}");
        server.expect(requestTo(KakaoIdentityClient.USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"this access token does not exist\",\"code\":-401}"));

        assertReason(() -> client.verify(code()), Reason.INVALID_CREDENTIAL);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"msg\":\"this access token does not exist\",\"code\":-401}          | errorCode=-401",
            "{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\"}          | errorCode=KOE320",
            "{\"error\":\"secret value with spaces and : colons\"}            | errorCode=null",
            "not-json                                                           | errorCode=null"
    })
    @ExtendWith(OutputCaptureExtension.class)
    void verifyCode_whenProvider4xx_logsOnlySafeErrorCode(String body, String expectedLog, CapturedOutput output) {
        expectToken("{\"access_token\":\"at\"}");
        server.expect(requestTo(KakaoIdentityClient.USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(body));

        assertReason(() -> client.verify(code()), Reason.INVALID_CREDENTIAL);

        assertThat(output).contains("provider=KAKAO", "status=401", expectedLog);
        assertThat(output).doesNotContain("secret value", "this access token does not exist");
    }

    @Test
    void verifyCode_whenUserMe400WithNonJsonBody_throwsInvalidCredential() {
        expectToken("{\"access_token\":\"at\"}");
        server.expect(requestTo(KakaoIdentityClient.USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("oops"));

        assertReason(() -> client.verify(code()), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyCode_whenUserMeHasNoId_throwsProviderUnavailable() {
        expectToken("{\"access_token\":\"at\"}");
        expectUserMe("at", "{\"kakao_account\":{}}");

        assertReason(() -> client.verify(code()), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenRedirectUriNotAllowed_throwsInvalidRequestWithoutCall() {
        assertReason(() -> client.verify(new SocialCredential.AuthorizationCode(
                "auth-code", REDIRECT_URI + "/", null, null, null)), Reason.INVALID_REQUEST);
        assertReason(() -> client.verify(new SocialCredential.AuthorizationCode(
                "auth-code", "https://evil.example.com/oauth/callback/kakao", null, null, null)), Reason.INVALID_REQUEST);
        server.verify();
    }

    @Test
    void verifyCode_whenCodeBlank_throwsInvalidRequest() {
        assertReason(() -> client.verify(new SocialCredential.AuthorizationCode(" ", REDIRECT_URI, null, null, null)),
                Reason.INVALID_REQUEST);
    }

    @Test
    void verifyCode_whenClientSecretNotConfigured_throwsServiceUnavailable() {
        KakaoIdentityClient unconfigured = newClient(
                new OAuthProviderProperties.Kakao("rest-key", "", "1234", List.of(REDIRECT_URI)));

        assertReason(() -> unconfigured.verify(code()), Reason.SERVICE_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenAllowedRedirectUrisEmpty_throwsServiceUnavailable() {
        KakaoIdentityClient noUris = newClient(new OAuthProviderProperties.Kakao("rest-key", "secret", "1234", null));

        assertReason(() -> noUris.verify(code()), Reason.SERVICE_UNAVAILABLE);
    }

    // ---------- email 플래그 ----------

    @ParameterizedTest
    @CsvSource({
            "true, true, true",
            "true, false, false",
            "false, true, false",
            "false, false, false",
            "'\"true\"', true, false",
            "null, true, false"
    })
    void verifyCode_whenEmailFlagsCombination_setsEmailVerified(String valid, String verified, boolean expected) {
        expectToken("{\"access_token\":\"at\"}");
        expectUserMe("at", """
                {"id": 1, "kakao_account": {"email": "e@test.com", "is_email_valid": %s, "is_email_verified": %s}}
                """.formatted(valid, verified));

        SocialUserInfo info = client.verify(code());

        assertThat(info.email()).isEqualTo("e@test.com");
        assertThat(info.emailVerified()).isEqualTo(expected);
        assertThat(info.name()).isNull();
    }

    @Test
    void verifyCode_whenEmailNotProvided_returnsNullEmailUnverified() {
        expectToken("{\"access_token\":\"at\"}");
        expectUserMe("at", """
                {"id": 1, "kakao_account": {"email_needs_agreement": true, "is_email_valid": true, "is_email_verified": true}}
                """);

        SocialUserInfo info = client.verify(code());

        assertThat(info.email()).isNull();
        assertThat(info.emailVerified()).isFalse();
    }

    // ---------- access token ----------

    @Test
    void verifyAccessToken_whenAppIdMatches_returnsUserInfo() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer sdk-at"))
                .andRespond(withSuccess("{\"id\":123456789,\"expires_in\":7199,\"app_id\":1234}",
                        MediaType.APPLICATION_JSON));
        expectUserMe("sdk-at", USER_ME);

        SocialUserInfo info = client.verify(new SocialCredential.AccessToken("sdk-at"));

        assertThat(info.providerUserId()).isEqualTo("123456789");
        assertThat(info.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void verifyAccessToken_whenAppIdMismatch_throwsInvalidCredentialWithoutUserMe() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":1,\"app_id\":9999}", MediaType.APPLICATION_JSON));

        assertReason(() -> client.verify(new SocialCredential.AccessToken("other-app-at")), Reason.INVALID_CREDENTIAL);
        server.verify();
    }

    @Test
    void verifyAccessToken_whenAppIdMissing_throwsInvalidCredential() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        assertReason(() -> client.verify(new SocialCredential.AccessToken("at")), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyAccessToken_whenTokenInfo401_throwsInvalidCredential() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"no token\",\"code\":-401}"));

        assertReason(() -> client.verify(new SocialCredential.AccessToken("at")), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyAccessToken_whenTokenInfoInternalError_throwsProviderUnavailable() {
        server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"internal\",\"code\":-1}"));

        assertReason(() -> client.verify(new SocialCredential.AccessToken("at")), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyAccessToken_whenAppIdHasSurroundingSpaces_stillMatches() {
        KakaoIdentityClient spaced = newClient(new OAuthProviderProperties.Kakao("k", "s", " 1234 ", List.of()));
        server.expect(requestTo(KakaoIdentityClient.TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"app_id\":1234}", MediaType.APPLICATION_JSON));
        expectUserMe("at", USER_ME);

        assertThat(spaced.verify(new SocialCredential.AccessToken("at")).providerUserId()).isEqualTo("123456789");
    }

    @Test
    void verifyAccessToken_whenAppIdNotConfigured_throwsServiceUnavailable() {
        KakaoIdentityClient unconfigured = newClient(new OAuthProviderProperties.Kakao("k", "s", " ", List.of()));

        assertReason(() -> unconfigured.verify(new SocialCredential.AccessToken("at")), Reason.SERVICE_UNAVAILABLE);
    }

    @Test
    void verifyAccessToken_whenTokenBlank_throwsInvalidRequest() {
        assertReason(() -> client.verify(new SocialCredential.AccessToken("")), Reason.INVALID_REQUEST);
    }
}
