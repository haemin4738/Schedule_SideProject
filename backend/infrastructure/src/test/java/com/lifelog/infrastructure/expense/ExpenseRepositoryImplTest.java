package com.lifelog.infrastructure.expense;

import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseCategoryTotal;
import com.lifelog.domain.expense.ExpenseDailyTotal;
import com.lifelog.domain.expense.ExpenseMonthlyTotal;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * ExpenseRepositoryImpl 통합 테스트 — 실제 MySQL(lifelog_test)에서 JPQL 파싱/집계/필터를 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.lifelog.domain")
@EnableJpaRepositories(basePackages = "com.lifelog.infrastructure")
@Import(ExpenseRepositoryImplTest.TestConfig.class)
class ExpenseRepositoryImplTest {

    @TestConfiguration
    @ComponentScan(basePackages = "com.lifelog.infrastructure.expense")
    static class TestConfig {
    }

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseCategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    private User user1;
    private User user2;
    private ExpenseCategory food;
    private ExpenseCategory transport;
    private ExpenseCategory salary;
    private ExpenseCategory otherUserFood;

    @BeforeEach
    void setUp() {
        user1 = User.create("exp1-" + System.nanoTime() + "@test.com", "encoded-pw", "유저1");
        user2 = User.create("exp2-" + System.nanoTime() + "@test.com", "encoded-pw", "유저2");
        entityManager.persist(user1);
        entityManager.persist(user2);
        food = categoryRepository.save(ExpenseCategory.create(user1, ExpenseType.EXPENSE, "식비"));
        transport = categoryRepository.save(ExpenseCategory.create(user1, ExpenseType.EXPENSE, "교통"));
        salary = categoryRepository.save(ExpenseCategory.create(user1, ExpenseType.INCOME, "급여"));
        otherUserFood = categoryRepository.save(ExpenseCategory.create(user2, ExpenseType.EXPENSE, "식비"));
        entityManager.flush();
    }

