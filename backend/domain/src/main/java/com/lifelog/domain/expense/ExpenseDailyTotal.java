package com.lifelog.domain.expense;

import java.time.LocalDate;

public record ExpenseDailyTotal(LocalDate date, ExpenseType type, Long total) {
}
