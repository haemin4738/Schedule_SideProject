package com.lifelog.infrastructure.expense;

import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ExpenseCategoryRepositoryImpl implements ExpenseCategoryRepository {

    private final ExpenseCategoryJpaRepository jpa;

    @Override public ExpenseCategory save(ExpenseCategory category) { return jpa.save(category); }
    @Override public Optional<ExpenseCategory> findById(Long id) { return jpa.findById(id); }
    @Override public void delete(ExpenseCategory category) { jpa.delete(category); }

    @Override
    public List<ExpenseCategory> findAllByUserIdAndType(Long userId, ExpenseType type) {
        return jpa.findAllByUserIdAndType(userId, type);
    }

    @Override
    public boolean existsByUserIdAndTypeAndName(Long userId, ExpenseType type, String name) {
        return jpa.existsByUserIdAndTypeAndName(userId, type, name);
    }

    @Override
    public long countByUserId(Long userId) {
        return jpa.countByUserId(userId);
    }
}
