package com.lifelog.auth;

import com.lifelog.auth.dto.LoginRequest;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
