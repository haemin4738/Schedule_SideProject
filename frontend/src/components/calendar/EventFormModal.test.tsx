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

  it('onClose_cancelOrEscape_callsOnClose', async () => {
    const user = userEvent.setup()
    const props = renderCreate()

    await user.click(screen.getByRole('button', { name: '취소' }))
    await user.keyboard('{Escape}')

    expect(props.onClose).toHaveBeenCalledTimes(2)
  })
})
