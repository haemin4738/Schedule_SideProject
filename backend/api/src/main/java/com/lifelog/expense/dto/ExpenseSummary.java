package com.lifelog.expense.dto;

import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.expense.PaymentMethod;

import java.time.LocalDate;

public record ExpenseSummary(
        Long id, ExpenseType type, Long categoryId, String categoryName,
        Long amount, PaymentMethod paymentMethod, LocalDate transactionDate, String description
) {
    public static ExpenseSummary from(Expense e) {
        return new ExpenseSummary(e.getId(), e.getType(),
                e.getCategory().getId(), e.getCategory().getName(),
                e.getAmount(), e.getPaymentMethod(), e.getTransactionDate(), e.getDescription());
    }
}
