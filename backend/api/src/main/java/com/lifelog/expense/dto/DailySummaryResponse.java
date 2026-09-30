package com.lifelog.expense.dto;

import java.time.LocalDate;
import java.util.List;

/** 일별 요약 — days 는 내역이 있는 날짜만 포함(sparse), 날짜 오름차순. */
public record DailySummaryResponse(
        LocalDate from, LocalDate to,
        long totalIncome, long totalExpense, long net,
        List<DailyItem> days
) {
    public record DailyItem(LocalDate date, long income, long expense, long net) {}
}
