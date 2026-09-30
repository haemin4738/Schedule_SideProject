package com.lifelog.domain.user.session;

import java.time.Duration;
import java.time.Instant;

/**
 * refresh 세션 저장소 (세션별 현재 jti allowlist + 회전 + 재사용 탐지).
 * 모든 연산은 원자적이다. 저장소 장애 시 {@link AuthSessionUnavailableException}.
 */
public interface RefreshSessionStore {

    /**
     * 새 세션을 저장하고 사용자 세션 인덱스에 등록한다. 만료된 인덱스 멤버는 이때 정리된다.
     *
     * @param session 모든 필드 필수 (sessionId/tokenId 는 공백 불가)
     * @param ttl     세션 TTL (양수)
     * @throws IllegalArgumentException 필드 누락/공백 또는 ttl 이 null·0 이하
     */
    void create(RefreshSession session, Duration ttl);

    /**
     * 제시한 refresh 토큰 jti 로 세션을 회전한다.
     * <ul>
     *     <li>세션 없음: tombstone 있으면 {@link RefreshRotationResult#REVOKED}, 없으면 {@link RefreshRotationResult#NOT_FOUND}</li>
     *     <li>현재 jti 일치: newTokenId 로 교체, TTL 을 ttl 로 재설정 → {@link RefreshRotationResult#ROTATED} (tokenId = newTokenId)</li>
     *     <li>직전 jti 이고 회전 후 overlapWindow 이내: 회전하지 않음, rotatedAt 불변, 세션 TTL 이 ttl 보다 짧을 때만 연장
     *         → {@link RefreshRotationResult#REISSUED} (tokenId = 저장소의 현재 jti)</li>
     *     <li>직전 jti 이고 overlapWindow 경과: 해당 세션만 삭제(tombstone 없음) → {@link RefreshRotationResult#PREVIOUS_AFTER_GRACE}</li>
     *     <li>그 외(두 세대 이상 전 jti): 해당 사용자의 모든 세션 삭제 + 세션별 tombstone(남은 TTL 동안)
     *         → {@link RefreshRotationResult#REUSE_DETECTED}</li>
     * </ul>
     * ROTATED/REISSUED 외의 결과는 tokenId 가 null 이다.
     * userId 가 null 이거나 문자열 인자가 공백, ttl 이 null·0 이하이면 저장소를 조회하지 않고 NOT_FOUND.
     *
     * @param now           회전 시각 (rotatedAt 기록, 겹침 구간 판정 기준)
     * @param overlapWindow 회전 직후 직전 jti 로도 재발급을 허용하는 겹침 구간
     * @throws IllegalArgumentException now 가 null 이거나 overlapWindow 가 null·음수
     */
    RefreshRotationOutcome rotate(Long userId, String sessionId, String presentedTokenId, String newTokenId,
                                  Duration ttl, Instant now, Duration overlapWindow);

    /**
     * 세션을 삭제한다(로그아웃). tombstone 은 남기지 않는다. 멱등.
     *
     * @return 이번 호출로 세션이 삭제되었으면 true. 없는 세션·잘못된 입력이면 false
     */
    boolean revoke(Long userId, String sessionId);
}
