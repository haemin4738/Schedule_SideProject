import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CalendarPage from './CalendarPage'
import { getEvent, getEventsInRange, type EventSummary } from '@/api/events'
import { getDailySummary } from '@/api/expenses'
import { getJobApplicationsInRange, type JobApplicationSummary } from '@/api/jobApplications'
import { getSpecialDays, type SpecialDay } from '@/api/specialDays'
import { LAYERS_STORAGE_KEY } from '@/components/calendar/calendarLayers'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/events', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/events')>()),
  getEventsInRange: vi.fn(),
  getEvent: vi.fn(),
}))
vi.mock('@/api/specialDays', () => ({ getSpecialDays: vi.fn() }))
vi.mock('@/api/expenses', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/expenses')>()),
  getDailySummary: vi.fn(),
}))
vi.mock('@/api/jobApplications', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jobApplications')>()),
  getJobApplicationsInRange: vi.fn(),
}))
vi.mock('@/components/LogoutButton', () => ({ default: () => <button type="button">로그아웃</button> }))

const mockedRange = vi.mocked(getEventsInRange)
const mockedGet = vi.mocked(getEvent)
const mockedSpecialDays = vi.mocked(getSpecialDays)
const mockedDaily = vi.mocked(getDailySummary)
const mockedJobs = vi.mocked(getJobApplicationsInRange)

const specialDaysRes = (data: SpecialDay[]) => ({ data: { success: true, data } }) as never
const dailyRes = (days: { date: string; income: number; expense: number; net: number }[]) =>
  ({ data: { success: true, data: { from: '', to: '', totalIncome: 0, totalExpense: 0, net: 0, days } } }) as never

const chuseok: SpecialDay = { date: '2026-09-25', name: '추석', kind: 'HOLIDAY', holiday: true }
const solarTerm: SpecialDay = { date: '2026-09-23', name: '추분', kind: 'SOLAR_TERM', holiday: false }
const jobApplication: JobApplicationSummary = {
  id: 7,
  companyName: '라이프로그',
  position: '백엔드',
  status: 'APPLIED',
  appliedAt: '2026-09-10',
}
const JOB_TITLE = '라이프로그 · 백엔드 (지원완료)'

