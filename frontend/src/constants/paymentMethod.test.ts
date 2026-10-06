import { describe, expect, it } from 'vitest'
import { PAYMENT_METHOD_LABELS, PAYMENT_METHOD_OPTIONS, type PaymentMethod } from './paymentMethod'

describe('PAYMENT_METHOD_LABELS', () => {
  it.each([
    ['CREDIT_CARD', '신용카드'],
    ['DEBIT_CARD', '체크카드'],
    ['CASH', '현금'],
    ['BANK_TRANSFER', '계좌이체'],
    ['EASY_PAY', '간편결제'],
    ['OTHER', '기타'],
  ] satisfies [PaymentMethod, string][])('%s 결제수단은 "%s" 라벨을 갖는다', (method, label) => {
    expect(PAYMENT_METHOD_LABELS[method]).toBe(label)
  })

  it('LABELS 는 백엔드 enum 과 같은 6개 키만 갖는다', () => {
    expect(Object.keys(PAYMENT_METHOD_LABELS)).toHaveLength(6)
  })

  it('OPTIONS 목록은 LABELS 와 동일한 6개 항목을 백엔드 enum 선언 순서로 담는다', () => {
    expect(PAYMENT_METHOD_OPTIONS).toEqual([
      ['CREDIT_CARD', '신용카드'],
      ['DEBIT_CARD', '체크카드'],
      ['CASH', '현금'],
      ['BANK_TRANSFER', '계좌이체'],
      ['EASY_PAY', '간편결제'],
      ['OTHER', '기타'],
    ])
  })
})
