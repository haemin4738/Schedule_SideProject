package com.lifelog.sse;

import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SSE 구독 — 실제 SseService 로 연결 직후 이벤트를 확인한다 */
@WebMvcTest(SseController.class)
@Import({SecurityConfig.class, SseService.class})
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // SecurityConfig 가 요구하는 빈
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void subscribe_whenAuthenticated_sendsConnectedEventImmediately() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sse/events")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(
                                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // 일정 변경이 없어도 연결 즉시 CONNECTED 가 나가 클라이언트가 연결(재연결)을 알 수 있다
        assertThat(result.getResponse().getContentAsString()).contains("event:CONNECTED");
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void subscribe_whenNotAuthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/sse/events")).andExpect(status().isUnauthorized());
    }
}
