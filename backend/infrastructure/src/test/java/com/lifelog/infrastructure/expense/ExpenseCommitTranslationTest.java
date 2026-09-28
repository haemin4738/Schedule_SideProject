package com.lifelog.infrastructure.expense;

import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 트랜잭션 커밋 시점(flush)에 발생한 FK/유니크 위반이 JpaTransactionManager를 거쳐
 * DataIntegrityViolationException(→ GlobalExceptionHandler 409)으로 변환되는지 검증한다.
 * 테스트 롤백 트랜잭션을 쓰지 않고 실제로 커밋하므로, 만든 데이터는 @AfterEach에서 직접 정리한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.lifelog.domain")
@EnableJpaRepositories(basePackages = "com.lifelog.infrastructure")
@Import(ExpenseCommitTranslationTest.TestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExpenseCommitTranslationTest {

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId == null) {
            return;
        }
        tx().executeWithoutResult(status -> {
            entityManager.createQuery("DELETE FROM Expense e WHERE e.user.id = :userId")
                    .setParameter("userId", userId).executeUpdate();
            entityManager.createQuery("DELETE FROM ExpenseCategory c WHERE c.user.id = :userId")
                    .setParameter("userId", userId).executeUpdate();
            entityManager.createQuery("DELETE FROM User u WHERE u.id = :userId")
                    .setParameter("userId", userId).executeUpdate();
        });
    }

    @Test
    void deleteCategory_whenExpenseLinkedAtCommit_throwsDataIntegrityViolation() {
        Long categoryId = tx().execute(status -> {
            User user = User.create("commit-" + System.nanoTime() + "@test.com", "encoded-pw", "커밋");
            entityManager.persist(user);
            userId = user.getId();
            ExpenseCategory food = categoryRepository.save(ExpenseCategory.create(user, ExpenseType.EXPENSE, "식비"));
            expenseRepository.save(Expense.create(user, food, ExpenseType.EXPENSE, 1_000L,
                    LocalDate.of(2026, 9, 1), null, null));
            return food.getId();
        });

        assertThatThrownBy(() -> tx().executeWithoutResult(status ->
                categoryRepository.delete(categoryRepository.findById(categoryId).orElseThrow())))
                .isInstanceOf(DataIntegrityViolationException.class);
        Boolean stillExists = tx().execute(status -> categoryRepository.findById(categoryId).isPresent());
        assertThat(stillExists).isTrue();
    }

    @Test
    void renameCategory_whenDuplicateNameAtCommit_throwsDataIntegrityViolation() {
        Long transportId = tx().execute(status -> {
            User user = User.create("commit-" + System.nanoTime() + "@test.com", "encoded-pw", "커밋");
            entityManager.persist(user);
            userId = user.getId();
            categoryRepository.save(ExpenseCategory.create(user, ExpenseType.EXPENSE, "식비"));
            return categoryRepository.save(ExpenseCategory.create(user, ExpenseType.EXPENSE, "교통")).getId();
        });

        assertThatThrownBy(() -> tx().executeWithoutResult(status ->
                categoryRepository.findById(transportId).orElseThrow().rename("식비")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }
}