class FakeEventSource {
  static CLOSED = 2
  static last: FakeEventSource | null = null
  readyState = 1
  onerror: (() => void) | null = null
  listeners = new Map<string, Array<() => void>>()
  constructor() {
    FakeEventSource.last = this
  }
  addEventListener(type: string, listener: () => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }
  emit(type: string) {
    this.listeners.get(type)?.forEach((l) => l())
  }
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

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

const renderWithRoutes = () =>
  render(
    <MemoryRouter initialEntries={['/calendar']}>
      <Routes>
        <Route path="/calendar" element={<CalendarPage />} />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )

const layerGroup = () => screen.getByRole('group', { name: '캘린더에 표시할 항목' })
const layerButton = (name: string) => within(layerGroup()).getByRole('button', { name })

describe('CalendarPage', () => {
  beforeEach(() => {
    localStorage.clear()
    mockedSpecialDays.mockResolvedValue(specialDaysRes([]))
    mockedDaily.mockResolvedValue(dailyRes([]))
    mockedJobs.mockResolvedValue([])
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
    localStorage.clear()
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

  it('onKeyDown_enterOnMonthEvent_opensEditModal', async () => {
    const user = userEvent.setup()
    mockedRange.mockResolvedValue([summary()])
    mockedGet.mockResolvedValue({
      data: { success: true, data: { ...summary(), description: null, location: null, createdAt: '', updatedAt: '' } },
    } as never)
    renderPage()

    const eventButton = await screen.findByRole('button', { name: '팀 회의' })
    eventButton.focus()
    await user.keyboard('{Enter}')

    expect(await screen.findByRole('dialog', { name: '일정 수정' })).toBeInTheDocument()
  })

  it('onSelectEvent_clickedTwiceQuickly_opensLastClickedEvent', async () => {
    const user = userEvent.setup()
    mockedRange.mockResolvedValue([summary(), summary({ id: 2, title: '점심' })])
    let resolveFirst: (v: unknown) => void = () => {}
    mockedGet
      .mockReturnValueOnce(new Promise((r) => (resolveFirst = r)) as never)
      .mockResolvedValueOnce({
        data: { success: true, data: { ...summary({ id: 2, title: '점심' }), description: null, location: null, createdAt: '', updatedAt: '' } },
      } as never)
    renderPage()

    await user.click(await screen.findByText('팀 회의'))
    await user.click(screen.getByText('점심'))
    expect(await screen.findByRole('dialog', { name: '일정 수정' })).toBeInTheDocument()
    expect(screen.getByLabelText('제목')).toHaveValue('점심')

    resolveFirst({ data: { success: true, data: { ...summary(), description: null, location: null, createdAt: '', updatedAt: '' } } })
    await new Promise((r) => setTimeout(r, 0))
    expect(screen.getByLabelText('제목')).toHaveValue('점심')
  })

  it('load_failsAfterSuccess_clearsStaleEvents', async () => {
    const user = userEvent.setup()
    mockedRange.mockResolvedValueOnce([summary()]).mockRejectedValueOnce(new Error('Network Error'))
    renderPage()
    expect(await screen.findByText('팀 회의')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '다음' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('일정을 불러오지 못했습니다.')
    expect(screen.queryByText('팀 회의')).not.toBeInTheDocument()
  })

  it('load_overPageLimit_showsTruncatedNotice', async () => {
    mockedRange.mockImplementation(async (_from, _to, onTruncated) => {
      onTruncated?.()
      return [summary()]
    })
    renderPage()

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('일정이 너무 많아 앞의 2,000개만 표시합니다.'),
    )
    expect(screen.getByText('팀 회의')).toBeInTheDocument()
  })

  it('sse_firstOpenSkipped_laterOpenAndRefreshReload', async () => {
    renderPage()
    await waitFor(() => expect(mockedRange).toHaveBeenCalledTimes(1))
    const es = FakeEventSource.last!

    // 서버가 연결 즉시 CONNECTED 를 보내 open 이 바로 온다 — 방금 불러왔으므로 다시 부르지 않는다
    act(() => es.emit('open'))
    expect(mockedRange).toHaveBeenCalledTimes(1)

    act(() => es.emit('REFRESH'))
    await waitFor(() => expect(mockedRange).toHaveBeenCalledTimes(2))

    // 끊겼다 다시 연결되면 그동안의 변경을 따라잡는다
    act(() => es.emit('open'))
    await waitFor(() => expect(mockedRange).toHaveBeenCalledTimes(3))
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
  describe('캘린더 오버레이', () => {
    it('render_layerToggles_allOnByDefault', () => {
      renderPage()

      for (const name of ['일정', '구직활동', '가계부', '공휴일·기념일']) {
        expect(layerButton(name)).toHaveAttribute('aria-pressed', 'true')
      }
    })

    it('render_savedLayers_restoresToggleState', () => {
      localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify({ expenses: false }))
      renderPage()

      expect(layerButton('가계부')).toHaveAttribute('aria-pressed', 'false')
      expect(layerButton('일정')).toHaveAttribute('aria-pressed', 'true')
    })

    it('render_specialDays_showsHolidayNameInRedAndOthersInGray', async () => {
      mockedSpecialDays.mockResolvedValue(specialDaysRes([chuseok, solarTerm]))
      renderPage()

      expect(await screen.findByText('추석')).toHaveClass('text-red-500')
      expect(screen.getByText('추분')).toHaveClass('text-gray-500')
      // 공휴일인 금요일 날짜 숫자도 빨강
      expect(screen.getByRole('button', { name: '9월 25일 금요일 추석' })).toHaveClass('text-red-500')
      expect(screen.getByRole('button', { name: '9월 23일 수요일' })).toHaveClass('text-gray-700')
      expect(mockedSpecialDays).toHaveBeenCalledWith('2026-08-30', '2026-10-03')
    })

    it('onToggle_specialDaysOff_hidesNamesButKeepsHolidayRedAndSaves', async () => {
      const user = userEvent.setup()
      mockedSpecialDays.mockResolvedValue(specialDaysRes([chuseok]))
      renderPage()
      await screen.findByText('추석')

      await user.click(layerButton('공휴일·기념일'))

      expect(layerButton('공휴일·기념일')).toHaveAttribute('aria-pressed', 'false')
      expect(screen.queryByText('추석')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: '9월 25일 금요일 추석' })).toHaveClass('text-red-500')
      expect(JSON.parse(localStorage.getItem(LAYERS_STORAGE_KEY)!)).toMatchObject({ specialDays: false })
    })

    it('onToggle_eventsOff_hidesEventsAndSaves', async () => {
      const user = userEvent.setup()
      mockedRange.mockResolvedValue([summary()])
      renderPage()
      await screen.findByText('팀 회의')

      await user.click(layerButton('일정'))

      expect(screen.queryByText('팀 회의')).not.toBeInTheDocument()
      expect(JSON.parse(localStorage.getItem(LAYERS_STORAGE_KEY)!)).toEqual({
        events: false,
        jobApplications: true,
        expenses: true,
        specialDays: true,
      })

      await user.click(layerButton('일정'))
      expect(await screen.findByText('팀 회의')).toBeInTheDocument()
    })

    it('render_dailyExpenses_showsSignedAmountsLinkingToExpensesMonth', async () => {
      mockedDaily.mockResolvedValue(
        dailyRes([
          { date: '2026-09-05', income: 50000, expense: 12000, net: 38000 },
          { date: '2026-10-01', income: 0, expense: 3000, net: -3000 },
        ]),
      )
      renderPage()

      const link = await screen.findByRole('link', { name: '9월 5일 가계부 지출 -12,000 수입 +50,000' })
      expect(link).toHaveAttribute('href', '/expenses?month=2026-09')
      expect(within(link).getByText('-12,000')).toHaveClass('text-red-500')
      expect(within(link).getByText('+50,000')).toHaveClass('text-blue-500')
      // 다음 달 날짜 칸은 그 달 가계부로 연결
      const octLink = screen.getByRole('link', { name: '10월 1일 가계부 지출 -3,000' })
      expect(octLink).toHaveAttribute('href', '/expenses?month=2026-10')
      expect(within(octLink).queryByText(/^\+/)).not.toBeInTheDocument()
      expect(mockedDaily).toHaveBeenCalledWith({ from: '2026-08-30', to: '2026-10-03' })
    })

    it('onToggle_expensesOff_hidesAmountsWithoutRefetching', async () => {
      const user = userEvent.setup()
      mockedDaily.mockResolvedValue(dailyRes([{ date: '2026-09-05', income: 0, expense: 12000, net: -12000 }]))
      renderPage()
      await screen.findByText('-12,000')

      await user.click(layerButton('가계부'))

      expect(screen.queryByText('-12,000')).not.toBeInTheDocument()
      expect(mockedDaily).toHaveBeenCalledTimes(1)
    })

    it('render_expensesLayerSavedOff_doesNotRequestDailySummary', async () => {
      localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify({ expenses: false, jobApplications: false }))
      renderPage()

      await waitFor(() => expect(mockedSpecialDays).toHaveBeenCalled())
      expect(mockedDaily).not.toHaveBeenCalled()
      expect(mockedJobs).not.toHaveBeenCalled()
    })

    it('onMouseDownExpenseLink_stopsPropagationToDocument', async () => {
      // react-big-calendar 는 document 의 mousedown/touchstart 로 칸 선택(일정 만들기)을 시작한다
      mockedDaily.mockResolvedValue(dailyRes([{ date: '2026-09-05', income: 0, expense: 12000, net: -12000 }]))
      renderPage()
      const link = await screen.findByRole('link', { name: /9월 5일 가계부/ })
      const onDocument = vi.fn()
      document.addEventListener('mousedown', onDocument)
      document.addEventListener('touchstart', onDocument)

      try {
        fireEvent.mouseDown(link)
        fireEvent.touchStart(link)
        expect(onDocument).not.toHaveBeenCalled()
      } finally {
        document.removeEventListener('mousedown', onDocument)
        document.removeEventListener('touchstart', onDocument)
      }
    })

    it('onClickExpenseLink_navigatesToExpensesMonth', async () => {
      const user = userEvent.setup()
      mockedDaily.mockResolvedValue(dailyRes([{ date: '2026-09-05', income: 0, expense: 12000, net: -12000 }]))
      renderWithRoutes()

      await user.click(await screen.findByRole('link', { name: /9월 5일 가계부/ }))

      expect(screen.getByTestId('location')).toHaveTextContent('/expenses?month=2026-09')
    })

    it('render_weekView_showsOverlayTextWithoutExpenseLink', async () => {
      const user = userEvent.setup()
      mockedSpecialDays.mockResolvedValue(specialDaysRes([{ ...chuseok, date: '2026-09-30', name: '테스트공휴일' }]))
      mockedDaily.mockResolvedValue(dailyRes([{ date: '2026-09-29', income: 1000, expense: 12000, net: -11000 }]))
      renderPage()

      await user.click(within(screen.getByRole('group', { name: '보기 전환' })).getByRole('button', { name: '주' }))

      await waitFor(() => expect(mockedDaily).toHaveBeenLastCalledWith({ from: '2026-09-27', to: '2026-10-03' }))
      expect(await screen.findByText('-12,000')).toHaveClass('text-red-500')
      expect(screen.getByText('+1,000')).toHaveClass('text-blue-500')
      expect(screen.getByText('테스트공휴일')).toHaveClass('text-red-500')
      // 주·일 머리글은 드릴다운 button 안이라 링크를 중첩하지 않는다
      expect(screen.queryByRole('link', { name: /가계부 지출/ })).not.toBeInTheDocument()
      expect(screen.getByText('-12,000').closest('a')).toBeNull()
    })

    it('render_jobApplications_showsAllDayItemWithStatus', async () => {
      mockedJobs.mockResolvedValue([jobApplication])
      renderPage()

      const item = await screen.findByText(JOB_TITLE)
      expect(item.closest('.rbc-event')).toHaveStyle({ backgroundColor: '#8e24aa' })
      expect(mockedJobs).toHaveBeenCalledWith('2026-08-30', '2026-10-03')
    })

    it('onToggle_jobApplicationsOff_hidesItems', async () => {
      const user = userEvent.setup()
      mockedJobs.mockResolvedValue([jobApplication])
      renderPage()
      await screen.findByText(JOB_TITLE)

      await user.click(layerButton('구직활동'))

      expect(screen.queryByText(JOB_TITLE)).not.toBeInTheDocument()
    })

    it('onSelectEvent_jobApplication_navigatesToJobApplicationEdit', async () => {
      const user = userEvent.setup()
      mockedJobs.mockResolvedValue([jobApplication])
      renderWithRoutes()

      await user.click(await screen.findByText(JOB_TITLE))

      expect(await screen.findByTestId('location')).toHaveTextContent('/job-applications?id=7')
      expect(mockedGet).not.toHaveBeenCalled()
    })

    it('render_oneOverlayFails_showsStatusAndKeepsOtherLayers', async () => {
      mockedSpecialDays.mockResolvedValue(specialDaysRes([chuseok]))
      mockedDaily.mockRejectedValue(new Error('Network Error'))
      mockedJobs.mockResolvedValue([jobApplication])
      mockedRange.mockResolvedValue([summary()])
      renderPage()

      // status 영역은 항상 있으므로 내용이 채워질 때까지 기다린다
      await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('가계부 합계를 불러오지 못했습니다.'))
      expect(await screen.findByText('추석')).toBeInTheDocument()
      expect(await screen.findByText(JOB_TITLE)).toBeInTheDocument()
      expect(await screen.findByText('팀 회의')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })
})
