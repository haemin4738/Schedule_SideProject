package com.lifelog.domain.expense;

/**
 * 지출 결제수단. DB 에는 이름 문자열(VARCHAR(20))로 저장되므로 값 추가만 허용하고 이름 변경·삭제는 금지한다.
 * 값을 추가하면 Hibernate 가 자동 생성한 로컬 DB(ddl-auto update)의 CHECK 제약은 갱신되지 않으므로
 * 로컬에서는 해당 CHECK 를 삭제해야 한다. 운영 DDL 에는 CHECK 를 만들지 않는다.
 */
public enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, CASH, BANK_TRANSFER, EASY_PAY, OTHER
}
