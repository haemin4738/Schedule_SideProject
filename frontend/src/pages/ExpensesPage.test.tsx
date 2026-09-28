import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import dayjs from 'dayjs'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExpensesPage from './ExpensesPage'
import {
  createExpense,
  deleteExpense,
  getCategorySummary,
  getExpense,
  getExpenseCategories,
  getExpenses,
  getMonthlySummary,
  updateExpense,
  type ExpenseCategory,
  type ExpenseSummary,
} from '@/api/expenses'

vi.mock('@/api/expenses', () => ({
  getExpenses: vi.fn(),
  getExpense: vi.fn(),
  createExpense: vi.fn(),
  updateExpense: vi.fn(),
  deleteExpense: vi.fn(),
  getExpenseCategories: vi.fn(),
  createExpenseCategory: vi.fn(),
  updateExpenseCategory: vi.fn(),
  deleteExpenseCategory: vi.fn(),
  getMonthlySummary: vi.fn(),
  getCategorySummary: vi.fn(),
}))

const mockedGetExpenses = vi.mocked(getExpenses)
const mockedGetExpense = vi.mocked(getExpense)
const mockedCreateExpense = vi.mocked(createExpense)
const mockedUpdateExpense = vi.mocked(updateExpense)
const mockedDeleteExpense = vi.mocked(deleteExpense)
const mockedGetExpenseCategories = vi.mocked(getExpenseCategories)
const mockedGetMonthlySummary = vi.mocked(getMonthlySummary)
const mockedGetCategorySummary = vi.mocked(getCategorySummary)

const thisMonth = dayjs().startOf('month')
const FROM = thisMonth.format('YYYY-MM-DD')
const TO = thisMonth.endOf('month').format('YYYY-MM-DD')

const foodCategory: ExpenseCategory = {
  id: 1,
  type: 'EXPENSE',
  name: '식비',
  createdAt: '',
  updatedAt: '',
}
const salaryCategory: ExpenseCategory = {
  id: 2,
  type: 'INCOME',
  name: '월급',
  createdAt: '',
  updatedAt: '',
}

const lunch: ExpenseSummary = {
  id: 10,
  type: 'EXPENSE',
  categoryId: 1,
  categoryName: '식비',
  amount: 12000,
  transactionDate: thisMonth.add(4, 'day').format('YYYY-MM-DD'),
  description: '점심',
}
const salary: ExpenseSummary = {
  id: 11,
  type: 'INCOME',
  categoryId: 2,
  categoryName: '월급',
  amount: 3000000,
  transactionDate: thisMonth.format('YYYY-MM-DD'),
  description: null,
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ExpensesPage />
    </MemoryRouter>,
  )
}

function mockListResponse(
  items: ExpenseSummary[],
  meta = { page: 0, size: 20, total: items.length, totalPages: 1 },
) {
  mockedGetExpenses.mockResolvedValue({ data: { success: true, data: items, meta } } as never)
}

function mockCategories(categories: ExpenseCategory[]) {
  mockedGetExpenseCategories.mockResolvedValue({
    data: { success: true, data: categories },
  } as never)
}

function mockSummaries({
  totalIncome = 0,
  totalExpense = 0,
  categories = [] as { categoryId: number; categoryName: string; amount: number; count: number }[],
} = {}) {
  mockedGetMonthlySummary.mockResolvedValue({
    data: {
      success: true,
      data: {
        from: '',
        to: '',
        totalIncome,
        totalExpense,
        net: totalIncome - totalExpense,
        months: [],
      },
    },
  } as never)
  mockedGetCategorySummary.mockResolvedValue({
    data: {
      success: true,
      data: {
        type: 'EXPENSE',
        from: FROM,
        to: TO,
        total: categories.reduce((sum, c) => sum + c.amount, 0),
        categories,
      },
    },
  } as never)
}

async function openCreateFormWithCategories(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: '내역 추가' }))
  const dialog = screen.getByRole('dialog', { name: '내역 추가' })
  await waitFor(() => {
    expect(within(dialog).getByRole('option', { name: '식비' })).toBeInTheDocument()
  })
  return dialog
}

function serverError(status: number, message: string) {
  return Object.assign(new Error(`status ${status}`), {
    response: { status, data: { success: false, data: null, error: message } },
  })
}

