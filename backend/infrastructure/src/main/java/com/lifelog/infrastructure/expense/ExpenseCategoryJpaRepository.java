package com.lifelog.infrastructure.expense;

import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface ExpenseCategoryJpaRepository extends JpaRepository<ExpenseCategory, Long> {

    @Query("SELECT c FROM ExpenseCategory c WHERE c.user.id = :userId " +
           "AND (:type IS NULL OR c.type = :type) " +
           "ORDER BY c.type ASC, c.name ASC")
    List<ExpenseCategory> findAllByUserIdAndType(@Param("userId") Long userId,
                                                 @Param("type") ExpenseType type);

    boolean existsByUserIdAndTypeAndName(Long userId, ExpenseType type, String name);

    boolean existsByUserIdAndTypeAndNameAndIdNot(Long userId, ExpenseType type, String name, Long id);

    long countByUserId(Long userId);
}
