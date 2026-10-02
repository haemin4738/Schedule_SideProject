package com.lifelog.domain.user.social;

import java.time.Duration;
import java.util.Optional;

/**
 * 앱 소셜 로그인(네이버) 1회용 state·ticket 저장소.
 * <ul>
 *     <li>state — 로그인 시작 시 발급, 앱 비밀의 해시(challenge)에 묶임. 콜백에서 1회 소비</li>
 *     <li>ticket — 서버가 code 교환을 마친 결과(사용자 정보)를 challenge 와 함께 보관. 앱이 비밀(verifier)과 함께 1회 소비</li>
 * </ul>
 * 원문 state·ticket 은 저장하지 않는다. 저장소 장애 시 {@link SocialAuthException} (SERVICE_UNAVAILABLE).
 */
public interface AppSocialLoginStore {

    /** 새 state 를 발급하고 원문을 반환 */
    String issueState(String codeChallenge, Duration ttl);

    /** state 를 원자적으로 소비하고 묶인 challenge 를 반환. 없거나 만료·이미 사용됐으면 empty */
    Optional<String> consumeState(String state);

    /** 새 ticket 을 발급하고 원문을 반환 */
    String issueTicket(AppLoginTicket ticket, Duration ttl);

    /** ticket 을 원자적으로 소비. 없거나 만료·이미 사용됐으면 empty */
    Optional<AppLoginTicket> consumeTicket(String ticket);
}
