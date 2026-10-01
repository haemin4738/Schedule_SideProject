package com.lifelog.infrastructure.expense;

import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseCategoryTotal;
import com.lifelog.domain.expense.ExpenseDailyTotal;
import com.lifelog.domain.expense.ExpenseMonthlyTotal;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ExpenseRepositoryImpl implements ExpenseRepository {

    private final ExpenseJpaRepository jpa;

    @Override public Expense save(Expense expense) { return jpa.save(expense); }
    @Override public Optional<Expense> findById(Long id) { return jpa.findById(id); }
    @Override public void delete(Expense expense) { jpa.delete(expense); }

    @Override
    public Page<Expense> findByUserIdAndFilter(Long userId, LocalDate from, LocalDate to,
                                               ExpenseType type, Long categoryId, Pageable pageable) {
        // 정렬은 쿼리에 고정(transactionDate DESC, id DESC) — 호출자의 Sort는 무시해 중복/충돌 방지 (unpaged 여도 Sort 를 버린다)
        Pageable unsorted = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : Pageable.unpaged();
        return jpa.findByUserIdAndFilter(userId, from, to, type, categoryId, unsorted);
    }

    @Override
    public boolean existsByCategoryId(Long categoryId) {
        return jpa.existsByCategoryId(categoryId);
    }

    @Override
    public List<ExpenseMonthlyTotal> sumMonthlyByUserId(Long userId, LocalDate from, LocalDate to) {
        return jpa.sumMonthlyByUserId(userId, from, to);
    }

    @Override
    public List<ExpenseDailyTotal> sumDailyByUserId(Long userId, LocalDate from, LocalDate to) {
        return jpa.sumDailyByUserId(userId, from, to);
    }

    @Override
    public List<ExpenseCategoryTotal> sumByCategory(Long userId, ExpenseType type, LocalDate from, LocalDate to) {
        return jpa.sumByCategory(userId, type, from, to);
    }
}
