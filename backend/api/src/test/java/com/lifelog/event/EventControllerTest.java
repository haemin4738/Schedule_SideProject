package com.lifelog.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.event.EventCategory;
import com.lifelog.event.dto.EventRequest;
import com.lifelog.event.dto.EventResponse;
import com.lifelog.event.dto.EventSummary;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EventController @WebMvcTest — Security 필터 포함, 요청 검증 및 응답 envelope 형식 검증.
 */
@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    // SecurityConfig가 요구하는 빈 (실제 필터체인 로드를 위해 필요)
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private static final Long USER_ID = 1L;

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private EventRequest validRequest() {
        return new EventRequest("팀 회의", "주간 회의",
                LocalDateTime.of(2026, 1, 10, 10, 0), LocalDateTime.of(2026, 1, 10, 11, 0),
                false, "회의실 A", "#FF0000", EventCategory.WORK);
    }

    private EventResponse sampleResponse() {
        return new EventResponse(1L, "팀 회의", "주간 회의",
                LocalDateTime.of(2026, 1, 10, 10, 0), LocalDateTime.of(2026, 1, 10, 11, 0),
                false, "회의실 A", "#FF0000", EventCategory.WORK,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void list_withoutAuthentication_returnsErrorStatus() throws Exception {
        // SecurityConfig는 anonymous 인증을 허용하므로 authenticated() 규칙 위반 시
        // AccessDeniedException(403)이 발생한다(401 커스텀 AuthenticationEntryPoint 미설정).
        // 인증/보안 설정 변경은 사용자 승인이 필요한 항목이라 여기서는 현재 동작(403)을 그대로 검증한다.
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_whenAuthenticated_returnsPagedResponseEnvelope() throws Exception {
        EventSummary summary = new EventSummary(1L, "팀 회의",
                LocalDateTime.of(2026, 1, 10, 10, 0), LocalDateTime.of(2026, 1, 10, 11, 0),
                false, "#FF0000", EventCategory.WORK);
        var page = new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1);
        when(eventService.list(any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/events").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("팀 회의"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void list_whenSizeExceedsMax_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("size", "101").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenPageIsNegative_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("page", "-1").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenValidRequest_returns201WithApiResponseEnvelope() throws Exception {
        when(eventService.create(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/events").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("팀 회의"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void create_whenTitleBlank_returns400WithValidationMessage() throws Exception {
        EventRequest invalid = new EventRequest("", "설명",
                LocalDateTime.of(2026, 1, 10, 10, 0), null, false, null, null, null);

        mockMvc.perform(post("/api/v1/events").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("제목은 필수입니다."));
    }

    @Test
    void create_whenStartAtMissing_returns400() throws Exception {
        EventRequest invalid = new EventRequest("제목", "설명",
                null, null, false, null, null, null);

        mockMvc.perform(post("/api/v1/events").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("시작 시간은 필수입니다."));
    }

    @Test
    void create_whenTitleTooLong_returns400() throws Exception {
        EventRequest invalid = new EventRequest("가".repeat(201), "설명",
                LocalDateTime.of(2026, 1, 10, 10, 0), null, false, null, null, null);

        mockMvc.perform(post("/api/v1/events").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("제목은 200자 이하여야 합니다."));
    }

    @Test
    void get_whenEventExists_returnsApiResponseEnvelope() throws Exception {
        when(eventService.get(any(), eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/events/{id}", 1L).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void get_whenEventNotFound_returns404WithErrorEnvelope() throws Exception {
        when(eventService.get(any(), eq(999L)))
                .thenThrow(BusinessException.notFound("일정을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/v1/events/{id}", 999L).with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("일정을 찾을 수 없습니다."));
    }

    @Test
    void get_whenAccessingOthersEvent_returns403() throws Exception {
        when(eventService.get(any(), eq(1L)))
                .thenThrow(BusinessException.forbidden("본인의 일정만 접근할 수 있습니다."));

        mockMvc.perform(get("/api/v1/events/{id}", 1L).with(asUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("본인의 일정만 접근할 수 있습니다."));
    }

    @Test
    void update_whenValidRequest_returnsUpdatedResponse() throws Exception {
        when(eventService.update(any(), eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/events/{id}", 1L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("팀 회의"));
    }

    @Test
    void update_whenTitleBlank_returns400() throws Exception {
        EventRequest invalid = new EventRequest("", "설명",
                LocalDateTime.of(2026, 1, 10, 10, 0), null, false, null, null, null);

        mockMvc.perform(put("/api/v1/events/{id}", 1L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_whenOwnedByUser_returns200WithNullData() throws Exception {
        mockMvc.perform(delete("/api/v1/events/{id}", 1L).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void delete_whenNotOwnedByUser_returns403() throws Exception {
        org.mockito.Mockito.doThrow(BusinessException.forbidden("본인의 일정만 접근할 수 있습니다."))
                .when(eventService).delete(any(), eq(1L));

        mockMvc.perform(delete("/api/v1/events/{id}", 1L).with(asUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
