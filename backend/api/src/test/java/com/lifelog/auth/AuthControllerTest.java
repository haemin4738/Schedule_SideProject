package com.lifelog.auth;

import com.lifelog.auth.dto.LoginRequest;
import com.lifelog.auth.dto.LogoutRequest;
import com.lifelog.auth.dto.RefreshRequest;
import com.lifelog.auth.dto.SignupRequest;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.auth.dto.UserResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.common.exception.ErrorCode;
import com.lifelog.domain.user.session.AuthSessionUnavailableException;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String BASE = "/api/v1/auth";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void signup_whenValidRequest_returns201() throws Exception {
        when(authService.signup(any())).thenReturn(
                new UserResponse(1L, "user@test.com", "사용자", LocalDateTime.of(2026, 9, 28, 10, 0)));

        mockMvc.perform(post(BASE + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("user@test.com", "Passw0rd!", "사용자"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@test.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef1!x", "Abcdefgh12345!@"})
    void signup_whenPasswordMeetsPolicyAtLengthBoundary_returns201(String password) throws Exception {
        when(authService.signup(any())).thenReturn(
                new UserResponse(1L, "user@test.com", "사용자", LocalDateTime.of(2026, 9, 28, 10, 0)));

        mockMvc.perform(post(BASE + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("user@test.com", password, "사용자"))))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Abcde1!x",          // 8자 (최소 미만)
            "Abcdefgh12345!@#",  // 16자 (최대 초과)
            "Abcdefgh1",         // 특수문자 없음
            "Abcdefgh!",         // 숫자 없음
            "12345678!",         // 영문 없음
            "Abcdef1! x",        // 공백 포함
            "비밀번호Abc12!"      // 영문/숫자/특수문자 외 문자 포함
    })
    void signup_whenPasswordViolatesPolicy_returns400WithoutCallingService(String password) throws Exception {
        mockMvc.perform(post(BASE + "/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest("user@test.com", password, "사용자"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함한 9~15자여야 합니다."));
        verify(authService, never()).signup(any());
    }

    @Test
    void login_whenLegacyPasswordNotMatchingSignupPolicy_passesValidationToService() throws Exception {
        when(authService.login(any())).thenReturn(TokenResponse.of("access", "refresh"));

        // 가입 정책(9~15자, 영문/숫자/특수문자)은 가입에만 적용 — 이전 규칙(8자 이상)으로 가입한 계정도 로그인 가능해야 한다
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@test.com", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    void login_whenInvalidCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(BusinessException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@test.com", "a".repeat(100)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void logout_whenValidBody_returns200WithNullDataAndNoCode() throws Exception {
        mockMvc.perform(post(BASE + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest("refresh.token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // 성공 응답의 data:null 은 유지되고, 선택 필드 code 는 생략된다
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(content().string(not(containsString("\"code\""))));
        verify(authService).logout(new LogoutRequest("refresh.token"));
    }

    @Test
    void logout_whenRefreshTokenBlank_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(post(BASE + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verify(authService, never()).logout(any());
    }

    @Test
    void logout_whenNoAuthorizationHeader_isPermitted() throws Exception {
        // 인증 불필요 — access 토큰이 만료된 상태에서도 로그아웃할 수 있어야 한다
        mockMvc.perform(post(BASE + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh.token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_whenSessionRevoked_returns401WithCode() throws Exception {
        when(authService.refresh(any())).thenThrow(
                BusinessException.unauthorized("보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해 주세요.",
                        ErrorCode.SESSION_REVOKED));

        mockMvc.perform(post(BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("refresh.token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void refresh_whenConcurrentRotation_returns409WithCode() throws Exception {
        when(authService.refresh(any())).thenThrow(
                BusinessException.conflict("다른 요청에서 토큰을 갱신하는 중입니다.", ErrorCode.REFRESH_IN_PROGRESS));

        mockMvc.perform(post(BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("refresh.token"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFRESH_IN_PROGRESS"));
    }

    @Test
    void login_whenInvalidCredentials_omitsCodeField() throws Exception {
        when(authService.login(any())).thenThrow(BusinessException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다."));

        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@test.com", "password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void logout_whenSessionStoreUnavailable_returns503WithFixedMessage() throws Exception {
        doThrow(new AuthSessionUnavailableException("store down: auth:refresh:{u:1}:session:x", new RuntimeException()))
                .when(authService).logout(any());

        mockMvc.perform(post(BASE + "/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest("refresh.token"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("인증 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}
