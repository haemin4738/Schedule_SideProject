package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 구글 id_token 검증 테스트. 로컬 RSA 키로 서명한 JWT 와 해당 공개키 디코더를 사용한다.
 */
class GoogleIdentityClientTest {

    private static final String WEB_CLIENT_ID = "web-client.apps.googleusercontent.com";
    private static final String IOS_CLIENT_ID = "ios-client.apps.googleusercontent.com";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback/google";

    private static RSAKey signingKey;
    private static RSAKey otherKey;

    private MockRestServiceServer server;
    private GoogleIdentityClient client;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("other-kid").generate();
    }

    @BeforeEach
    void setUp() throws JOSEException {
        client = newClient(new OAuthProviderProperties.Google(WEB_CLIENT_ID, "google-secret",
                List.of(WEB_CLIENT_ID, IOS_CLIENT_ID), List.of(REDIRECT_URI)),
                NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build());
    }

    private GoogleIdentityClient newClient(OAuthProviderProperties.Google google, JwtDecoder decoder) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GoogleIdentityClient(builder.build(), decoder, new OAuthProviderProperties(null, null, null, google));
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(WEB_CLIENT_ID)
                .subject("google-sub-1")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("email", "g@gmail.com")
                .claim("email_verified", true)
                .claim("name", "구글이름");
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String idToken(Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder builder = validClaims();
        customizer.accept(builder);
        return sign(signingKey, builder.build());
    }

    private static void assertReason(Runnable call, Reason reason) {
        assertThatThrownBy(call::run)
                .isInstanceOf(SocialAuthException.class)
                .extracting(e -> ((SocialAuthException) e).getReason())
                .isEqualTo(reason);
    }

    private SocialUserInfo verifyIdToken(String token, String nonce) {
        return client.verify(new SocialCredential.IdToken(token, nonce));
    }

    // ---------- id_token (Flutter) ----------

    @Test
    void verifyIdToken_whenValid_returnsUserInfo() {
        SocialUserInfo info = verifyIdToken(idToken(b -> {}), null);

        assertThat(info).isEqualTo(new SocialUserInfo(SocialProvider.GOOGLE, "google-sub-1", "g@gmail.com", true, "구글이름"));
    }

    @Test
    void verifyIdToken_whenIosAudienceAndShortIssuer_isAccepted() {
        String token = idToken(b -> b.audience(IOS_CLIENT_ID).issuer("accounts.google.com"));

        assertThat(verifyIdToken(token, null).providerUserId()).isEqualTo("google-sub-1");
    }

    @Test
    void verifyIdToken_whenMultipleAudiencesIncludeAllowed_isAccepted() {
        String token = idToken(b -> b.audience(List.of("someone-else", WEB_CLIENT_ID)));

        assertThat(verifyIdToken(token, null).providerUserId()).isEqualTo("google-sub-1");
    }

    @Test
    void verifyIdToken_whenAudienceMismatch_throwsInvalidCredential() {
        assertReason(() -> verifyIdToken(idToken(b -> b.audience("attacker-client")), null), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenAudienceMissing_throwsInvalidCredential() {
        assertReason(() -> verifyIdToken(idToken(b -> b.audience((String) null)), null), Reason.INVALID_CREDENTIAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example.com", "https://accounts.google.com/", "http://accounts.google.com"})
    void verifyIdToken_whenIssuerMismatch_throwsInvalidCredential(String issuer) {
        assertReason(() -> verifyIdToken(idToken(b -> b.issuer(issuer)), null), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenExpired_throwsInvalidCredential() {
        Instant past = Instant.now().minusSeconds(3600);
        String token = idToken(b -> b.issueTime(Date.from(past.minusSeconds(3600))).expirationTime(Date.from(past)));

        assertReason(() -> verifyIdToken(token, null), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenSignedWithOtherKey_throwsInvalidCredential() {
        String token = sign(otherKey, validClaims().build());

        assertReason(() -> verifyIdToken(token, null), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenPayloadTampered_throwsInvalidCredential() {
        String[] parts = idToken(b -> {}).split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                validClaims().subject("victim-sub").build().toString().getBytes());

        assertReason(() -> verifyIdToken(parts[0] + "." + forgedPayload + "." + parts[2], null), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenMalformed_throwsInvalidCredential() {
        assertReason(() -> verifyIdToken("not-a-jwt", null), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenEmailVerifiedFalse_returnsUnverified() {
        SocialUserInfo info = verifyIdToken(idToken(b -> b.claim("email_verified", false)), null);

        assertThat(info.email()).isEqualTo("g@gmail.com");
        assertThat(info.emailVerified()).isFalse();
    }

    @Test
    void verifyIdToken_whenEmailVerifiedIsStringTrue_isTreatedAsVerified() {
        // Spring 의 getClaimAsBoolean 은 "true" 문자열도 true 로 변환한다
        SocialUserInfo info = verifyIdToken(idToken(b -> b.claim("email_verified", "true")), null);

        assertThat(info.emailVerified()).isTrue();
    }

    @Test
    void verifyIdToken_whenEmailMissing_returnsNullEmailUnverified() {
        SocialUserInfo info = verifyIdToken(idToken(b -> b.claim("email", null).claim("email_verified", true)), null);

        assertThat(info.email()).isNull();
        assertThat(info.emailVerified()).isFalse();
    }

    @Test
    void verifyIdToken_whenNonceMatches_isAccepted() {
        assertThat(verifyIdToken(idToken(b -> b.claim("nonce", "n-1")), "n-1").providerUserId()).isEqualTo("google-sub-1");
    }

    @Test
    void verifyIdToken_whenNonceMismatch_throwsInvalidCredential() {
        assertReason(() -> verifyIdToken(idToken(b -> b.claim("nonce", "n-1")), "n-2"), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenExpectedNonceButTokenHasNone_throwsInvalidCredential() {
        assertReason(() -> verifyIdToken(idToken(b -> {}), "n-1"), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenSubMissing_throwsInvalidCredential() {
        assertReason(() -> verifyIdToken(idToken(b -> b.subject(null)), null), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyIdToken_whenJwkSetUnavailable_throwsProviderUnavailable() {
        GoogleIdentityClient jwksDown = newClient(
                new OAuthProviderProperties.Google(WEB_CLIENT_ID, "s", List.of(WEB_CLIENT_ID), List.of(REDIRECT_URI)),
                token -> {
                    throw new JwtException("Couldn't retrieve remote JWK set");
                });

        assertReason(() -> jwksDown.verify(new SocialCredential.IdToken("x.y.z", null)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyIdToken_whenAudiencesNotConfigured_throwsServiceUnavailable() throws JOSEException {
        GoogleIdentityClient unconfigured = newClient(
                new OAuthProviderProperties.Google(WEB_CLIENT_ID, "s", List.of("", " "), List.of(REDIRECT_URI)),
                NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build());

        assertReason(() -> unconfigured.verify(new SocialCredential.IdToken(idToken(b -> {}), null)),
                Reason.SERVICE_UNAVAILABLE);
    }

    @Test
    void verifyIdToken_whenTokenBlank_throwsInvalidRequest() {
        assertReason(() -> verifyIdToken(" ", null), Reason.INVALID_REQUEST);
    }

    // ---------- authorization code (웹) ----------

    private SocialCredential.AuthorizationCode code(String verifier, String nonce) {
        return new SocialCredential.AuthorizationCode("auth-code", REDIRECT_URI, verifier, nonce, null);
    }

    @Test
    void verifyCode_whenSuccess_exchangesCodeAndVerifiesIdToken() {
        String token = idToken(b -> b.claim("nonce", "web-nonce"));
        server.expect(requestTo(GoogleIdentityClient.TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", WEB_CLIENT_ID,
                        "client_secret", "google-secret",
                        "redirect_uri", REDIRECT_URI,
                        "code", "auth-code",
                        "code_verifier", "pkce-verifier")))
                .andRespond(withSuccess("{\"access_token\":\"ya29\",\"id_token\":\"" + token + "\"}",
                        MediaType.APPLICATION_JSON));

        SocialUserInfo info = client.verify(code("pkce-verifier", "web-nonce"));

        assertThat(info.providerUserId()).isEqualTo("google-sub-1");
        assertThat(info.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void verifyCode_whenExchangedIdTokenNonceMismatch_throwsInvalidCredential() {
        String token = idToken(b -> b.claim("nonce", "other"));
        server.expect(requestTo(GoogleIdentityClient.TOKEN_URI))
                .andRespond(withSuccess("{\"id_token\":\"" + token + "\"}", MediaType.APPLICATION_JSON));

        assertReason(() -> client.verify(code(null, "web-nonce")), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyCode_whenTokenResponseHasNoIdToken_throwsInvalidCredential() {
        server.expect(requestTo(GoogleIdentityClient.TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"ya29\"}", MediaType.APPLICATION_JSON));

        assertReason(() -> client.verify(code(null, null)), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyCode_whenTokenEndpoint4xx_throwsInvalidCredential() {
        server.expect(requestTo(GoogleIdentityClient.TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        assertReason(() -> client.verify(code(null, null)), Reason.INVALID_CREDENTIAL);
    }

    @Test
    void verifyCode_whenTokenEndpoint5xx_throwsProviderUnavailable() {
        server.expect(requestTo(GoogleIdentityClient.TOKEN_URI)).andRespond(withServerError());

        assertReason(() -> client.verify(code(null, null)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenTimeout_throwsProviderUnavailable() {
        server.expect(requestTo(GoogleIdentityClient.TOKEN_URI))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertReason(() -> client.verify(code(null, null)), Reason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void verifyCode_whenRedirectUriNotAllowed_throwsInvalidRequest() {
        assertReason(() -> client.verify(new SocialCredential.AuthorizationCode(
                "auth-code", "http://localhost:5173/oauth/callback/GOOGLE", null, null, null)), Reason.INVALID_REQUEST);
        server.verify();
    }

    @Test
    void verifyCode_whenSecretNotConfigured_throwsServiceUnavailable() throws JOSEException {
        GoogleIdentityClient unconfigured = newClient(
                new OAuthProviderProperties.Google(WEB_CLIENT_ID, null, List.of(WEB_CLIENT_ID), List.of(REDIRECT_URI)),
                NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build());

        assertReason(() -> unconfigured.verify(code(null, null)), Reason.SERVICE_UNAVAILABLE);
    }
}
