package com.lifelog.auth;

import com.lifelog.auth.dto.NaverAppStartResponse;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.common.exception.ErrorCode;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NaverAppLoginController.class)
@Import(SecurityConfig.class)
class NaverAppLoginControllerTest {

    private static final String BASE = "/api/v1/auth/social/naver";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NaverAppLoginService naverAppLoginService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // ---------- start ----------

    @Test
    void start_whenValidChallenge_returnsAuthorizeUrlEnvelopeWithoutAuth() throws Exception {
        when(naverAppLoginService.start(CHALLENGE))
                .thenReturn(new NaverAppStartResponse("https://nid.naver.com/oauth2.0/authorize?x", 600));

        mockMvc.perform(post(BASE + "/app/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeChallenge\":\"" + CHALLENGE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizeUrl").value("https://nid.naver.com/oauth2.0/authorize?x"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM=", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw+cM"})
    void start_whenChallengeMalformed_returns400WithoutCallingService(String challenge) throws Exception {
        mockMvc.perform(post(BASE + "/app/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeChallenge\":\"" + challenge + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verify(naverAppLoginService, never()).start(any());
    }

    // ---------- callback ----------

    @Test
    void callback_whenCalled_redirectsToServiceLocationWithSecurityHeaders() throws Exception {
        when(naverAppLoginService.callback("c", "s", null)).thenReturn(URI.create("lifelog://oauth/naver?ticket=t"));

        mockMvc.perform(get(BASE + "/app-callback").param("code", "c").param("state", "s"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "lifelog://oauth/naver?ticket=t"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void callback_whenErrorParam_passesItToService() throws Exception {
        when(naverAppLoginService.callback(null, "s", "access_denied"))
                .thenReturn(URI.create("lifelog://oauth/naver?error=ACCESS_DENIED"));

        mockMvc.perform(get(BASE + "/app-callback").param("state", "s").param("error", "access_denied")
                        .param("error_description", "x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "lifelog://oauth/naver?error=ACCESS_DENIED"));
    }

    // ---------- complete ----------

    @Test
    void complete_whenValid_returnsSocialLoginEnvelope() throws Exception {
        when(naverAppLoginService.complete("ticket-1", VERIFIER))
                .thenReturn(SocialLoginResponse.loggedIn(TokenResponse.of("access", "refresh"), true));

        mockMvc.perform(post(BASE + "/app/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"ticket-1\",\"codeVerifier\":\"" + VERIFIER + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.data.newUser").value(true))
                .andExpect(jsonPath("$.data.token.accessToken").value("access"));
    }

    @Test
    void complete_whenTicketInvalid_returns401WithCode() throws Exception {
        when(naverAppLoginService.complete("ticket-1", VERIFIER))
                .thenThrow(BusinessException.unauthorized(NaverAppLoginService.TICKET_INVALID_MESSAGE,
                        ErrorCode.SOCIAL_APP_TICKET_INVALID));

        mockMvc.perform(post(BASE + "/app/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"ticket-1\",\"codeVerifier\":\"" + VERIFIER + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SOCIAL_APP_TICKET_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ticket\":\"t\",\"codeVerifier\":\"too-short\"}",
            "{\"ticket\":\"t\",\"codeVerifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjX!\"}",
            "{\"ticket\":\"\",\"codeVerifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk\"}",
            "{\"codeVerifier\":\"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk\"}"
    })
    void complete_whenRequestMalformed_returns400WithoutCallingService(String body) throws Exception {
        mockMvc.perform(post(BASE + "/app/complete").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(naverAppLoginService, never()).complete(any(), any());
    }
}
