package com.lifelog.domain.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {
    Expense save(Expense expense);
    Optional<Expense> findById(Long id);
    /** from/to/type/categoryId가 null이면 해당 조건 미적용. from/to 양끝 포함. transactionDate DESC, id DESC 정렬. */
    Page<Expense> findByUserIdAndFilter(Long userId, LocalDate from, LocalDate to,
                                        ExpenseType type, Long categoryId, Pageable pageable);
    boolean existsByCategoryId(Long categoryId);
    List<ExpenseMonthlyTotal> sumMonthlyByUserId(Long userId, LocalDate from, LocalDate to);
    List<ExpenseCategoryTotal> sumByCategory(Long userId, ExpenseType type, LocalDate from, LocalDate to);
    void delete(Expense expense);
}
