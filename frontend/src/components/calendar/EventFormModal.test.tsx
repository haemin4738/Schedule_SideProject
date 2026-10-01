import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import EventFormModal from './EventFormModal'
import { createEvent, deleteEvent, updateEvent, type EventDetail } from '@/api/events'

vi.mock('@/api/events', () => ({
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
  deleteEvent: vi.fn(),
}))

const mockedCreate = vi.mocked(createEvent)
const mockedUpdate = vi.mocked(updateEvent)
const mockedDelete = vi.mocked(deleteEvent)

const detail: EventDetail = {
  id: 7,
  title: '팀 회의',
  startAt: '2026-09-30T14:00:00',
  endAt: '2026-09-30T15:30:00',
  allDay: false,
  color: '#D50000',
  eventCategory: 'WORK',
  description: '주간 회의',
  location: '회의실 A',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
}

const renderCreate = (overrides: Partial<Parameters<typeof EventFormModal>[0]> = {}) => {
  const props = {
    event: null,
    defaultStart: new Date(2026, 8, 30, 9, 0),
    defaultEnd: new Date(2026, 8, 30, 10, 0),
    onClose: vi.fn(),
    onSaved: vi.fn(),
    ...overrides,
  }
  render(<EventFormModal {...props} />)
  return props
}

