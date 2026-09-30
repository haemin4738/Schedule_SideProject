package com.lifelog.common.exception;

import com.lifelog.domain.user.session.AuthSessionUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {

        @GetMapping("/test/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/missing", "test/missing");
        }

        @GetMapping("/test/method-not-supported")
        void methodNotSupported() throws HttpRequestMethodNotSupportedException {
            throw new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));
        }

        @GetMapping("/test/data-exception")
        void dataException() {
            throw new DataIntegrityViolationException("data too long", new SQLException("too long", "22001", 1406));
        }

        @GetMapping("/test/business-with-code")
        void businessWithCode() {
            throw BusinessException.unauthorized("세션 폐기", ErrorCode.SESSION_REVOKED);
        }

        @GetMapping("/test/business-without-code")
        void businessWithoutCode() {
            throw BusinessException.notFound("없음");
        }

        @GetMapping("/test/auth-session-unavailable")
        void authSessionUnavailable() {
            throw new AuthSessionUnavailableException("auth:refresh:{u:42}:session:secret-sid",
                    new QueryTimeoutException("auth:refresh:{u:42}:session:secret-sid"));
        }

        @GetMapping("/test/auth-session-unavailable-no-cause")
        void authSessionUnavailableWithoutCause() {
            throw new AuthSessionUnavailableException("down", null);
        }

        @GetMapping("/test/constraint-violation")
        void constraintViolation() {
            throw new DataIntegrityViolationException("fk", new SQLException("fk", "23000", 1451));
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handleNoResourceFound_whenPathNotExists_returns404() throws Exception {
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("요청한 경로를 찾을 수 없습니다."));
    }

    @Test
    void handleMethodNotSupported_whenMethodNotAllowed_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(get("/test/method-not-supported"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET, POST"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("지원하지 않는 HTTP 메서드입니다."));
    }

    @Test
    void handleDataIntegrityViolation_whenSqlStateIsDataException_returns400() throws Exception {
        mockMvc.perform(get("/test/data-exception"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("요청 값이 허용 범위를 벗어났습니다."));
    }

    @Test
    void handleDataIntegrityViolation_whenSqlStateIsConstraintViolation_returns409() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("데이터 제약 조건과 충돌하는 요청입니다."));
    }

    @Test
    void handleBusiness_whenCodePresent_returnsStatusMessageAndCode() throws Exception {
        mockMvc.perform(get("/test/business-with-code"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").value("세션 폐기"))
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void handleBusiness_whenCodeAbsent_omitsCodeField() throws Exception {
        mockMvc.perform(get("/test/business-without-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("없음"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void handleAuthSessionUnavailable_whenStoreDown_returns503WithFixedMessage() throws Exception {
        mockMvc.perform(get("/test/auth-session-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("인증 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(content().string(not(containsString("secret-sid"))));
    }

    @Test
    void handleAuthSessionUnavailable_whenNoCause_returns503() throws Exception {
        mockMvc.perform(get("/test/auth-session-unavailable-no-cause"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("인증 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }
}
