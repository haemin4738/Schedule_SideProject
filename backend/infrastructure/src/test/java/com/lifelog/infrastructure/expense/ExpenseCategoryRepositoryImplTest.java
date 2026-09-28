package com.lifelog.infrastructure.expense;

import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExpenseCategoryRepositoryImpl 통합 테스트 — 실제 MySQL(lifelog_test) 사용.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.lifelog.domain")
@EnableJpaRepositories(basePackages = "com.lifelog.infrastructure")
@Import(ExpenseCategoryRepositoryImplTest.TestConfig.class)
class ExpenseCategoryRepositoryImplTest {

    @TestConfiguration
    @ComponentScan(basePackages = "com.lifelog.infrastructure.expense")
    static class TestConfig {
    }

    @Autowired
    private ExpenseCategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = User.create("cat1-" + System.nanoTime() + "@test.com", "encoded-pw", "유저1");
        user2 = User.create("cat2-" + System.nanoTime() + "@test.com", "encoded-pw", "유저2");
        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.flush();
    }

    private ExpenseCategory save(User user, ExpenseType type, String name) {
        return categoryRepository.save(ExpenseCategory.create(user, type, name));
    }

    @Test
    void save_whenValid_persistsWithIdAndTimestamps() {
        ExpenseCategory saved = save(user1, ExpenseType.EXPENSE, "식비");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_whenExists_returnsCategory() {
        ExpenseCategory saved = save(user1, ExpenseType.EXPENSE, "식비");
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(saved.getId()))
                .get()
                .satisfies(c -> {
                    assertThat(c.getName()).isEqualTo("식비");
                    assertThat(c.getType()).isEqualTo(ExpenseType.EXPENSE);
                });
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        assertThat(categoryRepository.findById(999_999L)).isEmpty();
    }

    @Test
    void findAllByUserIdAndType_whenTypeNull_returnsAllOwnSortedByTypeThenName() {
        save(user1, ExpenseType.INCOME, "급여");
        save(user1, ExpenseType.EXPENSE, "주거");
        save(user1, ExpenseType.EXPENSE, "교통");
        save(user1, ExpenseType.INCOME, "부수입");
        save(user2, ExpenseType.EXPENSE, "타인 카테고리");
        entityManager.flush();
        entityManager.clear();

        List<ExpenseCategory> result = categoryRepository.findAllByUserIdAndType(user1.getId(), null);

        // enum은 VARCHAR 문자열로 저장되므로 type 정렬은 문자열 사전순(EXPENSE < INCOME)
        assertThat(result).extracting(ExpenseCategory::getName)
                .containsExactly("교통", "주거", "급여", "부수입");
    }

    @Test
    void findAllByUserIdAndType_whenTypeGiven_filtersByType() {
        save(user1, ExpenseType.INCOME, "급여");
        save(user1, ExpenseType.EXPENSE, "식비");
        entityManager.flush();
        entityManager.clear();

        List<ExpenseCategory> result = categoryRepository.findAllByUserIdAndType(user1.getId(), ExpenseType.INCOME);

        assertThat(result).extracting(ExpenseCategory::getName).containsExactly("급여");
    }

    @Test
    void existsByUserIdAndTypeAndName_whenMatching_returnsTrue() {
        save(user1, ExpenseType.EXPENSE, "식비");
        entityManager.flush();

        assertThat(categoryRepository.existsByUserIdAndTypeAndName(user1.getId(), ExpenseType.EXPENSE, "식비")).isTrue();
    }

    @Test
    void existsByUserIdAndTypeAndName_whenDifferentTypeOrUser_returnsFalse() {
        save(user1, ExpenseType.EXPENSE, "식비");
        entityManager.flush();

        assertThat(categoryRepository.existsByUserIdAndTypeAndName(user1.getId(), ExpenseType.INCOME, "식비")).isFalse();
        assertThat(categoryRepository.existsByUserIdAndTypeAndName(user2.getId(), ExpenseType.EXPENSE, "식비")).isFalse();
    }

    @Test
    void countByUserId_whenCalled_countsOnlyOwnCategories() {
        save(user1, ExpenseType.EXPENSE, "식비");
        save(user1, ExpenseType.INCOME, "급여");
        save(user2, ExpenseType.EXPENSE, "식비");
        entityManager.flush();

        assertThat(categoryRepository.countByUserId(user1.getId())).isEqualTo(2);
        assertThat(categoryRepository.countByUserId(user2.getId())).isEqualTo(1);
    }

    @Test
    void save_whenDuplicateUserTypeName_throwsDataIntegrityViolation() {
        save(user1, ExpenseType.EXPENSE, "식비");
        entityManager.flush();

        // IDENTITY 전략이라 save 시점에 INSERT가 실행되고, @Repository 예외 변환으로 DataIntegrityViolationException
        assertThatThrownBy(() -> {
            save(user1, ExpenseType.EXPENSE, "식비");
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_whenSameNameDifferentType_succeeds() {
        save(user1, ExpenseType.EXPENSE, "기타");
        ExpenseCategory income = save(user1, ExpenseType.INCOME, "기타");
        entityManager.flush();

        assertThat(income.getId()).isNotNull();
    }

    @Test
    void rename_whenFlushed_persistsNewName() {
        ExpenseCategory saved = save(user1, ExpenseType.EXPENSE, "식비");
        entityManager.flush();
        entityManager.clear();

        ExpenseCategory found = categoryRepository.findById(saved.getId()).orElseThrow();
        found.rename("외식");
        categoryRepository.save(found);
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(saved.getId()).orElseThrow().getName()).isEqualTo("외식");
    }

    @Test
    void delete_whenNoExpenses_removesCategory() {
        ExpenseCategory saved = save(user1, ExpenseType.EXPENSE, "식비");
        entityManager.flush();
        Long id = saved.getId();

        categoryRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findById(id)).isEmpty();
    }
}
