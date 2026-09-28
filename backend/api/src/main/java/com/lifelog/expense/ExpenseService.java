package com.lifelog.expense;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.Expense;
import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.expense.dto.ExpenseRequest;
import com.lifelog.expense.dto.ExpenseResponse;
import com.lifelog.expense.dto.ExpenseSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseRequest request) {
        User user = getUser(userId);
        ExpenseCategory category = getOwnedCategory(request.categoryId(), userId);
        validateTypeMatches(category, request.type());
        Expense expense = Expense.create(user, category, request.type(), request.amount(),
                request.transactionDate(), request.description(), request.memo());
        Expense saved = expenseRepository.save(expense);
        log.info("Expense created: id={}, userId={}", saved.getId(), userId);
        return ExpenseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseSummary> list(Long userId, LocalDate from, LocalDate to,
                                     ExpenseType type, Long categoryId, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        return expenseRepository.findByUserIdAndFilter(userId, from, to, type, categoryId, pageable)
                .map(ExpenseSummary::from);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long userId, Long id) {
        return ExpenseResponse.from(getOwnedExpense(id, userId));
    }

    @Transactional
    public ExpenseResponse update(Long userId, Long id, ExpenseRequest request) {
        Expense expense = getOwnedExpense(id, userId);
        ExpenseCategory category = getOwnedCategory(request.categoryId(), userId);
        validateTypeMatches(category, request.type());
        expense.update(category, request.type(), request.amount(), request.transactionDate(),
                request.description(), request.memo());
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Expense expense = getOwnedExpense(id, userId);
        expenseRepository.delete(expense);
        log.info("Expense deleted: id={}, userId={}", id, userId);
    }

    private void validateTypeMatches(ExpenseCategory category, ExpenseType type) {
        if (category.getType() != type) {
            throw BusinessException.badRequest("카테고리 유형과 내역 유형이 일치하지 않습니다.");
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("사용자를 찾을 수 없습니다."));
    }

    private ExpenseCategory getOwnedCategory(Long id, Long userId) {
        ExpenseCategory category = expenseCategoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("카테고리를 찾을 수 없습니다."));
        if (!category.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("본인의 카테고리만 사용할 수 있습니다.");
        }
        return category;
    }

    private Expense getOwnedExpense(Long id, Long userId) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("내역을 찾을 수 없습니다."));
        if (!expense.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("본인의 내역만 접근할 수 있습니다.");
        }
        return expense;
    }
}
