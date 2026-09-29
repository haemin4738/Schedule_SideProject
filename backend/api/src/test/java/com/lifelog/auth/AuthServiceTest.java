package com.lifelog.auth;

import com.lifelog.auth.dto.LoginRequest;
import com.lifelog.auth.dto.RefreshRequest;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void login_whenPasswordOver72Bytes_throwsUnauthorizedInsteadOfServerError() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
        User user = User.create("user@test.com", passwordEncoder.encode("correct-password"), "사용자");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        // BCrypt는 72바이트 초과 입력을 encode 시 거부하지만 matches는 false를 반환해야 한다
        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "a".repeat(100))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_whenSocialOnlyUser_throwsSameUnauthorizedWithoutMatching() {
        PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
        AuthService authService = new AuthService(userRepository, mockEncoder, tokenProvider);
        User socialOnly = User.createSocial("social@test.com", "소셜", SocialProvider.KAKAO);
        when(userRepository.findByEmail("social@test.com")).thenReturn(Optional.of(socialOnly));

        assertThatThrownBy(() -> authService.login(new LoginRequest("social@test.com", "anything")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(mockEncoder, never()).matches(any(), any());
        verify(tokenProvider, never()).createAccessToken(any());
    }

    @Test
    void login_whenUnknownEmail_throwsSameUnauthorizedMessage() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@test.com", "pw")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void login_whenLocalUserWithCorrectPassword_issuesTokens() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
        User user = User.create("user@test.com", passwordEncoder.encode("correct-password"), "사용자");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(tokenProvider.createAccessToken(1L)).thenReturn("access");
        when(tokenProvider.createRefreshToken(1L)).thenReturn("refresh");

        TokenResponse response = authService.login(new LoginRequest("user@test.com", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void refresh_withValidRefreshToken_issuesNewTokens() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
        when(tokenProvider.resolveRefreshUserId("valid.refresh.token")).thenReturn(Optional.of(1L));
        when(tokenProvider.createAccessToken(1L)).thenReturn("new.access.token");
        when(tokenProvider.createRefreshToken(1L)).thenReturn("new.refresh.token");

        TokenResponse response = authService.refresh(new RefreshRequest("valid.refresh.token"));

        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.refreshToken()).isEqualTo("new.refresh.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void refresh_withInvalidToken_throwsUnauthorized() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
        when(tokenProvider.resolveRefreshUserId("forged.token.value")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("forged.token.value")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(tokenProvider, never()).createAccessToken(any());
    }

    @Test
    void refresh_withRealAccessToken_throwsUnauthorized() {
        // 실제 provider로 access 토큰이 refresh에 쓰일 수 없음을 확인
        JwtTokenProvider realProvider = realProvider();
        AuthService authService = new AuthService(userRepository, passwordEncoder, realProvider);
        String accessToken = realProvider.createAccessToken(1L);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(accessToken)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_withRealRefreshToken_issuesTypedTokens() {
        JwtTokenProvider realProvider = realProvider();
        AuthService authService = new AuthService(userRepository, passwordEncoder, realProvider);

        TokenResponse response = authService.refresh(new RefreshRequest(realProvider.createRefreshToken(1L)));

        assertThat(realProvider.resolveAccessUserId(response.accessToken())).contains(1L);
        assertThat(realProvider.resolveRefreshUserId(response.refreshToken())).contains(1L);
    }

    private JwtTokenProvider realProvider() {
        JwtTokenProvider realProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(realProvider, "secret", "test-secret-key-for-auth-service-refresh-0123456789");
        ReflectionTestUtils.setField(realProvider, "accessExpiration", 3_600_000L);
        ReflectionTestUtils.setField(realProvider, "refreshExpiration", 3_600_000L);
        ReflectionTestUtils.invokeMethod(realProvider, "init");
        return realProvider;
    }
}
