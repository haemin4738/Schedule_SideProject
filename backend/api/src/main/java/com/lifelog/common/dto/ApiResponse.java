package com.lifelog.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.common.exception.ErrorCode;

/**
 * 공통 응답 envelope.
 * {@code code} 는 선택 필드 — null 이면 JSON 에서 생략된다(필드 단위 NON_NULL).
 * 나머지 필드(data/error)는 null 이어도 그대로 직렬화해 기존 계약을 유지한다.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        @JsonInclude(JsonInclude.Include.NON_NULL) ErrorCode code
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> error(String message, ErrorCode code) {
        return new ApiResponse<>(false, null, message, code);
    }
}
