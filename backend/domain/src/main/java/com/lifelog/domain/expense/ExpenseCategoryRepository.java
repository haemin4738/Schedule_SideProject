package com.lifelog.domain.expense;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository {
    ExpenseCategory save(ExpenseCategory category);
    Optional<ExpenseCategory> findById(Long id);
    /** type이 null이면 전체 유형. type, name 오름차순 정렬. */
    List<ExpenseCategory> findAllByUserIdAndType(Long userId, ExpenseType type);
    boolean existsByUserIdAndTypeAndName(Long userId, ExpenseType type, String name);
    /** 이름 변경 시 자기 자신을 제외하고 중복 확인. 비교는 DB collation 기준. */
    boolean existsByUserIdAndTypeAndNameAndIdNot(Long userId, ExpenseType type, String name, Long id);
    long countByUserId(Long userId);
    void delete(ExpenseCategory category);
}