describe('EventFormModal', () => {
  afterEach(() => vi.resetAllMocks())

  it('onSubmit_timedEvent_createsWithLocalDateTimes', async () => {
    const user = userEvent.setup()
    mockedCreate.mockResolvedValue({} as never)
    const props = renderCreate()

    await user.type(screen.getByLabelText('제목'), '  점심 약속  ')
    await user.selectOptions(screen.getByLabelText('카테고리'), 'WORK')
    await user.type(screen.getByLabelText('장소'), '강남역')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(props.onSaved).toHaveBeenCalled())
    expect(mockedCreate).toHaveBeenCalledWith({
      title: '점심 약속',
      allDay: false,
      startAt: '2026-09-30T09:00:00',
      endAt: '2026-09-30T10:00:00',
      eventCategory: 'WORK',
      color: null,
      location: '강남역',
      description: null,
    })
  })

  it('onSubmit_allDayEvent_savesWholeDays', async () => {
    const user = userEvent.setup()
    mockedCreate.mockResolvedValue({} as never)
    renderCreate({ defaultStart: new Date(2026, 8, 28), defaultEnd: new Date(2026, 8, 29, 23, 59), defaultAllDay: true })

    expect(screen.queryByLabelText('시작 시간')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('제목'), '여행')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled())
    expect(mockedCreate.mock.calls[0][0]).toMatchObject({
      allDay: true,
      startAt: '2026-09-28T00:00:00',
      endAt: '2026-09-29T23:59:59',
    })
  })

  it('onSubmit_colorPicked_sendsColor', async () => {
    const user = userEvent.setup()
    mockedCreate.mockResolvedValue({} as never)
    renderCreate()

    await user.type(screen.getByLabelText('제목'), '운동')
    await user.click(screen.getByRole('radio', { name: '바질' }))
    expect(screen.getByRole('radio', { name: '바질' })).toHaveAttribute('aria-checked', 'true')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(mockedCreate).toHaveBeenCalled())
    expect(mockedCreate.mock.calls[0][0].color).toBe('#0B8043')
  })

  it('onSubmit_blankTitle_showsErrorAndDoesNotSave', async () => {
    const user = userEvent.setup()
    renderCreate()

    await user.type(screen.getByLabelText('제목'), '   ')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('제목을 입력해 주세요.')).toBeInTheDocument()
    expect(mockedCreate).not.toHaveBeenCalled()
  })

  it('onSubmit_endBeforeStart_showsErrorAndDoesNotSave', async () => {
    const user = userEvent.setup()
    renderCreate()

    await user.type(screen.getByLabelText('제목'), '회의')
    await user.clear(screen.getByLabelText('종료 시간'))
    await user.type(screen.getByLabelText('종료 시간'), '08:00')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('종료는 시작보다 빠를 수 없습니다.')).toBeInTheDocument()
    expect(mockedCreate).not.toHaveBeenCalled()
  })

  it('onSubmit_serverError_showsMessageAndKeepsModal', async () => {
    const user = userEvent.setup()
    mockedCreate.mockRejectedValue({ response: { data: { error: '제목은 200자 이하여야 합니다.' } } })
    const props = renderCreate()

    await user.type(screen.getByLabelText('제목'), '회의')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('제목은 200자 이하여야 합니다.')
    expect(props.onSaved).not.toHaveBeenCalled()
  })

  it('render_editMode_prefillsAndUpdates', async () => {
    const user = userEvent.setup()
    mockedUpdate.mockResolvedValue({} as never)
    const props = renderCreate({ event: detail })

    expect(screen.getByRole('heading', { name: '일정 수정' })).toBeInTheDocument()
    expect(screen.getByLabelText('제목')).toHaveValue('팀 회의')
    expect(screen.getByLabelText('시작 시간')).toHaveValue('14:00')
    expect(screen.getByLabelText('종료 시간')).toHaveValue('15:30')
    expect(screen.getByLabelText('카테고리')).toHaveValue('WORK')
    expect(screen.getByLabelText('장소')).toHaveValue('회의실 A')
    expect(screen.getByLabelText('설명')).toHaveValue('주간 회의')
    expect(screen.getByRole('radio', { name: '토마토' })).toHaveAttribute('aria-checked', 'true')

    await user.clear(screen.getByLabelText('제목'))
    await user.type(screen.getByLabelText('제목'), '팀 회의(변경)')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(props.onSaved).toHaveBeenCalled())
    expect(mockedUpdate).toHaveBeenCalledWith(7, expect.objectContaining({ title: '팀 회의(변경)', color: '#D50000' }))
  })

  it('onDelete_confirmed_deletesEvent', async () => {
    const user = userEvent.setup()
    mockedDelete.mockResolvedValue({} as never)
    const props = renderCreate({ event: detail })

    await user.click(screen.getByRole('button', { name: '삭제' }))
    expect(mockedDelete).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '삭제 확인' }))

    await waitFor(() => expect(props.onSaved).toHaveBeenCalled())
    expect(mockedDelete).toHaveBeenCalledWith(7)
  })

  it('onDelete_cancelled_keepsEvent', async () => {
    const user = userEvent.setup()
    renderCreate({ event: detail })

    await user.click(screen.getByRole('button', { name: '삭제' }))
    await user.click(screen.getByRole('button', { name: '아니요' }))

    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument()
    expect(mockedDelete).not.toHaveBeenCalled()
  })

  it('onDelete_serverError_showsMessage', async () => {
    const user = userEvent.setup()
    mockedDelete.mockRejectedValue(new Error('Network Error'))
    renderCreate({ event: detail })

    await user.click(screen.getByRole('button', { name: '삭제' }))
    await user.click(screen.getByRole('button', { name: '삭제 확인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('일정을 삭제하지 못했습니다.')
  })

  it('render_createMode_hasNoDeleteButton', () => {
    renderCreate()
    expect(screen.getByRole('heading', { name: '새 일정' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })

  it('requestClose_dirtyForm_asksBeforeDiscarding', async () => {
    const user = userEvent.setup()
    const props = renderCreate()

    await user.type(screen.getByLabelText('제목'), '작성 중')
    await user.keyboard('{Escape}')

    expect(props.onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('작성 중인 내용을 버릴까요?')
    expect(screen.getByRole('button', { name: '계속 작성' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: '계속 작성' }))
    expect(screen.queryByText('작성 중인 내용을 버릴까요?')).not.toBeInTheDocument()
    expect(screen.getByLabelText('제목')).toHaveValue('작성 중')

    await user.click(screen.getByRole('button', { name: '취소' }))
    await user.click(screen.getByRole('button', { name: '버리기' }))
    expect(props.onClose).toHaveBeenCalledTimes(1)
  })

  it('requestClose_whileSaving_ignoresClose', async () => {
    const user = userEvent.setup()
    mockedCreate.mockReturnValue(new Promise(() => {}))
    const props = renderCreate()

    await user.type(screen.getByLabelText('제목'), '회의')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('button', { name: '저장 중...' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '취소' })).toBeDisabled()
    await user.keyboard('{Escape}')
    expect(props.onClose).not.toHaveBeenCalled()
    expect(screen.queryByText('작성 중인 내용을 버릴까요?')).not.toBeInTheDocument()
  })

  it('render_editWithInvalidStoredColor_selectsCategoryDefault', () => {
    renderCreate({ event: { ...detail, color: 'red' } })
    expect(screen.getByRole('radio', { name: '카테고리 기본 색' })).toHaveAttribute('aria-checked', 'true')
  })

  it('onChange_startMovedBeforeEnd_clearsEndError', async () => {
    const user = userEvent.setup()
    renderCreate()

    await user.type(screen.getByLabelText('제목'), '회의')
    await user.clear(screen.getByLabelText('종료 시간'))
    await user.type(screen.getByLabelText('종료 시간'), '08:00')
    await user.click(screen.getByRole('button', { name: '저장' }))
    expect(await screen.findByText('종료는 시작보다 빠를 수 없습니다.')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('시작 시간'))
    await user.type(screen.getByLabelText('시작 시간'), '07:00')

    await waitFor(() => expect(screen.queryByText('종료는 시작보다 빠를 수 없습니다.')).not.toBeInTheDocument())
  })

  it('onClose_cancelOrEscape_callsOnClose', async () => {
    const user = userEvent.setup()
    const props = renderCreate()

    await user.click(screen.getByRole('button', { name: '취소' }))
    await user.keyboard('{Escape}')

    expect(props.onClose).toHaveBeenCalledTimes(2)
  })

  it('onSubmit_editEventWithoutEnd_keepsEndAtNull', async () => {
    const user = userEvent.setup()
    mockedUpdate.mockResolvedValue({} as never)
    renderCreate({ event: { ...detail, endAt: null } })

    await user.clear(screen.getByLabelText('제목'))
    await user.type(screen.getByLabelText('제목'), '제목만 수정')
    await user.click(screen.getByRole('button', { name: '저장' }))

    // 폼에는 임시 종료(1시간 뒤)가 보이지만 종료를 건드리지 않았으므로 종료 없음을 유지한다
    await waitFor(() => expect(mockedUpdate).toHaveBeenCalledWith(7, expect.objectContaining({ endAt: null })))
  })

  it('onSubmit_editEventWithoutEndAndEndChanged_sendsEnd', async () => {
    const user = userEvent.setup()
    mockedUpdate.mockResolvedValue({} as never)
    renderCreate({ event: { ...detail, endAt: null } })

    await user.clear(screen.getByLabelText('종료 시간'))
    await user.type(screen.getByLabelText('종료 시간'), '16:00')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith(7, expect.objectContaining({ endAt: '2026-09-30T16:00:00' })),
    )
  })

  it('onKeyDown_colorRadioArrows_movesSelectionAndFocusWithSingleTabStop', async () => {
    const user = userEvent.setup()
    renderCreate()
    const radios = screen.getAllByRole('radio')
    const auto = screen.getByRole('radio', { name: '카테고리 기본 색' })

    // Tab 진입점은 선택된 항목 하나뿐
    expect(radios.filter((r) => r.tabIndex === 0)).toEqual([auto])

    auto.focus()
    await user.keyboard('{ArrowRight}')
    expect(radios[1]).toHaveAttribute('aria-checked', 'true')
    expect(radios[1]).toHaveFocus()
    expect(radios[1]).toHaveAttribute('tabindex', '0')
    expect(auto).toHaveAttribute('tabindex', '-1')

    // 처음에서 왼쪽으로 가면 마지막으로 돈다
    await user.keyboard('{ArrowLeft}{ArrowLeft}')
    expect(radios[radios.length - 1]).toHaveAttribute('aria-checked', 'true')
    expect(radios[radios.length - 1]).toHaveFocus()
  })

  it('render_editWithUnlistedColor_firstRadioIsTabStop', () => {
    renderCreate({ event: { ...detail, color: '#123456' } })

    const tabStops = screen.getAllByRole('radio').filter((r) => r.tabIndex === 0)
    expect(tabStops).toEqual([screen.getByRole('radio', { name: '카테고리 기본 색' })])
  })

  it('onKeyDown_tabAtLastElement_wrapsFocusInsideDialog', async () => {
    const user = userEvent.setup()
    renderCreate()
    const dialog = screen.getByRole('dialog')
    const title = screen.getByLabelText('제목')

    // Shift+Tab 을 처음 요소에서 누르면 마지막 요소로, 거기서 Tab 이면 다시 처음으로
    title.focus()
    await user.tab({ shift: true })
    const last = document.activeElement as HTMLElement
    expect(dialog).toContainElement(last)
    expect(last).not.toBe(title)

    await user.tab()
    expect(title).toHaveFocus()
  })

})
