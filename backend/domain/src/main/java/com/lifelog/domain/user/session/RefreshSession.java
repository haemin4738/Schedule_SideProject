package com.lifelog.domain.user.session;

import java.time.Instant;

/**
 * refresh 세션 (기기/로그인 1건). 세션마다 현재 유효한 refresh 토큰 jti 1개만 저장한다(allowlist).
 *
 * @param userId    세션 소유 사용자
 * @param sessionId 세션 식별자(sid). refresh 회전 후에도 유지된다
 * @param tokenId   현재 유효한 refresh 토큰의 jti
 * @param authTime  최초 인증(로그인) 시각. 절대 수명 계산 기준
 */
public record RefreshSession(Long userId, String sessionId, String tokenId, Instant authTime) {
}
