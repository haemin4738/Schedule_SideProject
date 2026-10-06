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

// 서버에 새 값이 먼저 추가돼 이 목록에 없으면 값 자체를 보여 준다(빈 칸으로 사라지지 않게).
export function paymentMethodLabel(value: string): string {
  return PAYMENT_METHOD_LABELS[value as PaymentMethod] ?? value
}
