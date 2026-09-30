package com.lifelog.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    /** 선택. 클라이언트 분기가 필요한 경우에만 지정 */
    private final ErrorCode code;

    public BusinessException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public BusinessException(String message, HttpStatus status, ErrorCode code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(message, HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException unauthorized(String message, ErrorCode code) {
        return new BusinessException(message, HttpStatus.UNAUTHORIZED, code);
    }

    public static BusinessException badGateway(String message) {
        return new BusinessException(message, HttpStatus.BAD_GATEWAY);
    }

    public static BusinessException serviceUnavailable(String message) {
        return new BusinessException(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
