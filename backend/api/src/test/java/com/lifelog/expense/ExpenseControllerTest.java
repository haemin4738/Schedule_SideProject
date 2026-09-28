package com.lifelog.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.expense.dto.CategorySummaryResponse;
import com.lifelog.expense.dto.ExpenseRequest;
import com.lifelog.expense.dto.ExpenseResponse;
import com.lifelog.expense.dto.ExpenseSummary;
import com.lifelog.expense.dto.MonthlySummaryResponse;
import com.lifelog.security.JwtTokenProvider;
import com.lifelog.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
class ExpenseControllerTest {

    private static final Long USER_ID = 1L;
    private static final String BASE = "/api/v1/expenses";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private ExpenseSummaryService expenseSummaryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private RequestPostProcessor asUser() {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private ExpenseRequest validRequest() {
        return new ExpenseRequest(ExpenseType.EXPENSE, 10L, 12_000L, LocalDate.of(2026, 9, 1), "점심", "메모");
    }

    private ExpenseResponse sampleResponse() {
        return new ExpenseResponse(100L, ExpenseType.EXPENSE, 10L, "식비", 12_000L,
                LocalDate.of(2026, 9, 1), "점심", "메모",
                LocalDateTime.of(2026, 9, 1, 12, 0), LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ---- list ----

    @Test
    void list_withoutAuthentication_returnsForbidden() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    void list_whenNoParams_usesDefaultPagingAndReturnsPagedEnvelope() throws Exception {
        ExpenseSummary summary = new ExpenseSummary(100L, ExpenseType.EXPENSE, 10L, "식비",
                12_000L, LocalDate.of(2026, 9, 1), "점심");
        when(expenseService.list(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(BASE).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].categoryName").value("식비"))
                .andExpect(jsonPath("$.data[0].transactionDate").value("2026-09-01"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    void list_whenFiltersGiven_passesThemToService() throws Exception {
        when(expenseService.list(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 50), 0));

        mockMvc.perform(get(BASE).with(asUser())
                        .param("from", "2026-09-01").param("to", "2026-09-30")
                        .param("type", "INCOME").param("categoryId", "11")
                        .param("page", "1").param("size", "50"))
                .andExpect(status().isOk());

        verify(expenseService).list(USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                ExpenseType.INCOME, 11L, PageRequest.of(1, 50));
    }

    @Test
    void list_whenSizeZero_returnsBadRequest() throws Exception {
        mockMvc.perform(get(BASE).param("size", "0").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(expenseService);
    }

    @Test
    void list_whenSizeExceedsMax_returnsBadRequest() throws Exception {
        mockMvc.perform(get(BASE).param("size", "101").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenSizeIsMax_returnsOk() throws Exception {
        when(expenseService.list(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get(BASE).param("size", "100").with(asUser()))
                .andExpect(status().isOk());
    }

    @Test
    void list_whenPageNegative_returnsBadRequest() throws Exception {
        mockMvc.perform(get(BASE).param("page", "-1").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenDateFormatInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(get(BASE).param("from", "2026/09/01").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_whenFromAfterTo_returnsBadRequestFromService() throws Exception {
        when(expenseService.list(any(), any(), any(), any(), any(), any()))
                .thenThrow(BusinessException.badRequest("조회 시작일은 종료일보다 늦을 수 없습니다."));

        mockMvc.perform(get(BASE).param("from", "2026-09-02").param("to", "2026-09-01").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("조회 시작일은 종료일보다 늦을 수 없습니다."));
    }

    // ---- create ----

    @Test
    void create_whenValid_returns201() throws Exception {
        when(expenseService.create(eq(USER_ID), any())).thenReturn(sampleResponse());

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.amount").value(12000))
                .andExpect(jsonPath("$.data.transactionDate").value("2026-09-01"));
    }

    @Test
    void create_whenAmountZero_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(ExpenseType.EXPENSE, 10L, 0L, LocalDate.of(2026, 9, 1), null, null);

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("금액은 0보다 커야 합니다."));
        verifyNoInteractions(expenseService);
    }

    @Test
    void create_whenAmountExceedsMax_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(ExpenseType.EXPENSE, 10L, 100_000_000_000L,
                LocalDate.of(2026, 9, 1), null, null);

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("금액은 99,999,999,999원 이하여야 합니다."));
    }

    @Test
    void create_whenTypeMissing_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(null, 10L, 1_000L, LocalDate.of(2026, 9, 1), null, null);

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("내역 유형은 필수입니다."));
    }

    @Test
    void create_whenCategoryIdMissing_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(ExpenseType.EXPENSE, null, 1_000L, LocalDate.of(2026, 9, 1), null, null);

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("카테고리는 필수입니다."));
    }

    @Test
    void create_whenTransactionDateMissing_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(ExpenseType.EXPENSE, 10L, 1_000L, null, null, null);

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("거래일은 필수입니다."));
    }

    @Test
    void create_whenDescriptionTooLong_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(ExpenseType.EXPENSE, 10L, 1_000L,
                LocalDate.of(2026, 9, 1), "가".repeat(201), null);

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("설명은 200자 이하여야 합니다."));
    }

    @Test
    void create_whenMemoTooLong_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(ExpenseType.EXPENSE, 10L, 1_000L,
                LocalDate.of(2026, 9, 1), null, "가".repeat(10_001));

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("메모는 10,000자 이하여야 합니다."));
    }

    @Test
    void create_whenTypeInvalidEnum_returns400() throws Exception {
        // C1 회귀: 잘못된 enum 본문 → 400
        String body = "{\"type\":\"REFUND\",\"categoryId\":10,\"amount\":1000,\"transactionDate\":\"2026-09-01\"}";

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("요청 본문 형식이 올바르지 않습니다."));
    }

    @Test
    void create_whenDateMalformed_returns400() throws Exception {
        String body = "{\"type\":\"EXPENSE\",\"categoryId\":10,\"amount\":1000,\"transactionDate\":\"2026-13-01\"}";

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_whenCategoryTypeMismatch_returns400FromService() throws Exception {
        when(expenseService.create(eq(USER_ID), any()))
                .thenThrow(BusinessException.badRequest("카테고리 유형과 내역 유형이 일치하지 않습니다."));

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("카테고리 유형과 내역 유형이 일치하지 않습니다."));
    }

    @Test
    void create_whenCategoryDeletedConcurrently_returns409() throws Exception {
        // C1 회귀: FK 경합 → DataIntegrityViolationException → 409
        when(expenseService.create(eq(USER_ID), any()))
                .thenThrow(new DataIntegrityViolationException("fk violation"));

        mockMvc.perform(post(BASE).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("데이터 제약 조건과 충돌하는 요청입니다."));
    }

    // ---- get / update / delete ----

    @Test
    void get_whenOwned_returnsResponse() throws Exception {
        when(expenseService.get(USER_ID, 100L)).thenReturn(sampleResponse());

        mockMvc.perform(get(BASE + "/{id}", 100L).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("메모"));
    }

    @Test
    void get_whenNotFound_returns404() throws Exception {
        when(expenseService.get(USER_ID, 999L)).thenThrow(BusinessException.notFound("내역을 찾을 수 없습니다."));

        mockMvc.perform(get(BASE + "/{id}", 999L).with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("내역을 찾을 수 없습니다."));
    }

    @Test
    void get_whenIdNotNumeric_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", "abc").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_whenValid_returnsUpdated() throws Exception {
        when(expenseService.update(eq(USER_ID), eq(100L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put(BASE + "/{id}", 100L).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    void update_whenNotOwned_returns403() throws Exception {
        when(expenseService.update(eq(USER_ID), eq(100L), any()))
                .thenThrow(BusinessException.forbidden("본인의 내역만 접근할 수 있습니다."));

        mockMvc.perform(put(BASE + "/{id}", 100L).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_whenAmountNegative_returns400() throws Exception {
        ExpenseRequest invalid = new ExpenseRequest(ExpenseType.EXPENSE, 10L, -5L, LocalDate.of(2026, 9, 1), null, null);

        mockMvc.perform(put(BASE + "/{id}", 100L).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_whenOwned_returns200() throws Exception {
        mockMvc.perform(delete(BASE + "/{id}", 100L).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(expenseService).delete(USER_ID, 100L);
    }

    @Test
    void delete_whenNotOwned_returns403() throws Exception {
        doThrow(BusinessException.forbidden("본인의 내역만 접근할 수 있습니다."))
                .when(expenseService).delete(USER_ID, 100L);

        mockMvc.perform(delete(BASE + "/{id}", 100L).with(asUser()))
                .andExpect(status().isForbidden());
    }

    // ---- summary/monthly ----

    @Test
    void monthlySummary_whenValid_serializesYearMonthAsYyyyMm() throws Exception {
        YearMonth from = YearMonth.of(2026, 8);
        YearMonth to = YearMonth.of(2026, 9);
        MonthlySummaryResponse response = new MonthlySummaryResponse(from, to, 3_000_000L, 1_000_000L, 2_000_000L,
                List.of(new MonthlySummaryResponse.MonthlyItem(from, 0L, 0L, 0L),
                        new MonthlySummaryResponse.MonthlyItem(to, 3_000_000L, 1_000_000L, 2_000_000L)));
        when(expenseSummaryService.monthly(USER_ID, from, to)).thenReturn(response);

        mockMvc.perform(get(BASE + "/summary/monthly").param("from", "2026-08").param("to", "2026-09").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-08"))
                .andExpect(jsonPath("$.data.to").value("2026-09"))
                .andExpect(jsonPath("$.data.months[1].yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.months[1].net").value(2_000_000))
                .andExpect(jsonPath("$.data.totalIncome").value(3_000_000))
                .andExpect(jsonPath("$.data.net").value(2_000_000));
    }

    @Test
    void monthlySummary_whenToMissing_returns400() throws Exception {
        // C1 회귀: MissingServletRequestParameterException → 400
        mockMvc.perform(get(BASE + "/summary/monthly").param("from", "2026-08").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("필수 요청 파라미터가 누락되었습니다: to"));
        verifyNoInteractions(expenseSummaryService);
    }

    @Test
    void monthlySummary_whenYearMonthFormatInvalid_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/summary/monthly").param("from", "2026-8-01").param("to", "2026-09").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monthlySummary_whenRangeTooLong_returns400FromService() throws Exception {
        when(expenseSummaryService.monthly(any(), any(), any()))
                .thenThrow(BusinessException.badRequest("월별 요약은 최대 12개월까지 조회할 수 있습니다."));

        mockMvc.perform(get(BASE + "/summary/monthly").param("from", "2025-01").param("to", "2026-09").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("월별 요약은 최대 12개월까지 조회할 수 있습니다."));
    }

    // ---- summary/by-category ----

    @Test
    void categorySummary_whenValid_returnsItems() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        CategorySummaryResponse response = new CategorySummaryResponse(ExpenseType.EXPENSE, from, to, 80_000L,
                List.of(new CategorySummaryResponse.CategoryItem(11L, "교통", 50_000L, 3L),
                        new CategorySummaryResponse.CategoryItem(10L, "식비", 30_000L, 5L)));
        when(expenseSummaryService.byCategory(USER_ID, ExpenseType.EXPENSE, from, to)).thenReturn(response);

        mockMvc.perform(get(BASE + "/summary/by-category").with(asUser())
                        .param("type", "EXPENSE").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.from").value("2026-09-01"))
                .andExpect(jsonPath("$.data.total").value(80_000))
                .andExpect(jsonPath("$.data.categories[0].categoryName").value("교통"))
                .andExpect(jsonPath("$.data.categories[0].count").value(3));
    }

    @Test
    void categorySummary_whenTypeMissing_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/summary/by-category").with(asUser())
                        .param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("필수 요청 파라미터가 누락되었습니다: type"));
    }

    @Test
    void categorySummary_whenTypeInvalid_returns400() throws Exception {
        mockMvc.perform(get(BASE + "/summary/by-category").with(asUser())
                        .param("type", "NOPE").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isBadRequest());
    }
}
