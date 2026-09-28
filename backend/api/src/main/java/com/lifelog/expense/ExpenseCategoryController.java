package com.lifelog.expense;

import com.lifelog.common.dto.ApiResponse;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.expense.dto.ExpenseCategoryCreateRequest;
import com.lifelog.expense.dto.ExpenseCategoryResponse;
import com.lifelog.expense.dto.ExpenseCategoryUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
@Validated
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @GetMapping
    public ApiResponse<List<ExpenseCategoryResponse>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ExpenseType type) {
        return ApiResponse.ok(expenseCategoryService.list(userId, type));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseCategoryResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ExpenseCategoryCreateRequest request) {
        return ApiResponse.ok(expenseCategoryService.create(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenseCategoryResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ExpenseCategoryUpdateRequest request) {
        return ApiResponse.ok(expenseCategoryService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        expenseCategoryService.delete(userId, id);
        return ApiResponse.ok(null);
    }
}
