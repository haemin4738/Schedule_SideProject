package com.lifelog.jobapplication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.jobapplication.JobApplicationStatus;
import com.lifelog.jobapplication.dto.JobApplicationRequest;
import com.lifelog.jobapplication.dto.JobApplicationResponse;
import com.lifelog.jobapplication.dto.JobApplicationSummary;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * JobApplicationController @WebMvcTest — Security 필터 포함, 요청 검증 및 응답 envelope 형식 검증.
 */
@WebMvcTest(JobApplicationController.class)
@Import(SecurityConfig.class)
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobApplicationService jobApplicationService;

    // SecurityConfig가 요구하는 빈 (실제 필터체인 로드를 위해 필요)
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private static final Long USER_ID = 1L;

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private JobApplicationRequest validRequest() {
        return new JobApplicationRequest("회사A", "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10),
                "https://example.com/posting", "메모");
    }

    private JobApplicationResponse sampleResponse() {
        return new JobApplicationResponse(1L, "회사A", "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10),
                "https://example.com/posting", "메모",
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void list_withoutAuthentication_returnsErrorStatus() throws Exception {
        // SecurityConfig는 anonymous 인증을 허용하므로 authenticated() 규칙 위반 시
        // AccessDeniedException(403)이 발생한다(401 커스텀 AuthenticationEntryPoint 미설정).
        // 인증/보안 설정 변경은 사용자 승인이 필요한 항목이라 여기서는 현재 동작(403)을 그대로 검증한다.
        mockMvc.perform(get("/api/v1/job-applications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_whenAuthenticated_returnsPagedResponseEnvelope() throws Exception {
        JobApplicationSummary summary = new JobApplicationSummary(1L, "회사A", "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10));
        var page = new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1);
        when(jobApplicationService.list(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/job-applications").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].companyName").value("회사A"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void list_whenStatusFilterGiven_passesStatusToService() throws Exception {
        var page = new PageImpl<JobApplicationSummary>(List.of(), PageRequest.of(0, 20), 0);
        when(jobApplicationService.list(eq(USER_ID), eq(JobApplicationStatus.APPLIED), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/job-applications").param("status", "APPLIED").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void list_whenStatusInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/job-applications").param("status", "NOT_A_STATUS").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenSizeExceedsMax_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/job-applications").param("size", "101").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenPageIsNegative_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/job-applications").param("page", "-1").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenValidRequest_returns201WithApiResponseEnvelope() throws Exception {
        when(jobApplicationService.create(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/job-applications").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.companyName").value("회사A"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void create_whenCompanyNameBlank_returns400WithValidationMessage() throws Exception {
        JobApplicationRequest invalid = new JobApplicationRequest("", "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10), null, null);

        mockMvc.perform(post("/api/v1/job-applications").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("회사명은 필수입니다."));
    }

    @Test
    void create_whenPositionBlank_returns400WithValidationMessage() throws Exception {
        JobApplicationRequest invalid = new JobApplicationRequest("회사A", "",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10), null, null);

        mockMvc.perform(post("/api/v1/job-applications").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("지원 직무는 필수입니다."));
    }

    @Test
    void create_whenStatusMissing_returns400() throws Exception {
        JobApplicationRequest invalid = new JobApplicationRequest("회사A", "백엔드 개발자",
                null, LocalDate.of(2026, 1, 10), null, null);

        mockMvc.perform(post("/api/v1/job-applications").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("지원 상태는 필수입니다."));
    }

    @Test
    void create_whenAppliedAtMissing_returns400() throws Exception {
        JobApplicationRequest invalid = new JobApplicationRequest("회사A", "백엔드 개발자",
                JobApplicationStatus.APPLIED, null, null, null);

        mockMvc.perform(post("/api/v1/job-applications").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("지원일은 필수입니다."));
    }

    @Test
    void create_whenCompanyNameTooLong_returns400() throws Exception {
        JobApplicationRequest invalid = new JobApplicationRequest("가".repeat(201), "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10), null, null);

        mockMvc.perform(post("/api/v1/job-applications").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("회사명은 200자 이하여야 합니다."));
    }

    @Test
    void get_whenJobApplicationExists_returnsApiResponseEnvelope() throws Exception {
        when(jobApplicationService.get(any(), eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/job-applications/{id}", 1L).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void get_whenNotFound_returns404WithErrorEnvelope() throws Exception {
        when(jobApplicationService.get(any(), eq(999L)))
                .thenThrow(BusinessException.notFound("지원 내역을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/v1/job-applications/{id}", 999L).with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("지원 내역을 찾을 수 없습니다."));
    }

    @Test
    void get_whenAccessingOthersJobApplication_returns403() throws Exception {
        when(jobApplicationService.get(any(), eq(1L)))
                .thenThrow(BusinessException.forbidden("본인의 지원 내역만 접근할 수 있습니다."));

        mockMvc.perform(get("/api/v1/job-applications/{id}", 1L).with(asUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("본인의 지원 내역만 접근할 수 있습니다."));
    }

    @Test
    void update_whenValidRequest_returnsUpdatedResponse() throws Exception {
        when(jobApplicationService.update(any(), eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/job-applications/{id}", 1L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("회사A"));
    }

    @Test
    void update_whenCompanyNameBlank_returns400() throws Exception {
        JobApplicationRequest invalid = new JobApplicationRequest("", "백엔드 개발자",
                JobApplicationStatus.APPLIED, LocalDate.of(2026, 1, 10), null, null);

        mockMvc.perform(put("/api/v1/job-applications/{id}", 1L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_whenNotOwnedByUser_returns403() throws Exception {
        when(jobApplicationService.update(any(), eq(1L), any()))
                .thenThrow(BusinessException.forbidden("본인의 지원 내역만 접근할 수 있습니다."));

        mockMvc.perform(put("/api/v1/job-applications/{id}", 1L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void delete_whenOwnedByUser_returns200WithNullData() throws Exception {
        mockMvc.perform(delete("/api/v1/job-applications/{id}", 1L).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void delete_whenNotOwnedByUser_returns403() throws Exception {
        org.mockito.Mockito.doThrow(BusinessException.forbidden("본인의 지원 내역만 접근할 수 있습니다."))
                .when(jobApplicationService).delete(any(), eq(1L));

        mockMvc.perform(delete("/api/v1/job-applications/{id}", 1L).with(asUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
