package com.lifelog.auth;

import com.lifelog.auth.dto.*;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw BusinessException.conflict("이미 사용중인 이메일입니다.");
        }
        User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.name());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 소셜 전용 회원(비밀번호 없음)도 같은 메시지로 거부해 가입 경로를 노출하지 않는다
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw BusinessException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return TokenResponse.of(
                tokenProvider.createAccessToken(user.getId()),
                tokenProvider.createRefreshToken(user.getId())
        );
    }

    public TokenResponse refresh(RefreshRequest request) {
        Long userId = tokenProvider.resolveRefreshUserId(request.refreshToken())
                .orElseThrow(() -> {
                    log.warn("refresh 토큰 재발급 거부: 유효하지 않거나 refresh 타입이 아닌 토큰");
                    return BusinessException.unauthorized("유효하지 않은 refresh 토큰입니다.");
                });
        return TokenResponse.of(
                tokenProvider.createAccessToken(userId),
                tokenProvider.createRefreshToken(userId)
        );
    }
}
