package com.lifelog.expense;

import com.lifelog.common.exception.BusinessException;
import com.lifelog.domain.expense.ExpenseCategory;
import com.lifelog.domain.expense.ExpenseCategoryRepository;
import com.lifelog.domain.expense.ExpenseRepository;
import com.lifelog.domain.expense.ExpenseType;
import com.lifelog.domain.user.User;
import com.lifelog.domain.user.UserRepository;
import com.lifelog.expense.dto.ExpenseCategoryCreateRequest;
import com.lifelog.expense.dto.ExpenseCategoryResponse;
import com.lifelog.expense.dto.ExpenseCategoryUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    static final int MAX_CATEGORIES_PER_USER = 100;

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> list(Long userId, ExpenseType type) {
        return expenseCategoryRepository.findAllByUserIdAndType(userId, type).stream()
                .map(ExpenseCategoryResponse::from)
                .toList();
    }

    @Transactional
    public ExpenseCategoryResponse create(Long userId, ExpenseCategoryCreateRequest request) {
        User user = getUser(userId);
        String name = request.name().trim();
        if (expenseCategoryRepository.countByUserId(userId) >= MAX_CATEGORIES_PER_USER) {
            throw BusinessException.conflict("카테고리는 최대 " + MAX_CATEGORIES_PER_USER + "개까지 생성할 수 있습니다.");
        }
        if (expenseCategoryRepository.existsByUserIdAndTypeAndName(userId, request.type(), name)) {
            throw BusinessException.conflict("이미 존재하는 카테고리입니다.");
        }
        ExpenseCategory saved = expenseCategoryRepository.save(ExpenseCategory.create(user, request.type(), name));
        log.info("Expense category created: id={}, userId={}", saved.getId(), userId);
        return ExpenseCategoryResponse.from(saved);
    }

    @Transactional
    public ExpenseCategoryResponse update(Long userId, Long id, ExpenseCategoryUpdateRequest request) {
        ExpenseCategory category = getOwnedCategory(id, userId);
        String name = request.name().trim();
        if (!name.equals(category.getName())
                && expenseCategoryRepository.existsByUserIdAndTypeAndName(userId, category.getType(), name)) {
            throw BusinessException.conflict("이미 존재하는 카테고리입니다.");
        }
        category.rename(name);
        return ExpenseCategoryResponse.from(expenseCategoryRepository.save(category));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ExpenseCategory category = getOwnedCategory(id, userId);
        if (expenseRepository.existsByCategoryId(id)) {
            throw BusinessException.conflict("해당 카테고리를 사용하는 내역이 있어 삭제할 수 없습니다.");
        }
        expenseCategoryRepository.delete(category);
        log.info("Expense category deleted: id={}, userId={}", id, userId);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("사용자를 찾을 수 없습니다."));
    }

    private ExpenseCategory getOwnedCategory(Long id, Long userId) {
        ExpenseCategory category = expenseCategoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("카테고리를 찾을 수 없습니다."));
        if (!category.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("본인의 카테고리만 접근할 수 있습니다.");
        }
        return category;
    }
}
