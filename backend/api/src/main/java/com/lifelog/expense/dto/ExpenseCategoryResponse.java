package com.lifelog.expense.dto;

import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseType;

import java.time.LocalDateTime;

public record ExpenseCategoryResponse(
        Long id, ExpenseType type, String name,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static ExpenseCategoryResponse from(ExpenseCategory c) {
        return new ExpenseCategoryResponse(c.getId(), c.getType(), c.getName(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
