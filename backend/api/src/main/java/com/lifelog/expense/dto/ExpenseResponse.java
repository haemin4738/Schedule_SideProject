package com.lifelog.expense.dto;

import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpenseResponse(
        Long id, ExpenseType type, Long categoryId, String categoryName,
        Long amount, LocalDate transactionDate,
        String description, String memo,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(e.getId(), e.getType(),
                e.getCategory().getId(), e.getCategory().getName(),
                e.getAmount(), e.getTransactionDate(),
                e.getDescription(), e.getMemo(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
