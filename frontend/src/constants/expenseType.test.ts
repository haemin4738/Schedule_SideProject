import { describe, expect, it } from 'vitest'
import {
  EXPENSE_TYPE_LABELS,
  EXPENSE_TYPE_OPTIONS,
  formatAmount,
  formatSignedAmount,
  type ExpenseType,
} from './expenseType'

describe('EXPENSE_TYPE_LABELS', () => {
  it.each([
    ['EXPENSE', '지출'],
    ['INCOME', '수입'],
  ] satisfies [ExpenseType, string][])('%s 유형은 "%s" 라벨을 갖는다', (type, label) => {
    expect(EXPENSE_TYPE_LABELS[type]).toBe(label)
  })

  it('OPTIONS 목록은 LABELS 레코드와 동일한 2개 항목을 지출→수입 순서로 담는다', () => {
    expect(EXPENSE_TYPE_OPTIONS).toEqual([
      ['EXPENSE', '지출'],
      ['INCOME', '수입'],
    ])
  })
})

describe('formatAmount', () => {
  it.each([
    [0, '0원'],
    [1, '1원'],
    [999, '999원'],
    [1000, '1,000원'],
    [12000, '12,000원'],
    [1234567, '1,234,567원'],
    [99999999999, '99,999,999,999원'],
    [-5000, '-5,000원'],
  ])('%d → "%s"', (amount, expected) => {
    expect(formatAmount(amount)).toBe(expected)
  })
})

describe('formatSignedAmount', () => {
  it('수입은 + 부호를 붙인다', () => {
    expect(formatSignedAmount('INCOME', 12000)).toBe('+12,000원')
  })

  it('지출은 - 부호를 붙인다', () => {
    expect(formatSignedAmount('EXPENSE', 12000)).toBe('-12,000원')
  })
})
