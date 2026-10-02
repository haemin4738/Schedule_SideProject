package com.lifelog.expense;

import com.lifelog.domain.expense.ExpenseType;

import java.util.List;

/**
 * 회원에게 만들어 주는 기본 가계부 카테고리 (사용자 결정 2026-10-02 — 이전의 "기본 카테고리 없음" 결정을 대체).
 * 만든 뒤에는 일반 카테고리와 같아서 이름을 바꾸거나 지울 수 있다.
 */
final class DefaultExpenseCategories {

    record Entry(ExpenseType type, String name) {}

    static final List<Entry> ALL = List.of(
            new Entry(ExpenseType.EXPENSE, "식비"),
            new Entry(ExpenseType.EXPENSE, "카페·간식"),
            new Entry(ExpenseType.EXPENSE, "교통비"),
            new Entry(ExpenseType.EXPENSE, "주거·관리비"),
            new Entry(ExpenseType.EXPENSE, "통신비"),
            new Entry(ExpenseType.EXPENSE, "생활용품"),
            new Entry(ExpenseType.EXPENSE, "의류"),
            new Entry(ExpenseType.EXPENSE, "미용"),
            new Entry(ExpenseType.EXPENSE, "건강·의료"),
            new Entry(ExpenseType.EXPENSE, "취미·여가"),
            new Entry(ExpenseType.EXPENSE, "교육"),
            new Entry(ExpenseType.EXPENSE, "경조사·선물"),
            new Entry(ExpenseType.EXPENSE, "기타"),
            new Entry(ExpenseType.INCOME, "급여"),
            new Entry(ExpenseType.INCOME, "용돈"),
            new Entry(ExpenseType.INCOME, "부수입"),
            new Entry(ExpenseType.INCOME, "금융수입"),
            new Entry(ExpenseType.INCOME, "기타")
    );

    private DefaultExpenseCategories() {
    }
}
