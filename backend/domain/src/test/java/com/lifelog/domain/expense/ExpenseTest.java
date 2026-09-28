package com.lifelog.domain.expense;

import com.lifelog.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Expense / ExpenseCategory 도메인 단위 테스트 (순수 자바, DB 없음).
 */
class ExpenseTest {

    private User user;
    private ExpenseCategory food;
    private ExpenseCategory salary;

    @BeforeEach
    void setUp() {
        user = User.create("owner@test.com", "encoded-pw", "소유자");
        food = ExpenseCategory.create(user, ExpenseType.EXPENSE, "식비");
        salary = ExpenseCategory.create(user, ExpenseType.INCOME, "급여");
    }

    @Test
    void create_whenValid_setsAllFields() {
        Expense expense = Expense.create(user, food, ExpenseType.EXPENSE, 12_000L,
                LocalDate.of(2026, 9, 1), "점심", "메모");

        assertThat(expense.getUser()).isSameAs(user);
        assertThat(expense.getCategory()).isSameAs(food);
        assertThat(expense.getType()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(expense.getAmount()).isEqualTo(12_000L);
        assertThat(expense.getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(expense.getDescription()).isEqualTo("점심");
        assertThat(expense.getMemo()).isEqualTo("메모");
    }

    @Test
    void create_whenAmountZero_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Expense.create(user, food, ExpenseType.EXPENSE, 0L,
                LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenAmountNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Expense.create(user, food, ExpenseType.EXPENSE, -1L,
                LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenAmountNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Expense.create(user, food, ExpenseType.EXPENSE, null,
                LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenCategoryTypeMismatch_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Expense.create(user, salary, ExpenseType.EXPENSE, 1_000L,
                LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenCategoryNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Expense.create(user, null, ExpenseType.EXPENSE, 1_000L,
                LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_whenTypeNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Expense.create(user, food, null, 1_000L,
                LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_whenValid_changesCategoryTypeAndFields() {
        Expense expense = Expense.create(user, food, ExpenseType.EXPENSE, 12_000L,
                LocalDate.of(2026, 9, 1), "점심", "메모");

        expense.update(salary, ExpenseType.INCOME, 3_000_000L, LocalDate.of(2026, 9, 25), "월급", null);

        assertThat(expense.getCategory()).isSameAs(salary);
        assertThat(expense.getType()).isEqualTo(ExpenseType.INCOME);
        assertThat(expense.getAmount()).isEqualTo(3_000_000L);
        assertThat(expense.getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(expense.getDescription()).isEqualTo("월급");
        assertThat(expense.getMemo()).isNull();
    }

    @Test
    void update_whenCategoryTypeMismatch_throwsAndKeepsOriginalState() {
        Expense expense = Expense.create(user, food, ExpenseType.EXPENSE, 12_000L,
                LocalDate.of(2026, 9, 1), "점심", "메모");

        assertThatThrownBy(() -> expense.update(salary, ExpenseType.EXPENSE, 5_000L,
                LocalDate.of(2026, 9, 2), null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(expense.getCategory()).isSameAs(food);
        assertThat(expense.getAmount()).isEqualTo(12_000L);
    }

    @Test
    void update_whenAmountZero_throwsIllegalArgumentException() {
        Expense expense = Expense.create(user, food, ExpenseType.EXPENSE, 12_000L,
                LocalDate.of(2026, 9, 1), null, null);

        assertThatThrownBy(() -> expense.update(food, ExpenseType.EXPENSE, 0L,
                LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryCreate_whenCalled_setsUserTypeAndName() {
        assertThat(food.getUser()).isSameAs(user);
        assertThat(food.getType()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(food.getName()).isEqualTo("식비");
    }

    @Test
    void categoryRename_whenCalled_changesNameOnly() {
        food.rename("외식");

        assertThat(food.getName()).isEqualTo("외식");
        assertThat(food.getType()).isEqualTo(ExpenseType.EXPENSE);
    }
}
