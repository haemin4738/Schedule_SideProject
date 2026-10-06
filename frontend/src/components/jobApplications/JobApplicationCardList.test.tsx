import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { JobApplicationSummary } from '@/api/jobApplications'
import JobApplicationCardList from './JobApplicationCardList'
import JobApplicationStatusBadge from './JobApplicationStatusBadge'

const base: JobApplicationSummary = {
  id: 1,
  companyName: '테스트회사',
  position: '백엔드 개발자',
  status: 'INTERVIEW_SCHEDULED',
  appliedAt: '2026-09-01',
}

const second: JobApplicationSummary = {
  id: 2,
  companyName: '다른회사',
  position: '프론트엔드 개발자',
  status: 'OFFER',
  appliedAt: '2026-09-15',
}

type Props = Parameters<typeof JobApplicationCardList>[0]

const renderList = (items: JobApplicationSummary[] = [base], over: Partial<Props> = {}) => {
  const props: Props = {
    items,
    isLoading: false,
    hasError: false,
    editingId: null,
    onEdit: vi.fn(),
    onDelete: vi.fn(),
    ...over,
  }
  render(<JobApplicationCardList {...props} />)
  return props
}

const cardButton = (companyName: string) =>
  screen.getByRole('button', { name: new RegExp(`^${companyName}.*지원일`) })

describe('JobApplicationCardList', () => {
  it('render_withItems_showsSameFieldsAsTable', () => {
    renderList([base, second])

    const list = screen.getByRole('list', { name: '지원 내역 목록' })
    const cards = within(list).getAllByRole('listitem')
    expect(cards).toHaveLength(2)
    expect(within(cards[0]).getByText('테스트회사')).toBeInTheDocument()
    expect(within(cards[0]).getByText('백엔드 개발자')).toBeInTheDocument()
    expect(within(cards[1]).getByText('다른회사')).toBeInTheDocument()
    expect(within(cards[1]).getByText('프론트엔드 개발자')).toBeInTheDocument()
  })

  it('render_withSummaryOnly_hasNoPostingLinkOrMemo', () => {
    renderList([base])

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it.each([
    ['INTERVIEW_SCHEDULED', '면접예정'],
    ['OFFER', '최종합격(오퍼)'],
    ['WITHDRAWN', '지원취소'],
  ] as const)('render_withStatus_%s_showsLabelBadge', (status, label) => {
    renderList([{ ...base, status }])

    expect(within(cardButton('테스트회사')).getByText(label)).toBeInTheDocument()
  })

  it('render_withAppliedAt_showsPrefixedDate', () => {
    renderList([base, second])

    expect(screen.getByText('지원일 2026-09-01')).toBeInTheDocument()
    expect(screen.getByText('지원일 2026-09-15')).toBeInTheDocument()
  })

  it('render_editingItem_marksOnlyThatCardCurrent', () => {
    renderList([base, second], { editingId: 2 })

    expect(cardButton('다른회사')).toHaveAttribute('aria-current', 'true')
    expect(cardButton('테스트회사')).not.toHaveAttribute('aria-current')
  })

  it('render_notEditing_hasNoCurrentCard', () => {
    renderList([base, second])

    expect(cardButton('테스트회사')).not.toHaveAttribute('aria-current')
    expect(cardButton('다른회사')).not.toHaveAttribute('aria-current')
  })

  it('render_deleteButton_namedWithCompany', () => {
    renderList([base, second])

    expect(screen.getByRole('button', { name: '테스트회사 삭제' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다른회사 삭제' })).toBeInTheDocument()
  })

  it('clickCard_callsOnEditWithId', async () => {
    const user = userEvent.setup()
    const props = renderList([base, second])

    await user.click(cardButton('다른회사'))

    expect(props.onEdit).toHaveBeenCalledWith(2)
    expect(props.onDelete).not.toHaveBeenCalled()
  })

  it.each(['{Enter}', ' '])('pressKeyOnCard_%s_callsOnEdit', async (key) => {
    const user = userEvent.setup()
    const props = renderList([base])

    cardButton('테스트회사').focus()
    await user.keyboard(key)

    expect(props.onEdit).toHaveBeenCalledTimes(1)
    expect(props.onEdit).toHaveBeenCalledWith(1)
  })

  it('clickDelete_callsOnDeleteOnlyWithoutEdit', async () => {
    const user = userEvent.setup()
    const props = renderList([base, second])

    await user.click(screen.getByRole('button', { name: '다른회사 삭제' }))

    expect(props.onDelete).toHaveBeenCalledWith(2)
    expect(props.onEdit).not.toHaveBeenCalled()
  })

  it('render_whileLoading_showsLoadingWithoutEmptyMessage', () => {
    renderList([], { isLoading: true })

    expect(screen.getByText('불러오는 중...')).toBeInTheDocument()
    expect(screen.queryByText('지원 내역이 없습니다.')).not.toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('render_whileReloadingWithItems_keepsCardsAndShowsLoading', () => {
    renderList([base], { isLoading: true })

    expect(screen.getByRole('list', { name: '지원 내역 목록' })).toBeInTheDocument()
    expect(screen.getByText('불러오는 중...')).toBeInTheDocument()
  })

  it('render_emptyList_showsEmptyMessage', () => {
    renderList([])

    expect(screen.getByText('지원 내역이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('render_emptyListWithError_hidesEmptyMessage', () => {
    renderList([], { hasError: true })

    expect(screen.queryByText('지원 내역이 없습니다.')).not.toBeInTheDocument()
  })
})

describe('JobApplicationStatusBadge', () => {
  it('render_withStatus_showsKoreanLabel', () => {
    render(<JobApplicationStatusBadge status="DOCUMENT_PASS" />)

    expect(screen.getByText('서류합격')).toBeInTheDocument()
  })
})
