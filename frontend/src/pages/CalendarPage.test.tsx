import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CalendarPage from './CalendarPage'
import { toCalendarEvent, visibleRange } from '@/components/calendar/calendarUtils'
import { getEvent, getEventsInRange, type EventSummary } from '@/api/events'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/events', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/events')>()),
  getEventsInRange: vi.fn(),
  getEvent: vi.fn(),
}))
vi.mock('@/components/LogoutButton', () => ({ default: () => <button type="button">로그아웃</button> }))

const mockedRange = vi.mocked(getEventsInRange)
const mockedGet = vi.mocked(getEvent)

class FakeEventSource {
  static CLOSED = 2
  readyState = 1
  onerror: (() => void) | null = null
  addEventListener() {}
  close() {}
}

const summary = (over: Partial<EventSummary> = {}): EventSummary => ({
  id: 1,
  title: '팀 회의',
  startAt: '2026-09-30T14:00:00',
  endAt: '2026-09-30T15:00:00',
  allDay: false,
  color: null,
  eventCategory: 'WORK',
  ...over,
})

const renderPage = () =>
  render(
    <MemoryRouter>
      <CalendarPage />
    </MemoryRouter>,
  )

describe('CalendarPage', () => {
  beforeEach(() => {
    // 달력 기준일을 2026-09-30(수)으로 고정한다 (타이머는 실제로 둔다)
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 30, 10, 0))
    vi.stubGlobal('EventSource', FakeEventSource)
    // react-big-calendar 의 드래그 선택이 쓰는 API — jsdom 에는 없다
    document.elementFromPoint = () => null
    useAuthStore.setState({ accessToken: 'acc' })
    mockedRange.mockResolvedValue([])
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.resetAllMocks()
    useAuthStore.setState({ accessToken: null })
  })

  it('render_monthView_showsKoreanTitleWeekdaysAndFetchesVisibleRange', async () => {
    renderPage()

    expect(screen.getByRole('heading', { name: '2026년 9월' })).toBeInTheDocument()
    const weekdayHeader = (label: string) => screen.getByText(label, { selector: '.rbc-month-header span' })
    expect(weekdayHeader('일')).toHaveClass('text-red-500')
    expect(weekdayHeader('토')).toHaveClass('text-blue-500')
    expect(weekdayHeader('수')).toHaveClass('text-gray-700')
    await waitFor(() => expect(mockedRange).toHaveBeenCalled())
    const [from, to] = mockedRange.mock.calls[0]
    // 9월 달력은 8/30(일) ~ 10/3(토) 을 보여준다
    expect(from).toEqual(new Date(2026, 7, 30, 0, 0, 0, 0))
    expect(to.getFullYear()).toBe(2026)
    expect(to.getMonth()).toBe(9)
    expect(to.getDate()).toBe(3)
  })

  it('render_today_marksTodayDate', () => {
    renderPage()
    expect(screen.getByRole('button', { name: '9월 30일 수요일' })).toHaveAttribute('aria-current', 'date')
  })

  it('render_events_showsEventsInCategoryColor', async () => {
    mockedRange.mockResolvedValue([summary()])
    renderPage()

    const event = await screen.findByText('팀 회의')
    expect(event.closest('.rbc-event')).toHaveStyle({ backgroundColor: '#3F51B5' })
  })

  it('onNavigate_nextAndToday_changesMonthAndRefetches', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitFor(() => expect(mockedRange).toHaveBeenCalledTimes(1))

    await user.click(screen.getByRole('button', { name: '다음' }))
    expect(screen.getByRole('heading', { name: '2026년 10월' })).toBeInTheDocument()
    await waitFor(() => expect(mockedRange).toHaveBeenCalledTimes(2))

    await user.click(screen.getByRole('button', { name: '오늘' }))
    expect(screen.getByRole('heading', { name: '2026년 9월' })).toBeInTheDocument()
  })

  it('onView_week_showsWeekAndFetchesWeekRange', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(within(screen.getByRole('group', { name: '보기 전환' })).getByRole('button', { name: '주' }))

    expect(screen.getByRole('button', { name: '주', pressed: true })).toBeInTheDocument()
    await waitFor(() => {
      const [from, to] = mockedRange.mock.calls.at(-1)!
      expect(from).toEqual(new Date(2026, 8, 27, 0, 0, 0, 0))
      expect(to.getDate()).toBe(3)
    })
  })

  it('onCreateClick_opensCreateModalAndReloadsAfterSave', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: /만들기/ }))

    expect(screen.getByRole('dialog', { name: '새 일정' })).toBeInTheDocument()
    // 다음 정시(11:00)부터 1시간
    expect(screen.getByLabelText('시작 시간')).toHaveValue('11:00')
    expect(screen.getByLabelText('종료 시간')).toHaveValue('12:00')
  })

  it('onSelectEvent_fetchesDetailAndOpensEditModal', async () => {
    const user = userEvent.setup()
    mockedRange.mockResolvedValue([summary()])
    mockedGet.mockResolvedValue({
      data: { success: true, data: { ...summary(), description: '설명', location: '회의실', createdAt: '', updatedAt: '' } },
    } as never)
    renderPage()

    await user.click(await screen.findByText('팀 회의'))

    expect(await screen.findByRole('dialog', { name: '일정 수정' })).toBeInTheDocument()
    expect(mockedGet).toHaveBeenCalledWith(1)
    expect(screen.getByLabelText('장소')).toHaveValue('회의실')
  })

  it('onSelectEvent_detailFails_showsError', async () => {
    const user = userEvent.setup()
    mockedRange.mockResolvedValue([summary()])
    mockedGet.mockRejectedValue(new Error('Network Error'))
    renderPage()

    await user.click(await screen.findByText('팀 회의'))

    expect(await screen.findByRole('alert')).toHaveTextContent('일정을 불러오지 못했습니다.')
  })

  it('load_fails_showsError', async () => {
    mockedRange.mockRejectedValue({ response: { data: { error: '서버 오류' } } })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('서버 오류')
  })

  it('render_header_hasNavigationAndLogout', () => {
    renderPage()
    const nav = screen.getByRole('navigation', { name: '주요 메뉴' })
    expect(within(nav).getByRole('link', { name: '캘린더' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: '구직활동' })).toHaveAttribute('href', '/job-applications')
    expect(within(nav).getByRole('link', { name: '가계부' })).toHaveAttribute('href', '/expenses')
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
  })
})

describe('visibleRange', () => {
  it('visibleRange_day_returnsWholeDay', () => {
    const { from, to } = visibleRange(new Date(2026, 8, 30, 15), 'day')
    expect(from).toEqual(new Date(2026, 8, 30, 0, 0, 0, 0))
    expect(to).toEqual(new Date(2026, 8, 30, 23, 59, 59, 999))
  })
})

describe('toCalendarEvent', () => {
  it('toCalendarEvent_timedWithoutEnd_assumesOneHour', () => {
    const e = toCalendarEvent(summary({ endAt: null }))
    expect(e.end).toEqual(new Date(2026, 8, 30, 15, 0))
  })

  it('toCalendarEvent_allDayWithoutEnd_endsSameDay', () => {
    const e = toCalendarEvent(summary({ allDay: true, startAt: '2026-09-30T00:00:00', endAt: null }))
    expect(e.end).toEqual(new Date(2026, 8, 30, 23, 59, 59, 999))
  })

  it('toCalendarEvent_customColor_usesIt', () => {
    expect(toCalendarEvent(summary({ color: '#0B8043' })).color).toBe('#0B8043')
  })
})
