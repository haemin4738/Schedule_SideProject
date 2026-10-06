// 백엔드 com.lifelog.domain.expense.PaymentMethod 및 docs/api-spec.yaml PaymentMethod 와 동기화되어야 함.
export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'CASH' | 'BANK_TRANSFER' | 'EASY_PAY' | 'OTHER'

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CREDIT_CARD: '신용카드',
  DEBIT_CARD: '체크카드',
  CASH: '현금',
  BANK_TRANSFER: '계좌이체',
  EASY_PAY: '간편결제',
  OTHER: '기타',
}

export const PAYMENT_METHOD_OPTIONS = Object.entries(PAYMENT_METHOD_LABELS) as [PaymentMethod, string][]
