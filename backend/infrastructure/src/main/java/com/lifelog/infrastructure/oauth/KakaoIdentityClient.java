package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import static com.lifelog.infrastructure.oauth.ProviderCalls.*;

/**
 * 카카오 로그인 검증.
 * code → 토큰 교환(client_secret), access token → access_token_info 의 app_id 대조 후 /v2/user/me 조회.
 */
@Component
public class KakaoIdentityClient {

    static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    /** 카카오 API 공통 에러 코드 -1: 플랫폼 일시적 내부 장애 (HTTP 400 으로 응답됨) */
    private static final int KAKAO_INTERNAL_ERROR_CODE = -1;

    private static final SocialProvider PROVIDER = SocialProvider.KAKAO;

    private final RestClient restClient;
    private final OAuthProviderProperties.Kakao properties;

    public KakaoIdentityClient(@Qualifier("socialRestClient") RestClient restClient, OAuthProviderProperties properties) {
        this.restClient = restClient;
        this.properties = properties.kakao();
    }

    public SocialUserInfo verify(SocialCredential.AuthorizationCode credential) {
        if (!hasText(properties.clientId()) || !hasText(properties.clientSecret())) {
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

        String accessToken = text(token.path("access_token"));
        if (accessToken == null) {
            throw unavailable(PROVIDER, "token response has no access_token", null);
        }
        return fetchUser(accessToken);
    }

    public SocialUserInfo verify(SocialCredential.AccessToken credential) {
        if (!hasText(properties.appId())) {
            throw notConfigured(PROVIDER);
        }
        requireText(credential.token(), "accessToken");

        JsonNode info = callKakaoApi("access_token_info", TOKEN_INFO_URI, credential.token());
        // 다른 앱에서 발급된 토큰 거부 (audience 검증)
        if (!properties.appId().trim().equals(text(info.path("app_id")))) {
            throw invalidCredential("KAKAO access token was issued for another app");
        }
        return fetchUser(credential.token());
    }

    private SocialUserInfo fetchUser(String accessToken) {
        JsonNode me = callKakaoApi("user/me", USER_INFO_URI, accessToken);

        String id = text(me.path("id"));
        if (id == null) {
            throw unavailable(PROVIDER, "user/me response has no id", null);
        }
        JsonNode account = me.path("kakao_account");
        String email = text(account.path("email"));
        boolean emailVerified = email != null
                && isTrue(account.path("is_email_valid"))
                && isTrue(account.path("is_email_verified"));
        String name = text(account.path("profile").path("nickname"));
        return new SocialUserInfo(PROVIDER, id, email, emailVerified, name);
    }

    private JsonNode callKakaoApi(String step, String uri, String accessToken) {
        return call(PROVIDER, step, () -> {
            try {
                return restClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (HttpClientErrorException e) {
                if (isKakaoInternalError(e)) {
                    throw unavailable(PROVIDER, step + " failed: kakao internal error", e);
                }
                throw e;
            }
        });
    }

    private static boolean isKakaoInternalError(HttpClientErrorException e) {
        try {
            JsonNode body = e.getResponseBodyAs(JsonNode.class);
            return body != null && body.path("code").isNumber() && body.path("code").intValue() == KAKAO_INTERNAL_ERROR_CODE;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
