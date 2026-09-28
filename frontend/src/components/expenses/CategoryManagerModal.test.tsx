import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CategoryManagerModal from './CategoryManagerModal'
import {
  createExpenseCategory,
  deleteExpenseCategory,
  updateExpenseCategory,
  type ExpenseCategory,
} from '@/api/expenses'

vi.mock('@/api/expenses', () => ({
  createExpenseCategory: vi.fn(),
  updateExpenseCategory: vi.fn(),
  deleteExpenseCategory: vi.fn(),
}))

const mockedCreate = vi.mocked(createExpenseCategory)
const mockedUpdate = vi.mocked(updateExpenseCategory)
const mockedDelete = vi.mocked(deleteExpenseCategory)

const categories: ExpenseCategory[] = [
  { id: 1, type: 'EXPENSE', name: '식비', createdAt: '', updatedAt: '' },
  { id: 2, type: 'INCOME', name: '월급', createdAt: '', updatedAt: '' },
]

function serverError(status: number, message: string) {
  return Object.assign(new Error(`status ${status}`), {
    response: { status, data: { success: false, data: null, error: message } },
  })
}

function renderModal(onChanged = vi.fn().mockResolvedValue(undefined), onClose = vi.fn()) {
  render(<CategoryManagerModal categories={categories} onChanged={onChanged} onClose={onClose} />)
  return { onChanged, onClose }
}

describe('CategoryManagerModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('render_byTab_showsOnlyCategoriesOfSelectedType', async () => {
    const user = userEvent.setup()
    renderModal()

    expect(screen.getByText('식비')).toBeInTheDocument()
    expect(screen.queryByText('월급')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '수입' }))

    expect(screen.getByText('월급')).toBeInTheDocument()
    expect(screen.queryByText('식비')).not.toBeInTheDocument()
  })

  it('add_withName_createsCategoryOfCurrentTabAndNotifiesParent', async () => {
    const user = userEvent.setup()
    mockedCreate.mockResolvedValue({} as never)
    const { onChanged } = renderModal()

    await user.click(screen.getByRole('button', { name: '수입' }))
    await user.type(screen.getByPlaceholderText('새 카테고리 이름'), '  용돈  ')
    await user.click(screen.getByRole('button', { name: '추가' }))

    await waitFor(() => {
      expect(mockedCreate).toHaveBeenCalledWith({ type: 'INCOME', name: '용돈' })
    })
    expect(onChanged).toHaveBeenCalled()
    expect(screen.getByPlaceholderText('새 카테고리 이름')).toHaveValue('')
  })

  it('add_whenNameBlank_showsValidationErrorAndDoesNotCallApi', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.type(screen.getByPlaceholderText('새 카테고리 이름'), '   ')
    await user.click(screen.getByRole('button', { name: '추가' }))

    expect(screen.getByText('카테고리 이름을 입력하세요.')).toBeInTheDocument()
    expect(mockedCreate).not.toHaveBeenCalled()
  })

  it.each([
    ['중복 이름', '이미 존재하는 카테고리 이름입니다.'],
    ['100개 초과', '카테고리는 최대 100개까지 만들 수 있습니다.'],
  ])('add_when409%s_showsServerMessage', async (_, message) => {
    const user = userEvent.setup()
    mockedCreate.mockRejectedValue(serverError(409, message))
    const { onChanged } = renderModal()

    await user.type(screen.getByPlaceholderText('새 카테고리 이름'), '식비')
    await user.click(screen.getByRole('button', { name: '추가' }))

    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('rename_withNewName_callsUpdateWithTrimmedName', async () => {
    const user = userEvent.setup()
    mockedUpdate.mockResolvedValue({} as never)
    const { onChanged } = renderModal()

    await user.click(screen.getByRole('button', { name: '이름 변경' }))
    const input = screen.getByLabelText('카테고리 이름')
    await user.clear(input)
    await user.type(input, ' 외식 ')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => {
      expect(mockedUpdate).toHaveBeenCalledWith(1, { name: '외식' })
    })
    expect(onChanged).toHaveBeenCalled()
    await waitFor(() => {
      expect(screen.queryByLabelText('카테고리 이름')).not.toBeInTheDocument()
    })
  })

  it('delete_whenConfirmed_deletesAndNotifiesParent', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockedDelete.mockResolvedValue({} as never)
    const { onChanged } = renderModal()

    const row = screen.getByText('식비').closest('li') as HTMLElement
    await user.click(within(row).getByRole('button', { name: '삭제' }))

    await waitFor(() => {
      expect(mockedDelete).toHaveBeenCalledWith(1)
    })
    expect(onChanged).toHaveBeenCalled()
  })

  it('delete_when409InUse_showsServerMessage', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mockedDelete.mockRejectedValue(
      serverError(409, '해당 카테고리를 사용하는 내역이 있어 삭제할 수 없습니다.'),
    )
    const { onChanged } = renderModal()

    await user.click(screen.getByRole('button', { name: '삭제' }))

    expect(
      await screen.findByText('해당 카테고리를 사용하는 내역이 있어 삭제할 수 없습니다.'),
    ).toBeInTheDocument()
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('delete_whenCancelled_doesNotCallApi', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderModal()

    await user.click(screen.getByRole('button', { name: '삭제' }))

    expect(mockedDelete).not.toHaveBeenCalled()
  })

  it('close_whenClicked_callsOnClose', async () => {
    const user = userEvent.setup()
    const { onClose } = renderModal()

    await user.click(screen.getByRole('button', { name: '닫기' }))

    expect(onClose).toHaveBeenCalled()
  })
})
