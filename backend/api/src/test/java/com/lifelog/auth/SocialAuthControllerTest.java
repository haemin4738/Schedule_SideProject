package com.lifelog.auth;

import com.lifelog.auth.dto.SocialLinkRequest;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SocialAuthController.class)
@Import(SecurityConfig.class)
class SocialAuthControllerTest {

    private static final String BASE = "/api/v1/auth/social";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SocialAuthService socialAuthService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private String codeBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grantType", "AUTHORIZATION_CODE");
        body.put("code", "auth-code");
        body.put("redirectUri", "http://localhost:5173/oauth/callback/kakao");
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void login_whenLoggedIn_returns200WithTokenEnvelope() throws Exception {
        when(socialAuthService.login(eq("kakao"), any()))
                .thenReturn(SocialLoginResponse.loggedIn(TokenResponse.of("access", "refresh"), true));

        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.data.newUser").value(true))
                .andExpect(jsonPath("$.data.token.accessToken").value("access"))
                .andExpect(jsonPath("$.data.token.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.data.token.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.link").isEmpty());
    }

    @Test
    void login_whenLinkRequired_returns200WithLinkEnvelope() throws Exception {
        when(socialAuthService.login(eq("kakao"), any())).thenReturn(SocialLoginResponse.linkRequired(
                new SocialLoginResponse.LinkInfo("link-token", SocialProvider.KAKAO, "le***@gmail.com", 600)));

        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("LINK_REQUIRED"))
                .andExpect(jsonPath("$.data.newUser").value(false))
                .andExpect(jsonPath("$.data.token").isEmpty())
                .andExpect(jsonPath("$.data.link.linkToken").value("link-token"))
                .andExpect(jsonPath("$.data.link.provider").value("KAKAO"))
                .andExpect(jsonPath("$.data.link.maskedEmail").value("le***@gmail.com"))
                .andExpect(jsonPath("$.data.link.expiresInSeconds").value(600));
    }

    @Test
    void login_whenGrantTypeMissing_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\",\"redirectUri\":\"http://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("grantType은 필수입니다."));
        verify(socialAuthService, never()).login(any(), any());
    }

    @Test
    void login_whenGrantTypeUnknown_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"PASSWORD\",\"code\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("요청 본문 형식이 올바르지 않습니다."));
        verify(socialAuthService, never()).login(any(), any());
    }

    @Test
    void login_whenRequiredFieldForGrantTypeMissing_returns400() throws Exception {
        // toCredential 은 서비스 안에서 호출되므로 서비스가 던지는 400 을 그대로 전달하는지 확인
        when(socialAuthService.login(eq("kakao"), any())).thenThrow(BusinessException.badRequest("code은(는) 필수입니다."));

        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"AUTHORIZATION_CODE\",\"redirectUri\":\"http://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("code은(는) 필수입니다."));
    }

    @ParameterizedTest
    @CsvSource({
            "code, 2049, code가 너무 깁니다.",
            "redirectUri, 2049, redirectUri가 너무 깁니다.",
            "codeVerifier, 129, codeVerifier가 너무 깁니다.",
            "nonce, 513, nonce가 너무 깁니다.",
            "state, 513, state가 너무 깁니다.",
            "accessToken, 8193, accessToken이 너무 깁니다.",
            "idToken, 8193, idToken이 너무 깁니다."
    })
    void login_whenFieldExceedsMaxSize_returns400(String field, int length, String message) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grantType", "AUTHORIZATION_CODE");
        body.put(field, "a".repeat(length));

        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(message));
        verify(socialAuthService, never()).login(any(), any());
    }

    @Test
    void login_whenFieldAtMaxSize_passesValidation() throws Exception {
        when(socialAuthService.login(eq("kakao"), any()))
                .thenReturn(SocialLoginResponse.loggedIn(TokenResponse.of("a", "r"), false));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grantType", "AUTHORIZATION_CODE");
        body.put("code", "a".repeat(2048));
        body.put("redirectUri", "http://x");

        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void login_whenUnsupportedProvider_returns400() throws Exception {
        when(socialAuthService.login(eq("apple"), any()))
                .thenThrow(BusinessException.badRequest("지원하지 않는 소셜 로그인 제공자입니다."));

        mockMvc.perform(post(BASE + "/apple/login").contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("지원하지 않는 소셜 로그인 제공자입니다."));
    }

    @Test
    void login_whenConflict_returns409() throws Exception {
        when(socialAuthService.login(eq("kakao"), any()))
                .thenThrow(BusinessException.conflict("이미 네이버 로그인으로 가입된 이메일입니다."));

        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 네이버 로그인으로 가입된 이메일입니다."));
    }

    @ParameterizedTest
    @CsvSource({
            "INVALID_CREDENTIAL, 401, 소셜 로그인 인증에 실패했습니다. 다시 시도해 주세요.",
            "INVALID_REQUEST, 400, 지원하지 않는 소셜 로그인 요청입니다.",
            "PROVIDER_UNAVAILABLE, 502, 소셜 로그인 제공자와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.",
            "SERVICE_UNAVAILABLE, 503, 소셜 로그인을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
    })
    void login_whenSocialAuthException_mapsReasonToStatusWithFixedMessage(
            SocialAuthException.Reason reason, int status, String message) throws Exception {
        when(socialAuthService.login(eq("kakao"), any())).thenThrow(new SocialAuthException(reason,
                "KAKAO token rejected: secret-provider-body", new RuntimeException("raw")));

        mockMvc.perform(post(BASE + "/kakao/login").contentType(MediaType.APPLICATION_JSON).content(codeBody()))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(message))
                .andExpect(content().string(not(containsString("secret-provider-body"))));
    }

    @Test
    void link_whenSuccess_returns200WithTokens() throws Exception {
        when(socialAuthService.link(new SocialLinkRequest("link-token", "pw")))
                .thenReturn(TokenResponse.of("access", "refresh"));

        mockMvc.perform(post(BASE + "/link").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialLinkRequest("link-token", "pw"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void link_whenWrongPassword_returns401() throws Exception {
        when(socialAuthService.link(any())).thenThrow(BusinessException.unauthorized("비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post(BASE + "/link").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialLinkRequest("link-token", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("비밀번호가 올바르지 않습니다."));
    }

    @Test
    void link_whenStoreUnavailable_returns503() throws Exception {
        when(socialAuthService.link(any())).thenThrow(
                new SocialAuthException(SocialAuthException.Reason.SERVICE_UNAVAILABLE, "redis down"));

        mockMvc.perform(post(BASE + "/link").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialLinkRequest("link-token", "pw"))))
                .andExpect(status().isServiceUnavailable());
    }

    @ParameterizedTest
    @CsvSource({
            "129, 1, linkToken이 너무 깁니다.",
            "1, 129, 비밀번호가 너무 깁니다."
    })
    void link_whenFieldExceedsMaxSize_returns400WithoutCallingService(int tokenLength, int passwordLength,
                                                                      String message) throws Exception {
        SocialLinkRequest request = new SocialLinkRequest("a".repeat(tokenLength), "b".repeat(passwordLength));

        mockMvc.perform(post(BASE + "/link").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(message));
        verify(socialAuthService, never()).link(any());
    }

    @Test
    void link_whenFieldsAtMaxSize_callsService() throws Exception {
        SocialLinkRequest request = new SocialLinkRequest("a".repeat(128), "b".repeat(128));
        when(socialAuthService.link(request)).thenReturn(TokenResponse.of("access", "refresh"));

        mockMvc.perform(post(BASE + "/link").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, pw, linkToken은 필수입니다.",
            "' ', pw, linkToken은 필수입니다.",
            "tok, NULL, 비밀번호는 필수입니다.",
            "tok, '', 비밀번호는 필수입니다."
    }, nullValues = "NULL")
    void link_whenRequiredFieldMissing_returns400WithoutCallingService(String linkToken, String password,
                                                                       String message) throws Exception {
        mockMvc.perform(post(BASE + "/link").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialLinkRequest(linkToken, password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(message));
        verify(socialAuthService, never()).link(any());
    }
}
