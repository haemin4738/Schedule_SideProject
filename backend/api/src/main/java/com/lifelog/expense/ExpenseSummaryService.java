package com.lifelog.expense;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.ExpenseCategoryTotal;
import com.lifelog.domain.expense.ExpenseMonthlyTotal;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.expense.dto.CategorySummaryResponse;
import com.lifelog.expense.dto.CategorySummaryResponse.CategoryItem;
import com.lifelog.expense.dto.MonthlySummaryResponse;
import com.lifelog.expense.dto.MonthlySummaryResponse.MonthlyItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseSummaryService {

    static final int MAX_MONTHS = 12;
    static final int MAX_DAYS = 366;

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthly(Long userId, YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            throw BusinessException.badRequest("조회 시작월은 종료월보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
            throw BusinessException.badRequest("월별 요약은 최대 " + MAX_MONTHS + "개월까지 조회할 수 있습니다.");
        }

        Map<YearMonth, long[]> totals = new HashMap<>();  // [income, expense]
        for (ExpenseMonthlyTotal row : expenseRepository.sumMonthlyByUserId(userId, from.atDay(1), to.atEndOfMonth())) {
            long[] bucket = totals.computeIfAbsent(YearMonth.of(row.year(), row.month()), k -> new long[2]);
            long amount = row.total() == null ? 0L : row.total();
            if (row.type() == ExpenseType.INCOME) {
                bucket[0] += amount;
            } else {
                bucket[1] += amount;
            }
        }

        List<MonthlyItem> months = new ArrayList<>();
        long totalIncome = 0L;
        long totalExpense = 0L;
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            long[] bucket = totals.getOrDefault(ym, new long[2]);
            months.add(new MonthlyItem(ym, bucket[0], bucket[1], bucket[0] - bucket[1]));
            totalIncome += bucket[0];
            totalExpense += bucket[1];
        }
        return new MonthlySummaryResponse(from, to, totalIncome, totalExpense,
                totalIncome - totalExpense, months);
    }

    @Transactional(readOnly = true)
    public CategorySummaryResponse byCategory(Long userId, ExpenseType type, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw BusinessException.badRequest("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw BusinessException.badRequest("카테고리별 요약은 최대 " + MAX_DAYS + "일까지 조회할 수 있습니다.");
        }

        List<CategoryItem> categories = expenseRepository.sumByCategory(userId, type, from, to).stream()
                .map(this::toItem)
                .toList();
        long total = categories.stream().mapToLong(CategoryItem::amount).sum();
        return new CategorySummaryResponse(type, from, to, total, categories);
    }

    private CategoryItem toItem(ExpenseCategoryTotal row) {
        return new CategoryItem(row.categoryId(), row.categoryName(),
                row.total() == null ? 0L : row.total(),
                row.count() == null ? 0L : row.count());
    }
}