describe('ExpensesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockCategories([foodCategory, salaryCategory])
    mockSummaries()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('렌더링', () => {
    it('render_whenLoading_showsLoadingTextOnly', () => {
      mockedGetExpenses.mockReturnValue(new Promise(() => {}) as never)
      renderPage()

      expect(screen.getByText('불러오는 중...')).toBeInTheDocument()
      expect(screen.queryByText('내역이 없습니다')).not.toBeInTheDocument()
    })

    it('render_withData_showsRowsWithDateCategoryDescriptionAndSignedAmount', async () => {
      mockListResponse([lunch, salary])
      renderPage()

      const lunchRow = (await screen.findByText('점심')).closest('tr') as HTMLElement
      expect(within(lunchRow).getByText(dayjs(lunch.transactionDate).format('M/D'))).toBeInTheDocument()
      expect(within(lunchRow).getByText('식비')).toBeInTheDocument()
      expect(within(lunchRow).getByText('-12,000원')).toBeInTheDocument()

      const salaryRow = screen.getByText('+3,000,000원').closest('tr') as HTMLElement
      expect(within(salaryRow).getByText('월급')).toBeInTheDocument()
      expect(within(salaryRow).getByText('-')).toBeInTheDocument()
    })

    it('render_onMount_requestsSelectedMonthRangeWithPageSize20', async () => {
      mockListResponse([])
      renderPage()

      await screen.findByText('내역이 없습니다')
      expect(screen.getByText(thisMonth.format('YYYY년 M월'))).toBeInTheDocument()
      expect(mockedGetExpenses).toHaveBeenCalledWith({
        from: FROM,
        to: TO,
        type: undefined,
        categoryId: undefined,
        page: 0,
        size: 20,
      })
      expect(mockedGetMonthlySummary).toHaveBeenCalledWith({
        from: thisMonth.format('YYYY-MM'),
        to: thisMonth.format('YYYY-MM'),
      })
      expect(mockedGetCategorySummary).toHaveBeenCalledWith({ type: 'EXPENSE', from: FROM, to: TO })
    })

    it('render_whenEmpty_showsEmptyMessages', async () => {
      mockListResponse([])
      renderPage()

      expect(await screen.findByText('내역이 없습니다')).toBeInTheDocument()
      expect(screen.getByText('지출 내역이 없습니다')).toBeInTheDocument()
      expect(screen.queryByText('불러오는 중...')).not.toBeInTheDocument()
    })

    it('render_whenListFails_showsErrorMessage', async () => {
      mockedGetExpenses.mockRejectedValue(new Error('network error'))
      renderPage()

      expect(await screen.findByText('가계부 내역을 불러오지 못했습니다.')).toBeInTheDocument()
      expect(screen.queryByText('내역이 없습니다')).not.toBeInTheDocument()
    })

    it('render_whenSummaryFails_showsSummaryError', async () => {
      mockListResponse([])
      mockedGetMonthlySummary.mockRejectedValue(new Error('network error'))
      renderPage()

      expect(await screen.findByText('요약 정보를 불러오지 못했습니다.')).toBeInTheDocument()
    })

    it('render_withSummary_showsIncomeExpenseNetAndNegativeNetInRed', async () => {
      mockListResponse([])
      mockSummaries({ totalIncome: 10000, totalExpense: 25000 })
      renderPage()

      await waitFor(() => {
        expect(screen.getByTestId('summary-income')).toHaveTextContent('10,000원')
      })
      expect(screen.getByTestId('summary-expense')).toHaveTextContent('25,000원')
      expect(screen.getByTestId('summary-net')).toHaveTextContent('-15,000원')
      expect(screen.getByTestId('summary-net')).toHaveClass('text-red-500')
    })

    it('render_withCategorySummary_showsAmountCountAndRatio', async () => {
      mockListResponse([])
      mockSummaries({
        categories: [
          { categoryId: 1, categoryName: '식비', amount: 30000, count: 3 },
          { categoryId: 3, categoryName: '교통', amount: 10000, count: 1 },
        ],
      })
      renderPage()

      expect(await screen.findByText('(3건)')).toBeInTheDocument()
      expect(screen.getByText('75.0%')).toBeInTheDocument()
      expect(screen.getByText('25.0%')).toBeInTheDocument()
      expect(screen.queryByText('지출 내역이 없습니다')).not.toBeInTheDocument()
    })
  })

  describe('필터/월 이동/페이지', () => {
    it('moveMonth_whenPrevClicked_reloadsPreviousMonthFromPage0', async () => {
      const user = userEvent.setup()
      mockListResponse([lunch], { page: 0, size: 20, total: 40, totalPages: 2 })
      renderPage()
      await screen.findByText('점심')

      await user.click(screen.getByRole('button', { name: '다음' }))
      await waitFor(() => {
        expect(mockedGetExpenses).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
      })

      await user.click(screen.getByRole('button', { name: '이전 달' }))

      const prev = thisMonth.subtract(1, 'month')
      await waitFor(() => {
        expect(mockedGetExpenses).toHaveBeenLastCalledWith({
          from: prev.format('YYYY-MM-DD'),
          to: prev.endOf('month').format('YYYY-MM-DD'),
          type: undefined,
          categoryId: undefined,
          page: 0,
          size: 20,
        })
      })
      expect(screen.getByText(prev.format('YYYY년 M월'))).toBeInTheDocument()
      expect(mockedGetMonthlySummary).toHaveBeenLastCalledWith({
        from: prev.format('YYYY-MM'),
        to: prev.format('YYYY-MM'),
      })
    })

    it('changeType_whenTypeChanged_resetsCategoryFilterAndShowsOnlyThatTypeCategories', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      renderPage()
      await screen.findByText('내역이 없습니다')

      const categorySelect = screen.getByLabelText('카테고리 필터')
      await waitFor(() => {
        expect(within(categorySelect).getByRole('option', { name: '식비' })).toBeInTheDocument()
      })
      await user.selectOptions(categorySelect, '1')
      await waitFor(() => {
        expect(mockedGetExpenses).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 1 }))
      })

      await user.click(screen.getByRole('button', { name: '수입' }))

      await waitFor(() => {
        expect(mockedGetExpenses).toHaveBeenLastCalledWith(
          expect.objectContaining({ type: 'INCOME', categoryId: undefined, page: 0 }),
        )
      })
      expect(categorySelect).toHaveValue('')
      expect(within(categorySelect).queryByRole('option', { name: '식비' })).not.toBeInTheDocument()
      expect(within(categorySelect).getByRole('option', { name: '월급' })).toBeInTheDocument()
    })
  })

  describe('내역 폼', () => {
    it('create_withValidInput_callsCreateExpenseAndReloads', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      mockedCreateExpense.mockResolvedValue({ data: { success: true, data: {} } } as never)
      renderPage()
      await screen.findByText('내역이 없습니다')

      await user.click(screen.getByRole('button', { name: '내역 추가' }))
      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      expect(within(dialog).getByLabelText('날짜')).toHaveValue(dayjs().format('YYYY-MM-DD'))

      await user.selectOptions(within(dialog).getByLabelText('카테고리'), '1')
      await user.type(within(dialog).getByPlaceholderText('금액 (원)'), '12000')
      await user.type(within(dialog).getByPlaceholderText('설명'), '점심')
      await user.type(within(dialog).getByPlaceholderText('메모'), '회사 근처')
      const listCalls = mockedGetExpenses.mock.calls.length
      await user.click(within(dialog).getByRole('button', { name: '저장' }))

      await waitFor(() => {
        expect(mockedCreateExpense).toHaveBeenCalledWith({
          type: 'EXPENSE',
          categoryId: 1,
          amount: 12000,
          transactionDate: dayjs().format('YYYY-MM-DD'),
          description: '점심',
          memo: '회사 근처',
        })
      })
      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      })
      expect(mockedGetExpenses.mock.calls.length).toBeGreaterThan(listCalls)
    })

    it('create_whenNotCurrentMonth_defaultsDateToFirstDayOfSelectedMonth', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      renderPage()
      await screen.findByText('내역이 없습니다')

      await user.click(screen.getByRole('button', { name: '이전 달' }))
      await user.click(screen.getByRole('button', { name: '내역 추가' }))

      expect(screen.getByLabelText('날짜')).toHaveValue(
        thisMonth.subtract(1, 'month').format('YYYY-MM-DD'),
      )
    })

    it('submit_withoutCategoryAndAmount_showsValidationErrorsAndDoesNotCallApi', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      renderPage()
      await screen.findByText('내역이 없습니다')

      await user.click(screen.getByRole('button', { name: '내역 추가' }))
      await user.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByText('카테고리를 선택하세요.')).toBeInTheDocument()
      expect(screen.getByText('금액은 필수입니다.')).toBeInTheDocument()
      expect(mockedCreateExpense).not.toHaveBeenCalled()
    })

    it.each([
      ['0', '금액은 1원 이상 99,999,999,999원 이하여야 합니다.'],
      ['100000000000', '금액은 1원 이상 99,999,999,999원 이하여야 합니다.'],
      ['12a', '금액은 숫자만 입력하세요.'],
      ['-500', '금액은 숫자만 입력하세요.'],
    ])('submit_whenAmountIs%s_showsAmountError', async (amount, message) => {
      const user = userEvent.setup()
      mockListResponse([])
      renderPage()
      await screen.findByText('내역이 없습니다')

      await openCreateFormWithCategories(user)
      await user.selectOptions(screen.getByLabelText('카테고리'), '1')
      await user.type(screen.getByPlaceholderText('금액 (원)'), amount)
      await user.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(mockedCreateExpense).not.toHaveBeenCalled()
    })

    it('submit_whenDescriptionTooLong_showsLengthError', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      renderPage()
      await screen.findByText('내역이 없습니다')

      await openCreateFormWithCategories(user)
      await user.selectOptions(screen.getByLabelText('카테고리'), '1')
      await user.type(screen.getByPlaceholderText('금액 (원)'), '1000')
      await user.click(screen.getByPlaceholderText('설명'))
      await user.paste('가'.repeat(201))
      await user.click(screen.getByRole('button', { name: '저장' }))

      expect(await screen.findByText('설명은 200자 이하로 입력하세요.')).toBeInTheDocument()
      expect(mockedCreateExpense).not.toHaveBeenCalled()
    })

    it('submit_whenServerRejects_showsServerMessageInForm', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      mockedCreateExpense.mockRejectedValue(
        serverError(400, '카테고리 유형이 내역 유형과 일치하지 않습니다.'),
      )
      renderPage()
      await screen.findByText('내역이 없습니다')

      await openCreateFormWithCategories(user)
      await user.selectOptions(screen.getByLabelText('카테고리'), '1')
      await user.type(screen.getByPlaceholderText('금액 (원)'), '1000')
      await user.click(screen.getByRole('button', { name: '저장' }))

      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      expect(
        await within(dialog).findByText('카테고리 유형이 내역 유형과 일치하지 않습니다.'),
      ).toBeInTheDocument()
    })

    it('changeFormType_whenSwitchedToIncome_clearsCategoryAndShowsIncomeCategories', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      renderPage()
      await screen.findByText('내역이 없습니다')

      await user.click(screen.getByRole('button', { name: '내역 추가' }))
      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      await waitFor(() => {
        expect(within(dialog).getByRole('option', { name: '식비' })).toBeInTheDocument()
      })
      await user.selectOptions(within(dialog).getByLabelText('카테고리'), '1')

      await user.click(within(dialog).getByRole('button', { name: '수입' }))

      expect(within(dialog).getByLabelText('카테고리')).toHaveValue('')
      expect(within(dialog).queryByRole('option', { name: '식비' })).not.toBeInTheDocument()
      expect(within(dialog).getByRole('option', { name: '월급' })).toBeInTheDocument()
    })

    it('form_whenNoCategoriesForType_disablesSaveAndLinksToCategoryManager', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      mockCategories([salaryCategory]) // 지출 카테고리 0개
      renderPage()
      await screen.findByText('내역이 없습니다')

      await user.click(screen.getByRole('button', { name: '내역 추가' }))
      const dialog = screen.getByRole('dialog', { name: '내역 추가' })

      expect(await within(dialog).findByText('먼저 카테고리를 추가하세요.')).toBeInTheDocument()
      expect(within(dialog).getByRole('button', { name: '저장' })).toBeDisabled()

      await user.click(within(dialog).getByRole('button', { name: '카테고리 관리로 이동' }))

      expect(screen.queryByRole('dialog', { name: '내역 추가' })).not.toBeInTheDocument()
      expect(screen.getByRole('dialog', { name: '카테고리 관리' })).toBeInTheDocument()
    })

    it('edit_whenRowClicked_fetchesDetailAndFillsMemoThenPreservesItOnSave', async () => {
      const user = userEvent.setup()
      mockListResponse([lunch])
      mockedGetExpense.mockResolvedValue({
        data: {
          success: true,
          data: { ...lunch, memo: '기존 메모', createdAt: '', updatedAt: '' },
        },
      } as never)
      mockedUpdateExpense.mockResolvedValue({ data: { success: true, data: {} } } as never)
      renderPage()

      await user.click(await screen.findByText('점심'))

      expect(mockedGetExpense).toHaveBeenCalledWith(10)
      const dialog = await screen.findByRole('dialog', { name: '내역 수정' })
      expect(within(dialog).getByPlaceholderText('메모')).toHaveValue('기존 메모')
      expect(within(dialog).getByPlaceholderText('금액 (원)')).toHaveValue('12000')
      expect(within(dialog).getByLabelText('카테고리')).toHaveValue('1')

      await user.click(within(dialog).getByRole('button', { name: '저장' }))

      await waitFor(() => {
        expect(mockedUpdateExpense).toHaveBeenCalledWith(10, {
          type: 'EXPENSE',
          categoryId: 1,
          amount: 12000,
          transactionDate: lunch.transactionDate,
          description: '점심',
          memo: '기존 메모',
        })
      })
    })

    it('edit_whenRowFocusedAndEnterPressed_opensEditForm', async () => {
      const user = userEvent.setup()
      mockListResponse([lunch])
      mockedGetExpense.mockResolvedValue({
        data: { success: true, data: { ...lunch, memo: null, createdAt: '', updatedAt: '' } },
      } as never)
      renderPage()

      const row = (await screen.findByText('점심')).closest('tr') as HTMLElement
      row.focus()
      await user.keyboard('{Enter}')

      expect(mockedGetExpense).toHaveBeenCalledWith(10)
      expect(await screen.findByRole('dialog', { name: '내역 수정' })).toBeInTheDocument()
    })

    it('form_whenEscapePressed_closesDialogAndRestoresFocus', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      renderPage()
      await screen.findByText('내역이 없습니다')

      await user.click(screen.getByRole('button', { name: '내역 추가' }))
      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      expect(dialog).toHaveAttribute('aria-modal', 'true')
      expect(dialog).toHaveFocus()

      await user.keyboard('{Escape}')

      expect(screen.queryByRole('dialog', { name: '내역 추가' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: '내역 추가' })).toHaveFocus()
    })

    it('edit_whenDetailFetchFails_showsErrorAndDoesNotOpenForm', async () => {
      const user = userEvent.setup()
      mockListResponse([lunch])
      mockedGetExpense.mockRejectedValue(serverError(404, '가계부 내역을 찾을 수 없습니다.'))
      renderPage()

      await user.click(await screen.findByText('점심'))

      expect(await screen.findByText('가계부 내역을 찾을 수 없습니다.')).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('삭제', () => {
    it('delete_whenConfirmed_deletesAndReloadsListAndSummaries', async () => {
      const user = userEvent.setup()
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      mockListResponse([lunch])
      mockedDeleteExpense.mockResolvedValue({} as never)
      renderPage()
      await screen.findByText('점심')
      const listCalls = mockedGetExpenses.mock.calls.length
      const summaryCalls = mockedGetMonthlySummary.mock.calls.length

      await user.click(screen.getByRole('button', { name: '삭제' }))

      await waitFor(() => {
        expect(mockedDeleteExpense).toHaveBeenCalledWith(10)
      })
      await waitFor(() => {
        expect(mockedGetExpenses.mock.calls.length).toBeGreaterThan(listCalls)
      })
      expect(mockedGetMonthlySummary.mock.calls.length).toBeGreaterThan(summaryCalls)
      // 삭제 버튼 클릭이 행 클릭(수정)으로 전파되지 않아야 한다
      expect(mockedGetExpense).not.toHaveBeenCalled()
    })

    it('delete_whenCancelled_doesNotCallApi', async () => {
      const user = userEvent.setup()
      vi.spyOn(window, 'confirm').mockReturnValue(false)
      mockListResponse([lunch])
      renderPage()
      await screen.findByText('점심')

      await user.click(screen.getByRole('button', { name: '삭제' }))

      expect(mockedDeleteExpense).not.toHaveBeenCalled()
    })

    it('delete_whenServerRejects_showsServerMessage', async () => {
      const user = userEvent.setup()
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      mockListResponse([lunch])
      mockedDeleteExpense.mockRejectedValue(serverError(403, '접근 권한이 없습니다.'))
      renderPage()
      await screen.findByText('점심')

      await user.click(screen.getByRole('button', { name: '삭제' }))

      expect(await screen.findByText('접근 권한이 없습니다.')).toBeInTheDocument()
    })
  })
})
