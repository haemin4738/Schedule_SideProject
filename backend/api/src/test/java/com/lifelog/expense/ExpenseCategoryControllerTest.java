package com.lifelog.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.expense.dto.ExpenseCategoryCreateRequest;
import com.lifelog.expense.dto.ExpenseCategoryResponse;
import com.lifelog.expense.dto.ExpenseCategoryUpdateRequest;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExpenseCategoryController.class)
@Import(SecurityConfig.class)
class ExpenseCategoryControllerTest {

    private static final Long USER_ID = 1L;
    private static final String BASE = "/api/v1/expense-categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseCategoryService expenseCategoryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private RequestPostProcessor asUser() {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private ExpenseCategoryResponse sample() {
        return new ExpenseCategoryResponse(10L, ExpenseType.EXPENSE, "식비",
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    @Test
    void list_withoutAuthentication_returnsForbidden() throws Exception {
        // 현재 SecurityConfig는 401 EntryPoint 미설정 → 403 (JobApplicationControllerTest와 동일한 기존 동작)
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    void list_whenNoType_passesNullAndReturnsEnvelope() throws Exception {
        when(expenseCategoryService.list(eq(USER_ID), isNull())).thenReturn(List.of(sample()));

        mockMvc.perform(get(BASE).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data[0].name").value("식비"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void list_whenTypeGiven_passesTypeToService() throws Exception {
        when(expenseCategoryService.list(USER_ID, ExpenseType.INCOME)).thenReturn(List.of());

        mockMvc.perform(get(BASE).param("type", "INCOME").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(expenseCategoryService).list(USER_ID, ExpenseType.INCOME);
    }

    @Test
    void list_whenTypeInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(get(BASE).param("type", "NOPE").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create_whenValid_returns201() throws Exception {
        when(expenseCategoryService.create(eq(USER_ID), any())).thenReturn(sample());

        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "식비"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void create_whenNameBlank_returns400WithMessage() throws Exception {
        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("카테고리 이름은 필수입니다."));
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void create_whenNameTooLong_returns400() throws Exception {
        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "가".repeat(51)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("카테고리 이름은 50자 이하여야 합니다."));
    }

    @Test
    void create_whenTypeMissing_returns400() throws Exception {
        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"식비\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("카테고리 유형은 필수입니다."));
    }

    @Test
    void create_whenTypeInvalidEnum_returns400WithGenericMessage() throws Exception {
        // C1 회귀: HttpMessageNotReadableException → 400 (이전엔 catch-all 500)
        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOPE\",\"name\":\"식비\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("요청 본문 형식이 올바르지 않습니다."));
    }

    @Test
    void create_whenBodyMalformed_returns400() throws Exception {
        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenDuplicate_returns409() throws Exception {
        when(expenseCategoryService.create(eq(USER_ID), any()))
                .thenThrow(BusinessException.conflict("이미 존재하는 카테고리입니다."));

        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "식비"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 존재하는 카테고리입니다."));
    }

    @Test
    void create_whenUniqueConstraintRace_returns409WithoutSqlDetails() throws Exception {
        // C1 회귀: 선검사를 통과한 뒤 DB 유니크 제약에 걸린 경합 상황
        when(expenseCategoryService.create(eq(USER_ID), any()))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Duplicate entry '1-EXPENSE-식비' for key 'uk_expense_categories_user_type_name']"));

        mockMvc.perform(post(BASE).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExpenseCategoryCreateRequest(ExpenseType.EXPENSE, "식비"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("데이터 제약 조건과 충돌하는 요청입니다."));
    }

    @Test
    void update_whenValid_returnsUpdated() throws Exception {
        when(expenseCategoryService.update(eq(USER_ID), eq(10L), any())).thenReturn(sample());

        mockMvc.perform(put(BASE + "/{id}", 10L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseCategoryUpdateRequest("식비"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("식비"));
    }

    @Test
    void update_whenNameBlank_returns400() throws Exception {
        mockMvc.perform(put(BASE + "/{id}", 10L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseCategoryUpdateRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_whenNotOwned_returns403() throws Exception {
        when(expenseCategoryService.update(eq(USER_ID), eq(10L), any()))
                .thenThrow(BusinessException.forbidden("본인의 카테고리만 접근할 수 있습니다."));

        mockMvc.perform(put(BASE + "/{id}", 10L).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseCategoryUpdateRequest("외식"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_whenOwned_returns200WithNullData() throws Exception {
        mockMvc.perform(delete(BASE + "/{id}", 10L).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(expenseCategoryService).delete(USER_ID, 10L);
    }

    @Test
    void delete_whenLinkedExpensesExist_returns409() throws Exception {
        doThrow(BusinessException.conflict("해당 카테고리를 사용하는 내역이 있어 삭제할 수 없습니다."))
                .when(expenseCategoryService).delete(USER_ID, 10L);

        mockMvc.perform(delete(BASE + "/{id}", 10L).with(asUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("해당 카테고리를 사용하는 내역이 있어 삭제할 수 없습니다."));
    }

    @Test
    void delete_whenForeignKeyRace_returns409() throws Exception {
        // C1 회귀: existsByCategoryId 통과 후 동시 등록된 내역 때문에 FK 위반 → 409
        doThrow(new DataIntegrityViolationException("fk violation"))
                .when(expenseCategoryService).delete(USER_ID, 10L);

        mockMvc.perform(delete(BASE + "/{id}", 10L).with(asUser()))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_whenNotFound_returns404() throws Exception {
        doThrow(BusinessException.notFound("카테고리를 찾을 수 없습니다."))
                .when(expenseCategoryService).delete(USER_ID, 999L);

        mockMvc.perform(delete(BASE + "/{id}", 999L).with(asUser()))
                .andExpect(status().isNotFound());
    }
}
