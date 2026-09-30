package com.lifelog.common.exception;

/**
 * 클라이언트가 분기해야 하는 오류 식별자. 응답 envelope 의 선택 필드 {@code code} 로 노출된다.
 * 클라이언트는 error 문구가 아니라 이 값으로만 분기한다.
 */
public enum ErrorCode {
    /** refresh 토큰이 유효하지 않음(서명/만료/형식/레거시/세션 없음) — 조용히 로그인 화면으로 */
    INVALID_REFRESH_TOKEN,
    /** 보안상 세션이 폐기됨(재사용 탐지) — 사용자에게 알림 후 로그인 화면으로 */
    SESSION_REVOKED
}
