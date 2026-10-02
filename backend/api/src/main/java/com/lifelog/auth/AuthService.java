package com.lifelog.auth;

import com.lifelog.auth.dto.*;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.expense.ExpenseCategoryService;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
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
    private final AuthTokenService authTokenService;
    private final ExpenseCategoryService expenseCategoryService;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw BusinessException.conflict("이미 사용중인 이메일입니다.");
        }
        User user = userRepository.save(User.create(request.email(), passwordEncoder.encode(request.password()), request.name()));
        // 가계부 기본 카테고리도 가입과 함께 만든다 (같은 트랜잭션)
        expenseCategoryService.addDefaults(user);
        return UserResponse.from(user);
    }

    // Redis(세션 저장소) 호출이 포함되므로 DB 트랜잭션을 걸지 않는다 — 조회는 리포지토리 기본 트랜잭션으로 충분
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> BusinessException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 소셜 전용 회원(비밀번호 없음)도 같은 메시지로 거부해 가입 경로를 노출하지 않는다
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw BusinessException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        TokenResponse tokens = authTokenService.issue(user.getId());
        log.info("로그인 성공: userId={}", user.getId());
        return tokens;
    }

    public TokenResponse refresh(RefreshRequest request) {
        return authTokenService.rotate(request.refreshToken());
    }

    public void logout(LogoutRequest request) {
        authTokenService.revoke(request.refreshToken());
    }
}
