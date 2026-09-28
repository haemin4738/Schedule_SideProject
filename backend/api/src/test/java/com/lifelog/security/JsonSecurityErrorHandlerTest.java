package com.lifelog.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSecurityErrorHandlerTest {

    private final JsonSecurityErrorHandler handler = new JsonSecurityErrorHandler(JsonMapper.builder().build());

    @Test
    void commence_whenNotAuthenticated_writes401WithBearerChallengeAndEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"success\":false,\"data\":null,\"error\":\"인증이 필요합니다.\"}");
    }

    @Test
    void handle_whenAccessDenied_writes403WithoutChallengeHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"success\":false,\"data\":null,\"error\":\"접근 권한이 없습니다.\"}");
    }

    @Test
    void commence_whenResponseAlreadyCommitted_writesNothing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("data: streaming");
        response.flushBuffer();

        handler.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("expired"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(response.getContentAsString()).isEqualTo("data: streaming");
    }
}
