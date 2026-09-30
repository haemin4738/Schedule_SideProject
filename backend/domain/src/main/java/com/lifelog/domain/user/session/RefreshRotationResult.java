package com.lifelog.domain.user.session;

/** {@link RefreshSessionStore#rotate} 결과 */
public enum RefreshRotationResult {
    /** 제시한 jti 가 현재 jti 와 일치 → 새 jti 로 교체됨 */
    ROTATED,
    /** 직전 jti 를 유예 시간 내에 다시 제시(탭 동시 refresh 등) → 교체·폐기 없음 */
    STALE_CONCURRENT,
    /** 현재/직전(유예 내) 어느 쪽도 아닌 jti 제시 → 사용자 전체 세션 삭제 + 세션별 tombstone 기록 */
    REUSE_DETECTED,
    /** 세션은 없고 재사용 탐지로 폐기된 기록(tombstone)이 남아 있음 */
    REVOKED,
    /** 세션 없음(만료, 로그아웃, 존재한 적 없음) 또는 잘못된 입력 */
    NOT_FOUND
}
