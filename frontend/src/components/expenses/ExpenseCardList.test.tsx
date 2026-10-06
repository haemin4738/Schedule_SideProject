import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ExpenseSummary } from '@/api/expenses'
import ExpenseCardList from './ExpenseCardList'
import type { ExpenseListProps } from './ExpenseListTable'

const lunch: ExpenseSummary = {
  id: 10,
  type: 'EXPENSE',
  categoryId: 1,
  categoryName: '식비',
  amount: 12000,
  paymentMethod: 'CREDIT_CARD',
  transactionDate: '2026-09-05',
  description: '점심',
}
const salary: ExpenseSummary = {
  id: 11,
  type: 'INCOME',
  categoryId: 2,
  categoryName: '월급',
  amount: 3000000,
  paymentMethod: null,
  transactionDate: '2026-09-01',
  description: null,
}

const renderList = (items: ExpenseSummary[] = [lunch, salary], over: Partial<ExpenseListProps> = {}) => {
  const props: ExpenseListProps = {
    items,
    meta: { page: 0, size: 20, total: items.length, totalPages: 1 },
    page: 0,
    isLoading: false,
    error: null,
    onEdit: vi.fn(),
    onDelete: vi.fn(),
    onPageChange: vi.fn(),
    ...over,
  }
  render(<ExpenseCardList {...props} />)
  return props
}

const cardButton = (category: string) => screen.getByRole('button', { name: new RegExp(`^\\d+/\\d+ ${category} .* 수정$`) })

describe('ExpenseCardList', () => {
  it('render_withItems_showsDateCategoryPaymentDescriptionAndSignedAmount', () => {
    renderList()

    const cards = within(screen.getByRole('list', { name: '가계부 내역 목록' })).getAllByRole('listitem')
    expect(cards).toHaveLength(2)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    const lunchCard = cards[0]
    expect(within(lunchCard).getByText('식비')).toBeInTheDocument()
    expect(within(lunchCard).getByText('점심')).toBeInTheDocument()
    expect(within(lunchCard).getByText('9/5 · 신용카드')).toBeInTheDocument()
    const lunchAmount = within(lunchCard).getByTestId('card-amount')
    expect(lunchAmount).toHaveTextContent(/^-12,000원$/)
    expect(lunchAmount).toHaveClass('text-red-500', 'whitespace-nowrap', 'text-right')

    const salaryCard = cards[1]
    expect(within(salaryCard).getByText('9/1')).toBeInTheDocument()
    const salaryAmount = within(salaryCard).getByTestId('card-amount')
    expect(salaryAmount).toHaveTextContent(/^\+3,000,000원$/)
    expect(salaryAmount).toHaveClass('text-blue-600')
    // 색 외에 유형을 글자로도 알린다 (버튼 이름)
    expect(within(lunchCard).getByRole('button', { name: '9/5 식비 신용카드 점심 지출 -12,000원 수정' })).toBeInTheDocument()
    expect(within(salaryCard).getByRole('button', { name: '9/1 월급 수입 +3,000,000원 수정' })).toBeInTheDocument()
    // 설명이 없으면 '-' 대신 줄을 생략한다
    expect(within(salaryCard).queryByText('-')).not.toBeInTheDocument()
  })

  it('render_withUnknownPaymentMethod_showsRawValue', () => {
    renderList([{ ...lunch, paymentMethod: 'GIFT_CARD' as never }])

    expect(screen.getByText('9/5 · GIFT_CARD')).toBeInTheDocument()
  })

  it('clickCard_whenTapped_callsOnEditWithItem', async () => {
    const user = userEvent.setup()
    const props = renderList()

    await user.click(cardButton('월급'))

    expect(props.onEdit).toHaveBeenCalledWith(salary)
    expect(props.onDelete).not.toHaveBeenCalled()
  })

  it.each([['{Enter}'], [' ']])('keyDown_whenCardFocusedAndKeyPressed_callsOnEdit (%s)', async (key) => {
    const user = userEvent.setup()
    const props = renderList()

    cardButton('식비').focus()
    await user.keyboard(key)

    expect(props.onEdit).toHaveBeenCalledTimes(1)
    expect(props.onEdit).toHaveBeenCalledWith(lunch)
  })

  it('clickDelete_whenTapped_callsOnDeleteOnlyAndIsNotNestedInCardButton', async () => {
    const user = userEvent.setup()
    const props = renderList()

    const deleteButton = screen.getByRole('button', { name: '9/5 식비 점심 삭제' })
    expect(deleteButton.parentElement?.closest('button')).toBeNull()
    expect(deleteButton).toHaveClass('min-h-11', 'min-w-11')
    await user.click(deleteButton)

    expect(props.onDelete).toHaveBeenCalledWith(lunch)
    expect(props.onEdit).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '9/1 월급 삭제' })).toBeInTheDocument()
  })

  it('render_whenLoading_showsLoadingWithoutEmptyMessage', () => {
    renderList([], { isLoading: true, meta: null })

    expect(screen.getByText('불러오는 중...')).toBeInTheDocument()
    expect(screen.queryByText('내역이 없습니다')).not.toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('render_whenEmpty_showsEmptyMessage', () => {
    renderList([])

    expect(screen.getByText('내역이 없습니다')).toBeInTheDocument()
  })

  it('render_whenError_hidesEmptyMessage', () => {
    renderList([], { error: '가계부 내역을 불러오지 못했습니다.' })

    expect(screen.queryByText('내역이 없습니다')).not.toBeInTheDocument()
  })

  it('pagination_withMultiplePages_showsTouchSizedButtonsAndChangesPage', async () => {
    const user = userEvent.setup()
    const props = renderList([lunch], { meta: { page: 0, size: 20, total: 40, totalPages: 2 } })

    const prev = screen.getByRole('button', { name: '이전' })
    const next = screen.getByRole('button', { name: '다음' })
    expect(prev).toBeDisabled()
    expect(next).toHaveClass('min-h-11', 'min-w-11')
    expect(screen.getByText('1 / 2')).toBeInTheDocument()

    await user.click(next)

    expect(props.onPageChange).toHaveBeenCalledWith(1)
  })

  it('pagination_withSinglePage_isHidden', () => {
    renderList()

    expect(screen.queryByRole('button', { name: '다음' })).not.toBeInTheDocument()
  })
})
