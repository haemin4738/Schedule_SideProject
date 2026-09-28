// 백엔드 com.lifelog.domain.expense.ExpenseType 및 docs/api-spec.yaml ExpenseType 과 동기화되어야 함.
export type ExpenseType = 'EXPENSE' | 'INCOME'

export const EXPENSE_TYPE_LABELS: Record<ExpenseType, string> = {
  EXPENSE: '지출',
  INCOME: '수입',
}

export const EXPENSE_TYPE_OPTIONS = Object.entries(EXPENSE_TYPE_LABELS) as [ExpenseType, string][]

// 백엔드 ExpenseRequest.amount 범위 (원 단위 정수)
export const MIN_AMOUNT = 1
export const MAX_AMOUNT = 99_999_999_999

const withComma = (value: number) => String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',')

/** 12000 → "12,000원", -5000 → "-5,000원" */
export const formatAmount = (amount: number): string =>
  `${amount < 0 ? '-' : ''}${withComma(Math.abs(amount))}원`

/** 수입은 "+12,000원", 지출은 "-12,000원" */
export const formatSignedAmount = (type: ExpenseType, amount: number): string =>
  `${type === 'INCOME' ? '+' : '-'}${formatAmount(Math.abs(amount))}`
