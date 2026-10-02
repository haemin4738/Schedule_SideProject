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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /** 기본 카테고리 중 아직 없는 것만 추가하고 전체 목록을 돌려준다 ("기본 카테고리 추가" 버튼). 여러 번 눌러도 중복되지 않는다 */
    @Transactional
    public List<ExpenseCategoryResponse> addDefaults(Long userId) {
        int added = addDefaults(getUser(userId));
        log.info("Default expense categories added: userId={}, count={}", userId, added);
        return list(userId, null);
    }

    /**
     * 기본 카테고리 중 아직 없는 것만 추가한다 (회원가입 시 호출 — 가입과 같은 트랜잭션).
     * 사용자당 상한({@value #MAX_CATEGORIES_PER_USER})을 넘는 만큼은 건너뛴다.
     *
     * @return 추가한 개수
     */
    @Transactional
    public int addDefaults(User user) {
        Long userId = user.getId();
        List<ExpenseCategory> existing = expenseCategoryRepository.findAllByUserIdAndType(userId, null);
        Set<String> taken = new HashSet<>();
        for (ExpenseCategory category : existing) {
            taken.add(key(category.getType(), category.getName()));
        }
        int room = MAX_CATEGORIES_PER_USER - existing.size();
        int added = 0;
        for (DefaultExpenseCategories.Entry entry : DefaultExpenseCategories.ALL) {
            if (added >= room) {
                break;
            }
            if (taken.add(key(entry.type(), entry.name()))) {
                expenseCategoryRepository.save(ExpenseCategory.create(user, entry.type(), entry.name()));
                added++;
            }
        }
        return added;
    }

    @Transactional
    public ExpenseCategoryResponse update(Long userId, Long id, ExpenseCategoryUpdateRequest request) {
        ExpenseCategory category = getOwnedCategory(id, userId);
        String name = request.name().trim();
        // DB collation(utf8mb4_unicode_ci)이 대소문자/악센트를 구분하지 않으므로, 자기 자신을 제외하고 DB 기준으로 중복을 확인한다
        if (!name.equals(category.getName())
                && expenseCategoryRepository.existsByUserIdAndTypeAndNameAndIdNot(userId, category.getType(), name, id)) {
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

    // DB collation(utf8mb4_unicode_ci)처럼 대소문자를 구분하지 않고 비교한다 — 남는 차이는 유니크 제약이 막는다
    private static String key(ExpenseType type, String name) {
        return type + ":" + name.trim().toLowerCase(java.util.Locale.ROOT);
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
