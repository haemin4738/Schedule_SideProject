package com.lifelog.domain.expense;

/**
 * 지출 결제수단. DB 에는 이름 문자열(VARCHAR(20))로 저장되므로 값 추가만 허용하고 이름 변경·삭제는 금지한다.
 */
public enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, CASH, BANK_TRANSFER, EASY_PAY, OTHER
}
