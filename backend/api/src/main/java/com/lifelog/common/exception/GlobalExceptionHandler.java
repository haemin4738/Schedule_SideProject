package com.lifelog.common.exception;

import com.lifelog.common.dto.ApiResponse;
import com.lifelog.domain.user.session.AuthSessionUnavailableException;
import com.lifelog.domain.user.social.SocialAuthException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage(), e.getCode()));
    }

    @ExceptionHandler(AuthSessionUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthSessionUnavailable(AuthSessionUnavailableException e) {
        // 저장소 예외 메시지에 세션 키가 담길 수 있으므로 예외 타입만 기록하고, 응답은 고정 문구로 한다
        String causeType = e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none";
        log.error("Auth session store unavailable: cause={}", causeType);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("인증 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(SocialAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocialAuth(SocialAuthException e) {
        // 예외 메시지에 제공자 응답이 담길 수 있으므로 reason 과 예외 타입만 기록하고, 응답은 고정 문구로 한다
        SocialAuthException.Reason reason = e.getReason();
        String causeType = e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none";
        log.warn("Social auth failed: reason={}, cause={}", reason, causeType);
        return switch (reason) {
            case INVALID_CREDENTIAL -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("소셜 로그인 인증에 실패했습니다. 다시 시도해 주세요."));
            case INVALID_REQUEST -> ResponseEntity.badRequest()
                    .body(ApiResponse.error("지원하지 않는 소셜 로그인 요청입니다."));
            case PROVIDER_UNAVAILABLE -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("소셜 로그인 제공자와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요."));
            case SERVICE_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("소셜 로그인을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."));
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("유효성 검사 실패");
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("유효성 검사 실패");
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "잘못된 요청 파라미터입니다: " + e.getName();
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        // 본문 값이 메시지에 포함될 수 있으므로 예외 메시지는 로그/응답에 노출하지 않는다.
        log.warn("Unreadable request body: {}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ApiResponse.error("요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = "필수 요청 파라미터가 누락되었습니다: " + e.getParameterName();
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        // SQL/바인딩 값이 포함될 수 있으므로 원인 예외 타입만 기록한다.
        Throwable cause = e.getMostSpecificCause();
        log.warn("Data integrity violation: {}", cause.getClass().getSimpleName());
        // SQLState 22xxx(data exception: 길이 초과, 범위 밖 날짜 등)는 제약 충돌이 아니라 잘못된 입력이다
        if (cause instanceof SQLException sqlException
                && sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("22")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("요청 값이 허용 범위를 벗어났습니다."));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("데이터 제약 조건과 충돌하는 요청입니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(e.getHeaders())
                .body(ApiResponse.error("지원하지 않는 HTTP 메서드입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError().body(ApiResponse.error("서버 오류가 발생했습니다."));
    }
}
