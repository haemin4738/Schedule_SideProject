package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialAuthException.Reason;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

import static com.lifelog.infrastructure.oauth.ProviderCalls.*;

/**
 * 구글 로그인 검증. code 는 토큰 엔드포인트에서 id_token 으로 교환하고, id_token 은 경로와 무관하게
 * JWKS 서명 + exp(디코더) 와 iss / aud / nonce(이 클래스) 를 검증한다.
 */
@Component
public class GoogleIdentityClient {

    static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    static final Set<String> ALLOWED_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private static final SocialProvider PROVIDER = SocialProvider.GOOGLE;

    private final RestClient restClient;
    private final JwtDecoder idTokenDecoder;
    private final OAuthProviderProperties.Google properties;

    public GoogleIdentityClient(@Qualifier("socialRestClient") RestClient restClient,
                                @Qualifier("googleIdTokenDecoder") JwtDecoder idTokenDecoder,
                                OAuthProviderProperties properties) {
        this.restClient = restClient;
        this.idTokenDecoder = idTokenDecoder;
        this.properties = properties.google();
    }

    public SocialUserInfo verify(SocialCredential.AuthorizationCode credential) {
        if (!hasText(properties.clientId()) || !hasText(properties.clientSecret())
                || properties.allowedAudiences().isEmpty()) {
            throw notConfigured(PROVIDER);
        }
        requireText(credential.code(), "code");
        requireAllowedRedirectUri(PROVIDER, credential.redirectUri(), properties.allowedRedirectUris());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", credential.redirectUri());
        form.add("code", credential.code());
        if (hasText(credential.codeVerifier())) {
            form.add("code_verifier", credential.codeVerifier());
        }

        JsonNode token = call(PROVIDER, "token", () -> restClient.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));

        String idToken = text(token.path("id_token"));
        if (idToken == null) {
            // openid scope 누락 등 — 설정/요청 문제이므로 제공자 장애로 보지 않는다
            throw invalidCredential("GOOGLE token response has no id_token");
        }
        return verifyIdToken(idToken, credential.nonce());
    }

    public SocialUserInfo verify(SocialCredential.IdToken credential) {
        if (properties.allowedAudiences().isEmpty()) {
            throw notConfigured(PROVIDER);
        }
        requireText(credential.token(), "idToken");
        return verifyIdToken(credential.token(), credential.nonce());
    }

    private SocialUserInfo verifyIdToken(String idToken, String expectedNonce) {
        Jwt jwt = decode(idToken);

        if (!ALLOWED_ISSUERS.contains(jwt.getClaimAsString(JwtClaimNames.ISS))) {
            throw invalidCredential("GOOGLE id_token issuer mismatch");
        }
        List<String> audience = jwt.getAudience();
        if (audience == null || audience.stream().noneMatch(properties.allowedAudiences()::contains)) {
            throw invalidCredential("GOOGLE id_token audience mismatch");
        }
        if (hasText(expectedNonce) && !expectedNonce.equals(jwt.getClaimAsString("nonce"))) {
            throw invalidCredential("GOOGLE id_token nonce mismatch");
        }
        String sub = jwt.getSubject();
        if (!hasText(sub)) {
            throw invalidCredential("GOOGLE id_token has no sub");
        }

        String email = jwt.getClaimAsString("email");
        boolean emailVerified = hasText(email) && Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
        return new SocialUserInfo(PROVIDER, sub, hasText(email) ? email : null, emailVerified,
                jwt.getClaimAsString("name"));
    }

    private Jwt decode(String idToken) {
        try {
            return idTokenDecoder.decode(idToken);
        } catch (BadJwtException e) {
            // 서명 불일치, 형식 오류, 만료(JwtValidationException) 등
            throw new SocialAuthException(Reason.INVALID_CREDENTIAL, "GOOGLE id_token is invalid", e);
        } catch (JwtException e) {
            // JWK Set 조회 실패 등
            throw unavailable(PROVIDER, "id_token verification failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
