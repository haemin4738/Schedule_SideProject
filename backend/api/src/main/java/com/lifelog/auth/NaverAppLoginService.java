package com.lifelog.auth;

import com.lifelog.auth.dto.NaverAppStartResponse;
import com.lifelog.auth.dto.SocialLoginResponse;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.common.exception.ErrorCode;
import com.lifelog.domain.user.social.AppLoginTicket;
import com.lifelog.domain.user.social.AppSocialLoginStore;
import com.lifelog.domain.user.social.SocialAuthException;
import com.lifelog.domain.user.social.SocialCredential;
import com.lifelog.domain.user.social.SocialIdentityVerifier;
import com.lifelog.domain.user.social.SocialProvider;
import com.lifelog.domain.user.social.SocialUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * 네이버 앱(iOS) 로그인 — 서버가 code 를 교환하고 앱에는 앱 비밀에 묶인 1회용 ticket 만 넘긴다
 * (설계 .claude/flutter-social-login-design.md §2.3).
 * <ol>
 *     <li>start: 앱이 verifier 의 해시(challenge)를 보내면 state 를 발급해 authorize URL 을 돌려준다</li>
 *     <li>callback: 네이버 → 서버. state 1회 소비 → code 교환 → ticket 발급 → 앱 고정 대상으로 302</li>
 *     <li>complete: 앱이 ticket + verifier 를 보내면 해시를 대조하고 웹과 같은 판정을 한다</li>
 * </ol>
 * code 가 앱·URL 스킴으로 나가지 않으므로 다른 앱이 콜백을 가로채도 verifier 없이는 쓸 수 없다.
 * code·state·ticket·verifier 는 로그에 남기지 않는다.
 */
@Slf4j
@Service
public class NaverAppLoginService {

    static final Duration STATE_TTL = Duration.ofMinutes(10);
    static final Duration TICKET_TTL = Duration.ofMinutes(2);
    static final String AUTHORIZE_URI = "https://nid.naver.com/oauth2.0/authorize";
    static final String TICKET_INVALID_MESSAGE = "로그인 요청이 만료되었거나 올바르지 않습니다. 다시 시도해 주세요.";

    /** app-callback 이 앱에 넘기는 고정 오류 코드 — 제공자 원문은 전달하지 않는다 */
    enum CallbackError {
        /** 사용자 취소·네이버 error 응답 */
        ACCESS_DENIED,
        /** state 없음·만료·이미 사용됨 */
        INVALID_STATE,
        /** code 교환·프로필 실패, 저장소 장애 */
        PROVIDER_ERROR
    }

    private final AppSocialLoginStore store;
    private final SocialIdentityVerifier verifier;
    private final SocialAuthService socialAuthService;
    private final String clientId;
    private final String appRedirectUri;
    private final String callbackTarget;

    public NaverAppLoginService(AppSocialLoginStore store,
                                SocialIdentityVerifier verifier,
                                SocialAuthService socialAuthService,
                                @Value("${oauth.naver.client-id:}") String clientId,
                                @Value("${oauth.naver.app-redirect-uri:}") String appRedirectUri,
                                @Value("${oauth.naver.app-callback-target}") String callbackTarget) {
        this.store = store;
        this.verifier = verifier;
        this.socialAuthService = socialAuthService;
        this.clientId = clientId;
        this.appRedirectUri = appRedirectUri;
        this.callbackTarget = callbackTarget;
    }

    public NaverAppStartResponse start(String codeChallenge) {
        if (isBlank(clientId) || isBlank(appRedirectUri)) {
            throw new SocialAuthException(SocialAuthException.Reason.SERVICE_UNAVAILABLE, "NAVER app login not configured");
        }
        String state = store.issueState(codeChallenge, STATE_TTL);
        String authorizeUrl = UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("response_type", "code")
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("state", "{state}")
                .encode()
                .buildAndExpand(Map.of("clientId", clientId, "redirectUri", appRedirectUri, "state", state))
                .toUriString();
        return new NaverAppStartResponse(authorizeUrl, STATE_TTL.toSeconds());
    }

    /** 네이버 redirect 를 받아 앱 고정 대상(?ticket= 또는 ?error=) 위치를 만든다. 요청 값으로 대상이 바뀌지 않는다 */
    public URI callback(String code, String state, String error) {
        if (error != null) {
            discardState(state);
            log.warn("네이버 앱 로그인 콜백: result={}", CallbackError.ACCESS_DENIED);
            return redirect("error", CallbackError.ACCESS_DENIED.name());
        }
        try {
            Optional<String> challenge = store.consumeState(state);
            if (challenge.isEmpty()) {
                log.warn("네이버 앱 로그인 콜백: result={}", CallbackError.INVALID_STATE);
                return redirect("error", CallbackError.INVALID_STATE.name());
            }
            if (isBlank(code)) {
                log.warn("네이버 앱 로그인 콜백: result={}, code 없음", CallbackError.PROVIDER_ERROR);
                return redirect("error", CallbackError.PROVIDER_ERROR.name());
            }
            // 외부 호출 — 트랜잭션 없음
            SocialUserInfo info = verifier.verify(SocialProvider.NAVER, new SocialCredential.ServerCallbackCode(code, state));
            String ticket = store.issueTicket(new AppLoginTicket(challenge.get(), info), TICKET_TTL);
            log.info("네이버 앱 로그인 콜백: result=TICKET_ISSUED");
            return redirect("ticket", ticket);
        } catch (SocialAuthException e) {
            log.warn("네이버 앱 로그인 콜백: result={}, reason={}", CallbackError.PROVIDER_ERROR, e.getReason());
            return redirect("error", CallbackError.PROVIDER_ERROR.name());
        }
    }

    public SocialLoginResponse complete(String ticket, String codeVerifier) {
        AppLoginTicket stored = store.consumeTicket(ticket).orElseThrow(NaverAppLoginService::ticketInvalid);
        if (!matches(codeVerifier, stored.codeChallenge())) {
            log.warn("네이버 앱 로그인 완료 거부: verifier 불일치");
            throw ticketInvalid();
        }
        return socialAuthService.resolve(stored.userInfo());
    }

    /** BASE64URL(SHA-256(verifier)) 와 challenge 를 상수 시간 비교 */
    static boolean matches(String codeVerifier, String codeChallenge) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            byte[] expected = Base64.getUrlEncoder().withoutPadding().encodeToString(hash).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, codeChallenge.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void discardState(String state) {
        try {
            store.consumeState(state);
        } catch (SocialAuthException e) {
            // 정리 실패는 TTL 로 사라지므로 무시
            log.warn("네이버 앱 로그인 state 정리 실패: reason={}", e.getReason());
        }
    }

    private URI redirect(String name, String value) {
        // 값은 URI 변수로 확장해 예약 문자까지 모두 인코딩한다
        return UriComponentsBuilder.fromUriString(callbackTarget)
                .queryParam(name, "{value}")
                .encode()
                .buildAndExpand(Map.of("value", value))
                .toUri();
    }

    private static BusinessException ticketInvalid() {
        return BusinessException.unauthorized(TICKET_INVALID_MESSAGE, ErrorCode.SOCIAL_APP_TICKET_INVALID);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
