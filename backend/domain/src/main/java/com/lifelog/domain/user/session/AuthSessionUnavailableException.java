package com.lifelog.domain.user.session;

/** refresh 세션 저장소(Redis) 장애. api 레이어에서 503 으로 변환 */
public class AuthSessionUnavailableException extends RuntimeException {

    public AuthSessionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
