package com.lifelog.expense;

import java.lang.reflect.Field;

/**
 * expense 단위 테스트 공용 헬퍼. @GeneratedValue id를 리플렉션으로 세팅한다.
 */
final class ExpenseTestFixtures {

    private ExpenseTestFixtures() {
    }

    static <T> T withId(T entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
