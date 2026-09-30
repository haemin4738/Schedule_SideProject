package com.lifelog.auth;

import com.lifelog.auth.dto.LoginRequest;
import com.lifelog.auth.dto.LogoutRequest;
import com.lifelog.auth.dto.RefreshRequest;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.domain.user.social.SocialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

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
    private AuthTokenService tokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void login_whenPasswordOver72Bytes_throwsUnauthorizedInsteadOfServerError() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenService);
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
        AuthService authService = new AuthService(userRepository, mockEncoder, tokenService);
        User socialOnly = User.createSocial("social@test.com", "소셜", SocialProvider.KAKAO);
        when(userRepository.findByEmail("social@test.com")).thenReturn(Optional.of(socialOnly));

        assertThatThrownBy(() -> authService.login(new LoginRequest("social@test.com", "anything")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(mockEncoder, never()).matches(any(), any());
        verify(tokenService, never()).issue(any());
    }

    @Test
    void login_whenUnknownEmail_throwsSameUnauthorizedMessage() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenService);
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@test.com", "pw")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void login_whenLocalUserWithCorrectPassword_issuesTokens() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenService);
        User user = User.create("user@test.com", passwordEncoder.encode("correct-password"), "사용자");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(1L)).thenReturn(TokenResponse.of("access", "refresh"));

        TokenResponse response = authService.login(new LoginRequest("user@test.com", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void login_whenCalled_isNotWrappedInTransaction() throws NoSuchMethodException {
        // 세션 저장소(Redis) 호출이 포함되므로 DB 트랜잭션을 붙잡지 않는다
        assertThat(AuthService.class.getMethod("login", LoginRequest.class).isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    @Test
    void refresh_whenCalled_delegatesToTokenService() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenService);
        when(tokenService.rotate("valid.refresh.token"))
                .thenReturn(TokenResponse.of("new.access.token", "new.refresh.token"));

        TokenResponse response = authService.refresh(new RefreshRequest("valid.refresh.token"));

        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.refreshToken()).isEqualTo("new.refresh.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void refresh_whenTokenServiceRejects_propagatesUnauthorized() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenService);
        when(tokenService.rotate("forged.token.value"))
                .thenThrow(BusinessException.unauthorized("유효하지 않은 refresh 토큰입니다."));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("forged.token.value")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_whenCalled_delegatesToTokenService() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, tokenService);

        authService.logout(new LogoutRequest("refresh.token"));

        verify(tokenService).revoke("refresh.token");
    }
}
