package com.lifelog.expense.dto;

import com.lifelog.domain.expense.ExpenseType;

import java.time.LocalDate;
import java.util.List;

public record CategorySummaryResponse(
        ExpenseType type, LocalDate from, LocalDate to,
        long total, List<CategoryItem> categories
) {
    public record CategoryItem(Long categoryId, String categoryName, long amount, long count) {}
}
