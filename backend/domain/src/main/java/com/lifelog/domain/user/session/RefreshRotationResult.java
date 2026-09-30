package com.lifelog.domain.user.session;

/** {@link RefreshSessionStore#rotate} 결과 */
public enum RefreshRotationResult {
    /** 제시한 jti 가 현재 jti 와 일치 → 새 jti 로 교체됨 */
    ROTATED,
    /**
     * 직전 jti 를 회전 후 겹침 구간(overlapWindow) 이내에 다시 제시(탭 동시 refresh, 응답 유실 재시도 등)
     * → 회전하지 않고 저장소의 현재 jti 를 그대로 돌려준다. rotatedAt 은 바뀌지 않고 세션 TTL 은 늘리기만 한다
     */
    REISSUED,
    /** 직전 jti 를 겹침 구간이 지난 뒤 제시 → 해당 세션만 삭제(tombstone 없음) */
    PREVIOUS_AFTER_GRACE,
    /** 현재도 직전도 아닌 jti(두 세대 이상 전) 제시 → 사용자 전체 세션 삭제 + 세션별 tombstone 기록 */
    REUSE_DETECTED,
    /** 세션은 없고 재사용 탐지로 폐기된 기록(tombstone)이 남아 있음 */
    REVOKED,
    /** 세션 없음(만료, 로그아웃, 존재한 적 없음) 또는 잘못된 입력 */
    NOT_FOUND
}
