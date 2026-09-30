package com.lifelog.expense;

import com.lifelog.common.dto.ApiResponse;
import com.lifelog.common.dto.PagedResponse;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.expense.dto.CategorySummaryResponse;
import com.lifelog.expense.dto.DailySummaryResponse;
import com.lifelog.expense.dto.ExpenseRequest;
import com.lifelog.expense.dto.ExpenseResponse;
import com.lifelog.expense.dto.ExpenseSummary;
import com.lifelog.expense.dto.MonthlySummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Validated
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ExpenseSummaryService expenseSummaryService;

    @GetMapping
    public PagedResponse<ExpenseSummary> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ExpenseType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // 정렬(transactionDate DESC, id DESC)은 레포지토리 쿼리에 고정되어 있으므로 Sort를 넘기지 않는다.
        return PagedResponse.ok(expenseService.list(userId, from, to, type, categoryId,
                PageRequest.of(page, size)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ExpenseRequest request) {
        return ApiResponse.ok(expenseService.create(userId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(expenseService.get(userId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenseResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequest request) {
        return ApiResponse.ok(expenseService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        expenseService.delete(userId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/summary/monthly")
    public ApiResponse<MonthlySummaryResponse> monthlySummary(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
        return ApiResponse.ok(expenseSummaryService.monthly(userId, from, to));
    }

    @GetMapping("/summary/daily")
    public ApiResponse<DailySummaryResponse> dailySummary(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(expenseSummaryService.daily(userId, from, to));
    }

    @GetMapping("/summary/by-category")
    public ApiResponse<CategorySummaryResponse> categorySummary(
            @AuthenticationPrincipal Long userId,
            @RequestParam ExpenseType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(expenseSummaryService.byCategory(userId, type, from, to));
    }
}
