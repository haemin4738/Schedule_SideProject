package com.lifelog.domain.user.social;

import lombok.Getter;

/** 소셜 인증 외부 연동 실패. api 레이어에서 reason 별 HTTP 상태로 변환 */
@Getter
public class SocialAuthException extends RuntimeException {

    public enum Reason {
        /** 잘못되거나 만료된 code/token, 서명·audience 불일치 → 401 */
        INVALID_CREDENTIAL,
        /** 제공자별로 허용되지 않은 자격증명 유형, 허용 목록 밖 redirectUri → 400 */
        INVALID_REQUEST,
        /** 제공자 5xx/타임아웃 → 502 */
        PROVIDER_UNAVAILABLE,
        /** 제공자 설정값(client-id 등) 미설정, 연결 대기 저장소(Redis) 장애 → 503 */
        SERVICE_UNAVAILABLE
    }

    private final Reason reason;

    public SocialAuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public SocialAuthException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