    private Expense save(User user, ExpenseCategory category, long amount, LocalDate date) {
        return expenseRepository.save(Expense.create(user, category, category.getType(), amount, date, "설명", "메모"));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // ---- 기본 CRUD ----

    @Test
    void save_whenValid_persistsWithIdAndTimestamps() {
        Expense saved = save(user1, food, 10_000L, LocalDate.of(2026, 9, 1));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findById_whenExists_returnsExpenseWithStringEnumAndCategory() {
        Expense saved = save(user1, food, 10_000L, LocalDate.of(2026, 9, 1));
        flushAndClear();

        Expense found = expenseRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getType()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(found.getAmount()).isEqualTo(10_000L);
        assertThat(found.getCategory().getId()).isEqualTo(food.getId());
        // C3: VARCHAR 컬럼에 문자열로 저장되는지 확인
        Object raw = entityManager.createNativeQuery("SELECT type FROM expenses WHERE id = :id")
                .setParameter("id", saved.getId()).getSingleResult();
        assertThat(raw).isEqualTo("EXPENSE");
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        assertThat(expenseRepository.findById(999_999L)).isEmpty();
    }

    @Test
    void delete_whenCalled_removesExpense() {
        Expense saved = save(user1, food, 10_000L, LocalDate.of(2026, 9, 1));
        entityManager.flush();
        Long id = saved.getId();

        expenseRepository.delete(saved);
        flushAndClear();

        assertThat(expenseRepository.findById(id)).isEmpty();
    }

    // ---- 목록 필터 ----

    @Test
    void findByUserIdAndFilter_whenNoFilters_returnsOwnSortedByDateDescThenIdDesc() {
        Expense a = save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        Expense b = save(user1, food, 2_000L, LocalDate.of(2026, 9, 3));
        Expense c = save(user1, transport, 3_000L, LocalDate.of(2026, 9, 3));
        save(user2, otherUserFood, 9_000L, LocalDate.of(2026, 9, 2));
        flushAndClear();

        Page<Expense> page = expenseRepository.findByUserIdAndFilter(
                user1.getId(), null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Expense::getId)
                .containsExactly(c.getId(), b.getId(), a.getId());
    }

    @Test
    void findByUserIdAndFilter_whenFromToGiven_includesBothEnds() {
        save(user1, food, 1_000L, LocalDate.of(2026, 8, 31));
        Expense start = save(user1, food, 2_000L, LocalDate.of(2026, 9, 1));
        Expense end = save(user1, food, 3_000L, LocalDate.of(2026, 9, 30));
        save(user1, food, 4_000L, LocalDate.of(2026, 10, 1));
        flushAndClear();

        Page<Expense> page = expenseRepository.findByUserIdAndFilter(user1.getId(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Expense::getId).containsExactly(end.getId(), start.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByUserIdAndFilter_whenOnlyFromGiven_appliesLowerBoundOnly() {
        save(user1, food, 1_000L, LocalDate.of(2026, 8, 31));
        save(user1, food, 2_000L, LocalDate.of(2026, 9, 1));
        save(user1, food, 3_000L, LocalDate.of(2027, 1, 1));
        flushAndClear();

        Page<Expense> page = expenseRepository.findByUserIdAndFilter(user1.getId(),
                LocalDate.of(2026, 9, 1), null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByUserIdAndFilter_whenTypeGiven_filtersByType() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        save(user1, salary, 5_000_000L, LocalDate.of(2026, 9, 25));
        flushAndClear();

        Page<Expense> page = expenseRepository.findByUserIdAndFilter(user1.getId(),
                null, null, ExpenseType.INCOME, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Expense::getType).containsExactly(ExpenseType.INCOME);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findByUserIdAndFilter_whenCategoryIdGiven_filtersByCategory() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        save(user1, transport, 2_000L, LocalDate.of(2026, 9, 2));
        save(user1, transport, 3_000L, LocalDate.of(2026, 9, 3));
        flushAndClear();

        Page<Expense> page = expenseRepository.findByUserIdAndFilter(user1.getId(),
                null, null, null, transport.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(e -> e.getCategory().getId().equals(transport.getId()));
        // JOIN FETCH로 카테고리가 초기화되어 있어야 함 (clear 이후에도 이름 접근 가능)
        assertThat(page.getContent()).extracting(e -> e.getCategory().getName()).containsOnly("교통");
    }

    @Test
    void findByUserIdAndFilter_whenOtherUsersCategoryId_returnsEmpty() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        save(user2, otherUserFood, 2_000L, LocalDate.of(2026, 9, 1));
        flushAndClear();

        Page<Expense> page = expenseRepository.findByUserIdAndFilter(user1.getId(),
                null, null, null, otherUserFood.getId(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findByUserIdAndFilter_whenPaginated_countsTotalAndReturnsRequestedPage() {
        for (int day = 1; day <= 5; day++) {
            save(user1, food, 1_000L * day, LocalDate.of(2026, 9, day));
        }
        flushAndClear();

        Page<Expense> first = expenseRepository.findByUserIdAndFilter(user1.getId(),
                null, null, ExpenseType.EXPENSE, null, PageRequest.of(0, 2));
        Page<Expense> last = expenseRepository.findByUserIdAndFilter(user1.getId(),
                null, null, ExpenseType.EXPENSE, null, PageRequest.of(2, 2));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).extracting(Expense::getTransactionDate)
                .containsExactly(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 4));
        assertThat(last.getContent()).extracting(Expense::getTransactionDate)
                .containsExactly(LocalDate.of(2026, 9, 1));
    }

    @Test
    void findByUserIdAndFilter_whenCallerPassesSort_ignoresItAndKeepsFixedOrder() {
        Expense older = save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        Expense newer = save(user1, food, 2_000L, LocalDate.of(2026, 9, 2));
        flushAndClear();

        Page<Expense> page = expenseRepository.findByUserIdAndFilter(user1.getId(),
                null, null, null, null, PageRequest.of(0, 20, Sort.by("amount").ascending()));

        assertThat(page.getContent()).extracting(Expense::getId).containsExactly(newer.getId(), older.getId());
    }

    // ---- existsByCategoryId / FK ----

    @Test
    void existsByCategoryId_whenExpenseLinked_returnsTrue() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        entityManager.flush();

        assertThat(expenseRepository.existsByCategoryId(food.getId())).isTrue();
        assertThat(expenseRepository.existsByCategoryId(transport.getId())).isFalse();
    }

    @Test
    void deleteCategory_whenExpenseLinked_throwsOnFlushByForeignKey() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        flushAndClear();
        ExpenseCategory managedFood = categoryRepository.findById(food.getId()).orElseThrow();

        assertThatThrownBy(() -> {
            categoryRepository.delete(managedFood);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class)
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    // ---- 월별 집계 ----

    @Test
    void sumMonthlyByUserId_whenMultipleMonthsAndTypes_groupsByYearMonthType() {
        save(user1, food, 1_000L, LocalDate.of(2025, 12, 31));
        save(user1, transport, 2_000L, LocalDate.of(2025, 12, 1));
        save(user1, food, 3_000L, LocalDate.of(2026, 1, 1));
        save(user1, salary, 100_000L, LocalDate.of(2026, 1, 25));
        save(user1, food, 7_000L, LocalDate.of(2026, 2, 1)); // 범위 밖
        save(user2, otherUserFood, 50_000L, LocalDate.of(2026, 1, 10)); // 타인
        flushAndClear();

        List<ExpenseMonthlyTotal> result = expenseRepository.sumMonthlyByUserId(user1.getId(),
                LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 31));

        assertThat(result)
                .extracting(ExpenseMonthlyTotal::year, ExpenseMonthlyTotal::month,
                        ExpenseMonthlyTotal::type, ExpenseMonthlyTotal::total)
                .containsExactlyInAnyOrder(
                        tuple(2025, 12, ExpenseType.EXPENSE, 3_000L),
                        tuple(2026, 1, ExpenseType.EXPENSE, 3_000L),
                        tuple(2026, 1, ExpenseType.INCOME, 100_000L));
    }

    @Test
    void sumMonthlyByUserId_whenNoData_returnsEmptyList() {
        List<ExpenseMonthlyTotal> result = expenseRepository.sumMonthlyByUserId(user1.getId(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(result).isEmpty();
    }

    // ---- 카테고리별 집계 ----

    @Test
    void sumByCategory_whenCalled_returnsSumAndCountOrderedByAmountDesc() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));
        save(user1, food, 2_000L, LocalDate.of(2026, 9, 30));
        save(user1, transport, 5_000L, LocalDate.of(2026, 9, 15));
        save(user1, food, 99_000L, LocalDate.of(2026, 10, 1)); // 범위 밖
        save(user1, salary, 1_000_000L, LocalDate.of(2026, 9, 25)); // 다른 type
        save(user2, otherUserFood, 70_000L, LocalDate.of(2026, 9, 10)); // 타인
        flushAndClear();

        List<ExpenseCategoryTotal> result = expenseRepository.sumByCategory(user1.getId(), ExpenseType.EXPENSE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result)
                .extracting(ExpenseCategoryTotal::categoryId, ExpenseCategoryTotal::categoryName,
                        ExpenseCategoryTotal::total, ExpenseCategoryTotal::count)
                .containsExactly(
                        tuple(transport.getId(), "교통", 5_000L, 1L),
                        tuple(food.getId(), "식비", 3_000L, 2L));
    }

    @Test
    void sumByCategory_whenAmountsTie_ordersByCategoryIdAsc() {
        save(user1, transport, 4_000L, LocalDate.of(2026, 9, 2));
        save(user1, food, 4_000L, LocalDate.of(2026, 9, 1));
        flushAndClear();

        List<ExpenseCategoryTotal> result = expenseRepository.sumByCategory(user1.getId(), ExpenseType.EXPENSE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // food가 먼저 저장되어 id가 더 작다
        assertThat(food.getId()).isLessThan(transport.getId());
        assertThat(result).extracting(ExpenseCategoryTotal::categoryId)
                .containsExactly(food.getId(), transport.getId());
    }

    // ---- 일별 집계 ----

    @Test
    void sumDailyByUserId_whenMultipleDaysAndTypes_groupsByDateAndTypeOrderedByDate() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 1));        // 시작 경계일
        save(user1, transport, 2_000L, LocalDate.of(2026, 9, 1));   // 같은 날·같은 유형 → 합산
        save(user1, salary, 100_000L, LocalDate.of(2026, 9, 1));    // 같은 날·다른 유형 → 별도 행
        save(user1, food, 3_000L, LocalDate.of(2026, 9, 30));       // 종료 경계일
        save(user1, food, 5_000L, LocalDate.of(2026, 9, 15));
        save(user1, food, 7_000L, LocalDate.of(2026, 8, 31));       // 범위 밖(이전)
        save(user1, food, 9_000L, LocalDate.of(2026, 10, 1));       // 범위 밖(이후)
        save(user2, otherUserFood, 50_000L, LocalDate.of(2026, 9, 15)); // 타인
        flushAndClear();

        List<ExpenseDailyTotal> result = expenseRepository.sumDailyByUserId(user1.getId(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result)
                .extracting(ExpenseDailyTotal::date, ExpenseDailyTotal::type, ExpenseDailyTotal::total)
                .containsExactlyInAnyOrder(
                        tuple(LocalDate.of(2026, 9, 1), ExpenseType.EXPENSE, 3_000L),
                        tuple(LocalDate.of(2026, 9, 1), ExpenseType.INCOME, 100_000L),
                        tuple(LocalDate.of(2026, 9, 15), ExpenseType.EXPENSE, 5_000L),
                        tuple(LocalDate.of(2026, 9, 30), ExpenseType.EXPENSE, 3_000L));
        assertThat(result).extracting(ExpenseDailyTotal::date)
                .isSortedAccordingTo(LocalDate::compareTo);
    }

    @Test
    void sumDailyByUserId_whenSingleDayRange_returnsOnlyThatDay() {
        save(user1, food, 1_000L, LocalDate.of(2026, 9, 9));
        save(user1, food, 2_000L, LocalDate.of(2026, 9, 10));
        save(user1, food, 4_000L, LocalDate.of(2026, 9, 10));
        save(user1, food, 8_000L, LocalDate.of(2026, 9, 11));
        flushAndClear();

        LocalDate day = LocalDate.of(2026, 9, 10);
        List<ExpenseDailyTotal> result = expenseRepository.sumDailyByUserId(user1.getId(), day, day);

        assertThat(result)
                .extracting(ExpenseDailyTotal::date, ExpenseDailyTotal::type, ExpenseDailyTotal::total)
                .containsExactly(tuple(day, ExpenseType.EXPENSE, 6_000L));
    }

    @Test
    void sumDailyByUserId_whenOnlyOtherUsersData_returnsEmptyList() {
        save(user2, otherUserFood, 50_000L, LocalDate.of(2026, 9, 15));
        flushAndClear();

        List<ExpenseDailyTotal> result = expenseRepository.sumDailyByUserId(user1.getId(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result).isEmpty();
    }
}
