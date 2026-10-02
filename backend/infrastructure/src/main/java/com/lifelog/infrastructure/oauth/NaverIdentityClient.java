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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import static com.lifelog.infrastructure.oauth.ProviderCalls.*;

/**
 * 네이버 로그인 검증. authorization code 교환만 허용한다 (발급 앱 검증 API 가 없어 access token 직접 수신 불가).
 * 네이버는 오류를 HTTP 200 + error 바디(토큰) / resultcode != "00"(프로필) 으로 줄 수 있어 바디도 검사한다.
 */
@Component
public class NaverIdentityClient {

    static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    static final String USER_INFO_URI = "https://openapi.naver.com/v1/nid/me";

    private static final String SUCCESS_RESULT_CODE = "00";
    private static final SocialProvider PROVIDER = SocialProvider.NAVER;

    private final RestClient restClient;
    private final OAuthProviderProperties.Naver properties;

    public NaverIdentityClient(@Qualifier("socialRestClient") RestClient restClient, OAuthProviderProperties properties) {
        this.restClient = restClient;
        this.properties = properties.naver();
    }

    public SocialUserInfo verify(SocialCredential.AuthorizationCode credential) {
        requireConfigured();
        requireText(credential.code(), "code");
        requireText(credential.state(), "state");
        // 네이버 토큰 요청에는 redirect_uri 가 없어 제공자가 대조하지 않는다 — 입력 방어용 자체 검사
        requireAllowedRedirectUri(PROVIDER, credential.redirectUri(), properties.allowedRedirectUris());
        return exchange(credential.code(), credential.state(), credential.codeVerifier());
    }

    /** 앱 로그인 — 서버 콜백이 직접 받은 code. redirect_uri 는 서버 설정값(app-redirect-uri)이라 허용 목록 검사가 없다 */
    public SocialUserInfo verify(SocialCredential.ServerCallbackCode credential) {
        requireConfigured();
        requireText(credential.code(), "code");
        requireText(credential.state(), "state");
        return exchange(credential.code(), credential.state(), null);
    }

    private void requireConfigured() {
        if (!hasText(properties.clientId()) || !hasText(properties.clientSecret())) {
            throw notConfigured(PROVIDER);
        }
    }

    private SocialUserInfo exchange(String code, String state, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code", code);
        form.add("state", state);
        if (hasText(codeVerifier)) {
            form.add("code_verifier", codeVerifier);
        }

        JsonNode token = call(PROVIDER, "token", () -> restClient.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));

        if (text(token.path("error")) != null) {
            // 제공자 문자열은 예외 메시지에 싣지 않는다
            throw invalidCredential("NAVER token rejected");
        }
        String accessToken = text(token.path("access_token"));
        if (accessToken == null) {
            throw unavailable(PROVIDER, "token response has no access_token", null);
        }
        return fetchUser(accessToken);
    }

    private SocialUserInfo fetchUser(String accessToken) {
        JsonNode me = call(PROVIDER, "nid/me", () -> restClient.get()
                .uri(USER_INFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class));

        String resultCode = text(me.path("resultcode"));
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            throw invalidCredential("NAVER profile rejected: resultcode " + resultCode);
        }
        JsonNode response = me.path("response");
        String id = text(response.path("id"));
        if (id == null) {
            throw unavailable(PROVIDER, "profile response has no id", null);
        }
        String email = text(response.path("email"));
        String name = text(response.path("name"));
        if (name == null) {
            name = text(response.path("nickname"));
        }
        // 네이버는 이메일 검증 플래그를 제공하지 않는다 → 제공된 이메일을 검증된 것으로 간주 (설계 4.2)
        return new SocialUserInfo(PROVIDER, id, email, email != null, name);
    }
}
