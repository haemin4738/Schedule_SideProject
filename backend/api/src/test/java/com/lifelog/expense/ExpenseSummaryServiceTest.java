package com.lifelog.expense;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.ExpenseCategoryTotal;
import com.lifelog.domain.expense.ExpenseDailyTotal;
import com.lifelog.domain.expense.ExpenseMonthlyTotal;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.expense.dto.CategorySummaryResponse;
import com.lifelog.expense.dto.CategorySummaryResponse.CategoryItem;
import com.lifelog.expense.dto.DailySummaryResponse;
import com.lifelog.expense.dto.DailySummaryResponse.DailyItem;
import com.lifelog.expense.dto.MonthlySummaryResponse;
import com.lifelog.expense.dto.MonthlySummaryResponse.MonthlyItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseSummaryServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private ExpenseSummaryService service;

    private static HttpStatus statusOf(Throwable e) {
        return ((BusinessException) e).getStatus();
    }

    // ---- monthly ----

    @Test
    void monthly_whenDataSparse_fillsEmptyMonthsWithZeroAndComputesNet() {
        YearMonth from = YearMonth.of(2025, 11);
        YearMonth to = YearMonth.of(2026, 2);
        when(expenseRepository.sumMonthlyByUserId(1L, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 2, 28)))
                .thenReturn(List.of(
                        new ExpenseMonthlyTotal(2025, 12, ExpenseType.INCOME, 3_000_000L),
                        new ExpenseMonthlyTotal(2025, 12, ExpenseType.EXPENSE, 1_200_000L),
                        new ExpenseMonthlyTotal(2026, 2, ExpenseType.EXPENSE, 500_000L)));

        MonthlySummaryResponse result = service.monthly(1L, from, to);

        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(to);
        assertThat(result.months())
                .extracting(MonthlyItem::yearMonth, MonthlyItem::income, MonthlyItem::expense, MonthlyItem::net)
                .containsExactly(
                        tuple(YearMonth.of(2025, 11), 0L, 0L, 0L),
                        tuple(YearMonth.of(2025, 12), 3_000_000L, 1_200_000L, 1_800_000L),
                        tuple(YearMonth.of(2026, 1), 0L, 0L, 0L),
                        tuple(YearMonth.of(2026, 2), 0L, 500_000L, -500_000L));
        assertThat(result.totalIncome()).isEqualTo(3_000_000L);
        assertThat(result.totalExpense()).isEqualTo(1_700_000L);
        assertThat(result.net()).isEqualTo(1_300_000L);
    }

    @Test
    void monthly_whenNoData_returnsZeroFilledSingleMonth() {
        YearMonth ym = YearMonth.of(2026, 9);
        when(expenseRepository.sumMonthlyByUserId(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of());

        MonthlySummaryResponse result = service.monthly(1L, ym, ym);

        assertThat(result.months()).hasSize(1);
        assertThat(result.totalIncome()).isZero();
        assertThat(result.totalExpense()).isZero();
        assertThat(result.net()).isZero();
    }

    @Test
    void monthly_whenTotalNull_treatsAsZero() {
        YearMonth ym = YearMonth.of(2026, 9);
        when(expenseRepository.sumMonthlyByUserId(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(new ExpenseMonthlyTotal(2026, 9, ExpenseType.EXPENSE, null)));

        assertThat(service.monthly(1L, ym, ym).totalExpense()).isZero();
    }

    @Test
    void monthly_whenExactlyTwelveMonths_isAllowed() {
        YearMonth from = YearMonth.of(2025, 10);
        YearMonth to = YearMonth.of(2026, 9);
        when(expenseRepository.sumMonthlyByUserId(1L, LocalDate.of(2025, 10, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of());

        assertThat(service.monthly(1L, from, to).months()).hasSize(12);
    }

    @Test
    void monthly_whenThirteenMonths_throwsBadRequest() {
        assertThatThrownBy(() -> service.monthly(1L, YearMonth.of(2025, 9), YearMonth.of(2026, 9)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseSummaryServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void monthly_whenFromAfterTo_throwsBadRequest() {
        assertThatThrownBy(() -> service.monthly(1L, YearMonth.of(2026, 10), YearMonth.of(2026, 9)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseSummaryServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }

    // ---- byCategory ----

    @Test
    void byCategory_whenCalled_mapsItemsKeepsOrderAndSumsTotal() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(expenseRepository.sumByCategory(1L, ExpenseType.EXPENSE, from, to)).thenReturn(List.of(
                new ExpenseCategoryTotal(11L, "교통", 50_000L, 3L),
                new ExpenseCategoryTotal(10L, "식비", 30_000L, 5L)));

        CategorySummaryResponse result = service.byCategory(1L, ExpenseType.EXPENSE, from, to);

        assertThat(result.type()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(to);
        assertThat(result.total()).isEqualTo(80_000L);
        assertThat(result.categories())
                .extracting(CategoryItem::categoryId, CategoryItem::categoryName, CategoryItem::amount, CategoryItem::count)
                .containsExactly(tuple(11L, "교통", 50_000L, 3L), tuple(10L, "식비", 30_000L, 5L));
    }

    @Test
    void byCategory_whenNullTotals_treatsAsZero() {
        LocalDate day = LocalDate.of(2026, 9, 1);
        when(expenseRepository.sumByCategory(1L, ExpenseType.INCOME, day, day))
                .thenReturn(List.of(new ExpenseCategoryTotal(10L, "급여", null, null)));

        CategorySummaryResponse result = service.byCategory(1L, ExpenseType.INCOME, day, day);

        assertThat(result.total()).isZero();
        assertThat(result.categories().get(0).count()).isZero();
    }

    @Test
    void byCategory_whenExactly366Days_isAllowed() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31); // 윤년 366일
        when(expenseRepository.sumByCategory(1L, ExpenseType.EXPENSE, from, to)).thenReturn(List.of());

        assertThat(service.byCategory(1L, ExpenseType.EXPENSE, from, to).total()).isZero();
    }

    @Test
    void byCategory_whenMoreThan366Days_throwsBadRequest() {
        assertThatThrownBy(() -> service.byCategory(1L, ExpenseType.EXPENSE,
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseSummaryServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void byCategory_whenFromAfterTo_throwsBadRequest() {
        assertThatThrownBy(() -> service.byCategory(1L, ExpenseType.EXPENSE,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseSummaryServiceTest::statusOf).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }

    // ---- daily ----

    @Test
    void daily_whenDataSparse_returnsOnlyDaysWithDataSortedAndComputesNet() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        // 리포지토리 반환 순서가 섞여 있어도 날짜 오름차순으로 정렬되어야 한다
        when(expenseRepository.sumDailyByUserId(1L, from, to)).thenReturn(List.of(
                new ExpenseDailyTotal(LocalDate.of(2026, 9, 20), ExpenseType.EXPENSE, 12_000L),
                new ExpenseDailyTotal(LocalDate.of(2026, 9, 3), ExpenseType.INCOME, 3_000_000L),
                new ExpenseDailyTotal(LocalDate.of(2026, 9, 3), ExpenseType.EXPENSE, 45_000L),
                new ExpenseDailyTotal(LocalDate.of(2026, 9, 25), ExpenseType.INCOME, 10_000L)));

        DailySummaryResponse result = service.daily(1L, from, to);

        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(to);
        assertThat(result.days())
                .extracting(DailyItem::date, DailyItem::income, DailyItem::expense, DailyItem::net)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 3), 3_000_000L, 45_000L, 2_955_000L),
                        tuple(LocalDate.of(2026, 9, 20), 0L, 12_000L, -12_000L),
                        tuple(LocalDate.of(2026, 9, 25), 10_000L, 0L, 10_000L));
        assertThat(result.totalIncome()).isEqualTo(3_010_000L);
        assertThat(result.totalExpense()).isEqualTo(57_000L);
        assertThat(result.net()).isEqualTo(2_953_000L);
    }

    @Test
    void daily_whenNoData_returnsEmptyDaysAndZeroTotals() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(expenseRepository.sumDailyByUserId(1L, day, day)).thenReturn(List.of());

        DailySummaryResponse result = service.daily(1L, day, day);

        assertThat(result.days()).isEmpty();
        assertThat(result.totalIncome()).isZero();
        assertThat(result.totalExpense()).isZero();
        assertThat(result.net()).isZero();
    }

    @Test
    void daily_whenTotalNull_treatsAsZero() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(expenseRepository.sumDailyByUserId(1L, day, day))
                .thenReturn(List.of(new ExpenseDailyTotal(day, ExpenseType.EXPENSE, null)));

        DailySummaryResponse result = service.daily(1L, day, day);

        assertThat(result.days()).extracting(DailyItem::expense).containsExactly(0L);
        assertThat(result.net()).isZero();
    }

    @Test
    void daily_whenExactly366Days_isAllowed() {
        LocalDate from = LocalDate.of(2025, 10, 1);
        LocalDate to = from.plusDays(365);
        when(expenseRepository.sumDailyByUserId(1L, from, to)).thenReturn(List.of());

        assertThat(service.daily(1L, from, to).days()).isEmpty();
    }

    @Test
    void daily_whenMoreThan366Days_throwsBadRequest() {
        LocalDate from = LocalDate.of(2025, 10, 1);
        LocalDate to = from.plusDays(366);

        assertThatThrownBy(() -> service.daily(1L, from, to))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseSummaryServiceTest::statusOf)
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void daily_whenFromAfterTo_throwsBadRequest() {
        assertThatThrownBy(() -> service.daily(1L, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(ExpenseSummaryServiceTest::statusOf)
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(expenseRepository);
    }
}
