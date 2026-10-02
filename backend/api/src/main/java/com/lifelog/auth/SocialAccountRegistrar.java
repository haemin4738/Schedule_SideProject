package com.lifelog.auth;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.expense.ExpenseCategoryService;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.domain.user.social.PendingSocialLink;
import com.lifelog.domain.user.social.SocialAccount;
import com.lifelog.domain.user.social.SocialAccountRepository;
import com.lifelog.domain.user.social.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 가입/연결의 DB 쓰기 전담.
 * SocialAuthService 는 외부 제공자 호출을 트랜잭션 밖에서 하므로, 쓰기 트랜잭션은 이 별도 빈에서 연다 (self-invocation 회피).
 * 동시 요청으로 unique 제약 위반 시 DataIntegrityViolationException → GlobalExceptionHandler 에서 409.
 */
@Component
@RequiredArgsConstructor
public class SocialAccountRegistrar {

    static final String ALREADY_LINKED_MESSAGE = "이미 다른 회원에게 연결된 소셜 계정입니다.";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ExpenseCategoryService expenseCategoryService;

    /** 신규 소셜 회원 생성 + 소셜 계정 연결 + 가계부 기본 카테고리 (한 트랜잭션) */
    @Transactional
    public User registerNewUser(SocialUserInfo info) {
        User user = userRepository.save(User.createSocial(info.email(), resolveName(info), info.provider()));
        socialAccountRepository.save(SocialAccount.create(user, info.provider(), info.providerUserId()));
        expenseCategoryService.addDefaults(user);
        return user;
    }

    /** 비밀번호 확인을 마친 기존 회원에 소셜 계정 연결. 그 사이 다른 연결이 생겼으면 409 */
    @Transactional
    public User link(PendingSocialLink pending) {
        if (socialAccountRepository.findByProviderAndProviderUserId(pending.provider(), pending.providerUserId()).isPresent()) {
            throw BusinessException.conflict(ALREADY_LINKED_MESSAGE);
        }
        if (socialAccountRepository.existsByUserIdAndProvider(pending.userId(), pending.provider())) {
            throw BusinessException.conflict(SocialAuthService.PROVIDER_ALREADY_LINKED_MESSAGE);
        }
        User user = userRepository.findById(pending.userId())
                .orElseThrow(() -> BusinessException.unauthorized(SocialAuthService.LINK_EXPIRED_MESSAGE));
        socialAccountRepository.save(SocialAccount.create(user, pending.provider(), pending.providerUserId()));
        return user;
    }

    /** 제공자 이름이 없으면 이메일 local-part 사용 */
    static String resolveName(SocialUserInfo info) {
        if (info.name() != null && !info.name().isBlank()) {
            return info.name().strip();
        }
        String email = info.email();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
