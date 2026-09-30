package com.lifelog.auth;

import com.lifelog.auth.dto.SocialLinkRequest;
import com.lifelog.auth.dto.SocialLoginRequest;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.auth.dto.TokenResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.user.SignupProvider;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.domain.user.social.PendingSocialLink;
import com.lifelog.domain.user.social.PendingSocialLinkStore;
import com.lifelog.domain.user.social.SocialAccount;
import com.lifelog.domain.user.social.SocialAccountRepository;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialIdentityVerifier;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * 소셜 로그인 판정 (설계 4.1).
 * 외부 제공자 호출(verifier)이 DB 트랜잭션을 붙잡지 않도록 이 클래스에는 @Transactional 을 두지 않고,
 * 쓰기는 {@link SocialAccountRegistrar} 에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    static final Duration LINK_TTL = Duration.ofMinutes(10);
    static final int MAX_LINK_ATTEMPTS = 5;

    static final String EMAIL_REQUIRED_MESSAGE = "소셜 계정의 이메일 제공 동의(인증된 이메일)가 필요합니다.";
    static final String PROVIDER_ALREADY_LINKED_MESSAGE = "이미 같은 제공자의 다른 소셜 계정이 연결된 회원입니다.";
    static final String LINK_EXPIRED_MESSAGE = "연결 요청이 만료되었습니다. 소셜 로그인을 다시 시도해 주세요.";
    static final String LINK_WRONG_PASSWORD_MESSAGE = "비밀번호가 올바르지 않습니다.";
    static final String LINK_TOO_MANY_ATTEMPTS_MESSAGE = "비밀번호 입력 횟수를 초과했습니다. 소셜 로그인을 다시 시도해 주세요.";

    private final SocialIdentityVerifier verifier;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final PendingSocialLinkStore pendingSocialLinkStore;
    private final SocialAccountRegistrar registrar;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    public SocialLoginResponse login(String providerName, SocialLoginRequest request) {
        SocialProvider provider = parseProvider(providerName);
        SocialCredential credential = request.toCredential();

        // 1. 제공자 검증 — 트랜잭션 밖
        SocialUserInfo info = verifier.verify(provider, credential);

        // 2. 이미 연결된 소셜 계정
        Optional<SocialAccount> linked =
                socialAccountRepository.findByProviderAndProviderUserId(info.provider(), info.providerUserId());
        if (linked.isPresent()) {
            Long userId = linked.get().getUser().getId();
            log.info("소셜 로그인: userId={}, provider={}, status={}", userId, provider, SocialLoginResponse.Status.LOGGED_IN);
            return SocialLoginResponse.loggedIn(issueTokens(userId), false);
        }

        // 3. 인증된 이메일 필수
        if (info.email() == null || info.email().isBlank() || !info.emailVerified()) {
            log.warn("소셜 로그인 거부: provider={}, 인증된 이메일 없음", provider);
            throw BusinessException.badRequest(EMAIL_REQUIRED_MESSAGE);
        }

        // 4. 이메일로 기존 회원 조회
        Optional<User> existing = userRepository.findByEmail(info.email());

        // 4a. 신규 가입
        if (existing.isEmpty()) {
            User user = registrar.registerNewUser(info);
            log.info("소셜 로그인(신규 가입): userId={}, provider={}, status={}",
                    user.getId(), provider, SocialLoginResponse.Status.LOGGED_IN);
            return SocialLoginResponse.loggedIn(issueTokens(user.getId()), true);
        }

        User user = existing.get();

        // 4c. 다른 소셜로 가입한 소셜 전용 회원 — 확인할 비밀번호가 없으므로 자동 통합하지 않는다
        if (!user.hasPassword()) {
            log.warn("소셜 로그인 거부: userId={}, provider={}, 다른 소셜로 가입된 이메일", user.getId(), provider);
            throw BusinessException.conflict(
                    "이미 " + displayName(user.getSignupProvider()) + " 로그인으로 가입된 이메일입니다.");
        }

        // 4b. 일반 회원 — 같은 제공자의 다른 계정이 이미 연결돼 있으면 409, 아니면 비밀번호 확인 요청
        if (socialAccountRepository.existsByUserIdAndProvider(user.getId(), provider)) {
            log.warn("소셜 로그인 거부: userId={}, provider={}, 같은 제공자 계정이 이미 연결됨", user.getId(), provider);
            throw BusinessException.conflict(PROVIDER_ALREADY_LINKED_MESSAGE);
        }

        String linkToken = pendingSocialLinkStore.issue(
                new PendingSocialLink(user.getId(), provider, info.providerUserId()), LINK_TTL);
        log.info("소셜 로그인: userId={}, provider={}, status={}",
                user.getId(), provider, SocialLoginResponse.Status.LINK_REQUIRED);
        return SocialLoginResponse.linkRequired(new SocialLoginResponse.LinkInfo(
                linkToken, provider, maskEmail(user.getEmail()), LINK_TTL.toSeconds()));
    }

    public TokenResponse link(SocialLinkRequest request) {
        String linkToken = request.linkToken();
        PendingSocialLink pending = pendingSocialLinkStore.find(linkToken)
                .orElseThrow(() -> BusinessException.unauthorized(LINK_EXPIRED_MESSAGE));

        User user = userRepository.findById(pending.userId()).orElse(null);
        if (user == null || !user.hasPassword()) {
            pendingSocialLinkStore.consume(linkToken);
            throw BusinessException.unauthorized(LINK_EXPIRED_MESSAGE);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int attempts = pendingSocialLinkStore.incrementAttempts(linkToken);
            if (attempts == 0) {
                // 그 사이 만료/사용됨
                throw BusinessException.unauthorized(LINK_EXPIRED_MESSAGE);
            }
            if (attempts >= MAX_LINK_ATTEMPTS) {
                pendingSocialLinkStore.consume(linkToken);
                log.warn("소셜 계정 연결 폐기: userId={}, provider={}, 비밀번호 시도 횟수 초과", user.getId(), pending.provider());
                throw BusinessException.unauthorized(LINK_TOO_MANY_ATTEMPTS_MESSAGE);
            }
            log.warn("소셜 계정 연결 비밀번호 불일치: userId={}, provider={}, attempts={}",
                    user.getId(), pending.provider(), attempts);
            throw BusinessException.unauthorized(LINK_WRONG_PASSWORD_MESSAGE);
        }

        // 1회용 보장 — 동시 요청 중 하나만 통과
        if (!pendingSocialLinkStore.consume(linkToken)) {
            throw BusinessException.unauthorized(LINK_EXPIRED_MESSAGE);
        }

        User linkedUser = registrar.link(pending);
        log.info("소셜 계정 연결: userId={}, provider={}, status={}",
                linkedUser.getId(), pending.provider(), SocialLoginResponse.Status.LOGGED_IN);
        return issueTokens(linkedUser.getId());
    }

    static SocialProvider parseProvider(String providerName) {
        if (providerName != null) {
            try {
                return SocialProvider.valueOf(providerName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 아래에서 400
            }
        }
        throw BusinessException.badRequest("지원하지 않는 소셜 로그인 제공자입니다.");
    }

    /** 앞 2글자 + *** + @도메인. local-part 가 2글자 이하면 첫 글자만 노출 */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() > 2 ? local.substring(0, 2) : local.substring(0, 1);
        return visible + "***" + email.substring(at);
    }

    private static String displayName(SignupProvider provider) {
        return switch (provider) {
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
            case GOOGLE -> "구글";
            case LOCAL -> "이메일";
        };
    }

    private TokenResponse issueTokens(Long userId) {
        return authTokenService.issue(userId);
    }
}
