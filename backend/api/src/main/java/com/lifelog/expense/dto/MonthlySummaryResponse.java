package com.lifelog.expense.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthlySummaryResponse(
        YearMonth from, YearMonth to,
        long totalIncome, long totalExpense, long net,
        List<MonthlyItem> months
) {
    public record MonthlyItem(YearMonth yearMonth, long income, long expense, long net) {}
}
