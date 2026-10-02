package com.lifelog.infrastructure.oauth;

import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialCredential.AccessToken;
import com.lifelog.domain.user.social.SocialCredential.AuthorizationCode;
import com.lifelog.domain.user.social.SocialCredential.IdToken;
import com.lifelog.domain.user.social.SocialCredential.ServerCallbackCode;
import com.lifelog.domain.user.social.SocialIdentityVerifier;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 제공자 × 자격증명 유형 라우팅 (설계 2.2).
 * KAKAO = code | access token, NAVER = code | 서버 콜백 code, GOOGLE = code | id_token. 그 외 조합은 INVALID_REQUEST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialIdentityVerifierImpl implements SocialIdentityVerifier {

    private final KakaoIdentityClient kakao;
    private final NaverIdentityClient naver;
    private final GoogleIdentityClient google;

    @Override
    public SocialUserInfo verify(SocialProvider provider, SocialCredential credential) {
        if (provider == null || credential == null) {
            throw ProviderCalls.invalidRequest("provider and credential are required");
        }
        String grantType = credential.getClass().getSimpleName();
        try {
            SocialUserInfo info = route(provider, credential);
            log.info("소셜 자격증명 검증 성공: provider={}, grantType={}", provider, grantType);
            return info;
        } catch (SocialAuthException e) {
            // 메시지에는 토큰·code·이메일을 넣지 않는다 (ProviderCalls 참고)
            log.warn("소셜 자격증명 검증 실패: provider={}, grantType={}, reason={}, cause={}",
                    provider, grantType, e.getReason(),
                    e.getCause() != null ? e.getCause().getClass().getSimpleName() : "-");
            throw e;
        }
    }

    private SocialUserInfo route(SocialProvider provider, SocialCredential credential) {
        return switch (provider) {
            case KAKAO -> switch (credential) {
                case AuthorizationCode code -> kakao.verify(code);
                case AccessToken token -> kakao.verify(token);
                case IdToken ignored -> throw unsupported(provider, credential);
                case ServerCallbackCode ignored -> throw unsupported(provider, credential);
            };
            case NAVER -> switch (credential) {
                case AuthorizationCode code -> naver.verify(code);
                case ServerCallbackCode code -> naver.verify(code);
                case AccessToken ignored -> throw unsupported(provider, credential);
                case IdToken ignored -> throw unsupported(provider, credential);
            };
            case GOOGLE -> switch (credential) {
                case AuthorizationCode code -> google.verify(code);
                case IdToken token -> google.verify(token);
                case AccessToken ignored -> throw unsupported(provider, credential);
                case ServerCallbackCode ignored -> throw unsupported(provider, credential);
            };
        };
    }

    private static SocialAuthException unsupported(SocialProvider provider, SocialCredential credential) {
        return ProviderCalls.invalidRequest(provider + " does not accept " + credential.getClass().getSimpleName());
    }
}
