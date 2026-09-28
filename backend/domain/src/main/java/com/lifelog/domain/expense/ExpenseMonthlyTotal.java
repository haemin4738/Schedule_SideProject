package com.lifelog.domain.expense;

public record ExpenseMonthlyTotal(Integer year, Integer month, ExpenseType type, Long total) {
}
