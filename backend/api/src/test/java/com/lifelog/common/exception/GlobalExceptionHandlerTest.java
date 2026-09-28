package com.lifelog.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {

        @GetMapping("/test/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "test/missing");
        }

        @GetMapping("/test/method-not-supported")
        void methodNotSupported() throws HttpRequestMethodNotSupportedException {
            throw new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));
        }

        @GetMapping("/test/data-exception")
        void dataException() {
            throw new DataIntegrityViolationException("data too long", new SQLException("too long", "22001", 1406));
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
}
