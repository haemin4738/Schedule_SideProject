package com.lifelog.infrastructure.expense;

import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseCategoryTotal;
import com.lifelog.domain.expense.ExpenseMonthlyTotal;
import com.lifelog.domain.expense.ExpenseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

interface ExpenseJpaRepository extends JpaRepository<Expense, Long> {

    @Query(value = "SELECT e FROM Expense e JOIN FETCH e.category " +
                   "WHERE e.user.id = :userId " +
                   "AND (:from IS NULL OR e.transactionDate >= :from) " +
                   "AND (:to IS NULL OR e.transactionDate <= :to) " +
                   "AND (:type IS NULL OR e.type = :type) " +
                   "AND (:categoryId IS NULL OR e.category.id = :categoryId) " +
                   "ORDER BY e.transactionDate DESC, e.id DESC",
           countQuery = "SELECT COUNT(e) FROM Expense e " +
                        "WHERE e.user.id = :userId " +
                        "AND (:from IS NULL OR e.transactionDate >= :from) " +
                        "AND (:to IS NULL OR e.transactionDate <= :to) " +
                        "AND (:type IS NULL OR e.type = :type) " +
                        "AND (:categoryId IS NULL OR e.category.id = :categoryId)")
    Page<Expense> findByUserIdAndFilter(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("type") ExpenseType type,
                                        @Param("categoryId") Long categoryId,
                                        Pageable pageable);

    boolean existsByCategoryId(Long categoryId);

    @Query("SELECT new com.lifelog.domain.expense.ExpenseMonthlyTotal(" +
           "YEAR(e.transactionDate), MONTH(e.transactionDate), e.type, SUM(e.amount)) " +
           "FROM Expense e " +
           "WHERE e.user.id = :userId AND e.transactionDate BETWEEN :from AND :to " +
           "GROUP BY YEAR(e.transactionDate), MONTH(e.transactionDate), e.type")
    List<ExpenseMonthlyTotal> sumMonthlyByUserId(@Param("userId") Long userId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    @Query("SELECT new com.lifelog.domain.expense.ExpenseCategoryTotal(" +
           "c.id, c.name, SUM(e.amount), COUNT(e)) " +
           "FROM Expense e JOIN e.category c " +
           "WHERE e.user.id = :userId AND e.type = :type " +
           "AND e.transactionDate BETWEEN :from AND :to " +
           "GROUP BY c.id, c.name " +
           "ORDER BY SUM(e.amount) DESC, c.id ASC")
    List<ExpenseCategoryTotal> sumByCategory(@Param("userId") Long userId,
                                             @Param("type") ExpenseType type,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);
}
