import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CalendarPage from './CalendarPage'
import { getEvent, getEventsInRange, type EventSummary } from '@/api/events'
import {
  addDefaultExpenseCategories,
  createExpense,
  getDailySummary,
  getExpenseCategories,
  getMonthlySummary,
} from '@/api/expenses'
import { createJobApplication, getJobApplicationsInRange, type JobApplicationSummary } from '@/api/jobApplications'
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
  getMonthlySummary: vi.fn(),
  getExpenseCategories: vi.fn(),
  createExpense: vi.fn(),
  addDefaultExpenseCategories: vi.fn(),
}))
vi.mock('@/api/jobApplications', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/jobApplications')>()),
  getJobApplicationsInRange: vi.fn(),
  createJobApplication: vi.fn(),
}))
vi.mock('@/components/LogoutButton', () => ({ default: () => <button type="button">로그아웃</button> }))

const mockedRange = vi.mocked(getEventsInRange)
const mockedGet = vi.mocked(getEvent)
const mockedSpecialDays = vi.mocked(getSpecialDays)
const mockedDaily = vi.mocked(getDailySummary)
const mockedMonthly = vi.mocked(getMonthlySummary)
const mockedJobs = vi.mocked(getJobApplicationsInRange)
const mockedCategories = vi.mocked(getExpenseCategories)
const mockedCreateExpense = vi.mocked(createExpense)
const mockedAddDefaults = vi.mocked(addDefaultExpenseCategories)
const mockedCreateJob = vi.mocked(createJobApplication)

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
    mockedMonthly.mockResolvedValue({
      data: { success: true, data: { from: '', to: '', totalIncome: 0, totalExpense: 0, net: 0, months: [] } },
    } as never)
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

  describe('사이드바 가계부 요약', () => {
    const panel = () => screen.getByRole('region', { name: '가계부 요약' })
    const totals = (title: string) => within(panel()).getByRole('heading', { name: title }).parentElement!

    it('render_monthView_showsMonthTotalsWithoutOffRangeDaysAndTodayByDefault', async () => {
      mockedDaily.mockResolvedValue(
        dailyRes([
          { date: '2026-08-31', income: 0, expense: 999, net: -999 },
          { date: '2026-09-05', income: 50000, expense: 12000, net: 38000 },
          { date: '2026-09-30', income: 0, expense: 3000, net: -3000 },
          { date: '2026-10-01', income: 0, expense: 777, net: -777 },
        ]),
      )
      renderPage()

      const month = totals('9월 전체')
      await waitFor(() => expect(month).toHaveTextContent('수입50,000원지출15,000원합계35,000원'))
      // 기본 선택일은 오늘(9/30)
      expect(totals('9월 30일 (수)')).toHaveTextContent('수입0원지출3,000원합계-3,000원')
      expect(within(panel()).getByRole('link', { name: '가계부 보기' })).toHaveAttribute('href', '/expenses?month=2026-09')
      // 월간 보기는 일별 합계만으로 계산한다
      expect(mockedMonthly).not.toHaveBeenCalled()
    })

    it('onView_week_usesMonthlySummaryForMonthTotals', async () => {
      const user = userEvent.setup()
      mockedMonthly.mockResolvedValue({
        data: { success: true, data: { from: '2026-09', to: '2026-09', totalIncome: 1000, totalExpense: 2000, net: -1000, months: [] } },
      } as never)
      renderPage()

      await user.click(within(screen.getByRole('group', { name: '보기 전환' })).getByRole('button', { name: '주' }))

      await waitFor(() => expect(totals('9월 전체')).toHaveTextContent('수입1,000원지출2,000원합계-1,000원'))
      expect(mockedMonthly).toHaveBeenCalledWith({ from: '2026-09', to: '2026-09' })
    })

    it('onNavigate_nextMonth_defaultsToFirstDayOfMonth', async () => {
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: '다음' }))

      expect(within(panel()).getByRole('heading', { name: '10월 1일 (목)' })).toBeInTheDocument()
      expect(within(panel()).getByRole('link', { name: '가계부 보기' })).toHaveAttribute('href', '/expenses?month=2026-10')
    })

    it('toggleLayer_expensesOff_hidesPanel', async () => {
      const user = userEvent.setup()
      renderPage()
      expect(panel()).toBeInTheDocument()

      await user.click(layerButton('가계부'))

      expect(screen.queryByRole('region', { name: '가계부 요약' })).not.toBeInTheDocument()
    })

    it('load_dailyFails_showsErrorInPanelAndKeepsCalendar', async () => {
      mockedDaily.mockRejectedValue(new Error('Network Error'))
      mockedRange.mockResolvedValue([summary()])
      renderPage()

      await waitFor(() => expect(within(panel()).getAllByText('불러오지 못했습니다.')).toHaveLength(2))
      expect(await screen.findByText('팀 회의')).toBeInTheDocument()
    })

    const monthlyRes = (totalIncome: number, totalExpense: number) =>
      ({
        data: { success: true, data: { from: '', to: '', totalIncome, totalExpense, net: totalIncome - totalExpense, months: [] } },
      }) as never
    const switchView = (user: ReturnType<typeof userEvent.setup>, name: string) =>
      user.click(within(screen.getByRole('group', { name: '보기 전환' })).getByRole('button', { name }))

    /**
     * 월간 보기의 날짜 칸을 마우스로 한 번 누른다.
     * react-big-calendar 는 document 의 mousedown/mouseup 좌표와 각 주 행(.rbc-row-bg)의 위치로 칸을 계산하는데
     * jsdom 에는 레이아웃이 없어, 주 행마다 가로 700px·세로 100px 위치와 elementFromPoint 를 흉내 낸다
     */
    const clickMonthCell = (weekIndex: number, dayIndex: number) => {
      const rows = Array.from(document.querySelectorAll<HTMLElement>('.rbc-month-view .rbc-row-bg'))
      rows.forEach((row, i) => {
        row.getBoundingClientRect = () => ({ top: i * 100, left: 0, bottom: i * 100 + 100, right: 700 }) as DOMRect
        Object.defineProperty(row, 'offsetWidth', { configurable: true, value: 700 })
        Object.defineProperty(row, 'offsetHeight', { configurable: true, value: 100 })
      })
      const cell = rows[weekIndex].querySelectorAll<HTMLElement>('.rbc-day-bg')[dayIndex]
      document.elementFromPoint = () => cell
      const point = { clientX: dayIndex * 100 + 50, clientY: weekIndex * 100 + 50, button: 0 }
      fireEvent.mouseDown(cell, point)
      fireEvent.mouseUp(cell, point)
    }

    /** 월간 보기 격자에서 (주, 요일) 칸의 배경 요소 */
    const monthCell = (weekIndex: number, dayIndex: number) =>
      document.querySelectorAll('.rbc-month-view .rbc-row-bg')[weekIndex].querySelectorAll('.rbc-day-bg')[dayIndex]

    /** 같은 주 행에서 fromDay 칸부터 toDay 칸까지 마우스로 끌어 고른다 (layout 흉내는 clickMonthCell 과 같다) */
    const dragMonthCells = (weekIndex: number, fromDay: number, toDay: number) => {
      const rows = Array.from(document.querySelectorAll<HTMLElement>('.rbc-month-view .rbc-row-bg'))
      rows.forEach((row, i) => {
        row.getBoundingClientRect = () => ({ top: i * 100, left: 0, bottom: i * 100 + 100, right: 700 }) as DOMRect
        Object.defineProperty(row, 'offsetWidth', { configurable: true, value: 700 })
        Object.defineProperty(row, 'offsetHeight', { configurable: true, value: 100 })
      })
      const cells = rows[weekIndex].querySelectorAll<HTMLElement>('.rbc-day-bg')
      const point = (day: number) => ({ clientX: day * 100 + 50, clientY: weekIndex * 100 + 50, button: 0 })
      document.elementFromPoint = () => cells[fromDay]
      act(() => {
        fireEvent.mouseDown(cells[fromDay], point(fromDay))
      })
      document.elementFromPoint = () => cells[toDay]
      // rbc 는 첫 이동 지점을 드래그 시작점으로 삼으므로 시작 칸 안에서 조금 움직인 뒤 목표 칸으로 옮긴다.
      // 끄는 동안의 선택 범위(state)가 반영된 뒤에 놓아야 하므로 act 를 나눈다
      act(() => {
        fireEvent.mouseMove(cells[fromDay], { ...point(fromDay), clientX: fromDay * 100 + 60 })
      })
      act(() => {
        fireEvent.mouseMove(cells[toDay], point(toDay))
      })
      act(() => {
        fireEvent.mouseUp(cells[toDay], point(toDay))
      })
    }

    it('onSelectSlot_firstClickOnOtherDay_selectsDayWithoutOpeningModal', async () => {
      mockedDaily.mockResolvedValue(
        dailyRes([
          { date: '2026-09-16', income: 7000, expense: 2000, net: 5000 },
          { date: '2026-09-30', income: 0, expense: 3000, net: -3000 },
        ]),
      )
      renderPage()
      await waitFor(() => expect(totals('9월 30일 (수)')).toHaveTextContent('합계-3,000원'))
      // 기본 선택일(오늘)이 강조되어 있다
      expect(monthCell(4, 3)).toHaveClass('lifelog-selected-day')

      // 9월 격자 3번째 주(9/13~9/19)의 수요일 = 9/16
      act(() => clickMonthCell(2, 3))

      await waitFor(() => expect(totals('9월 16일 (수)')).toHaveTextContent('수입7,000원지출2,000원합계5,000원'))
      expect(within(panel()).queryByRole('heading', { name: '9월 30일 (수)' })).not.toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(monthCell(2, 3)).toHaveClass('lifelog-selected-day')
      expect(monthCell(4, 3)).not.toHaveClass('lifelog-selected-day')
      expect(document.querySelectorAll('.lifelog-selected-day')).toHaveLength(1)
    })

    it('onSelectSlot_secondClickOnSelectedDay_opensModalWithThatDate', async () => {
      renderPage()
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))
      act(() => clickMonthCell(2, 3))
      await waitFor(() => expect(within(panel()).getByRole('heading', { name: '9월 16일 (수)' })).toBeInTheDocument())
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

      act(() => clickMonthCell(2, 3))

      const dialog = await screen.findByRole('dialog', { name: '새 일정' })
      expect(within(dialog).getByLabelText('시작', { exact: true })).toHaveValue('2026-09-16')
      expect(within(dialog).getByLabelText('종일')).toBeChecked()
      // 연달아 누른 두 번째 클릭(doubleClick 으로 올 수도 있다)에도 창은 하나만 열린다
      expect(screen.getAllByRole('dialog')).toHaveLength(1)
    })

    it('onSelectSlot_firstClickOnToday_opensModalImmediately', async () => {
      renderPage()
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))

      // 오늘(9/30)은 기본 선택일이라 첫 클릭에 바로 열린다
      act(() => clickMonthCell(4, 3))

      const dialog = await screen.findByRole('dialog', { name: '새 일정' })
      expect(within(dialog).getByLabelText('시작', { exact: true })).toHaveValue('2026-09-30')
    })

    it('onSelectSlot_clickWhileModalOpen_keepsSingleModal', async () => {
      const user = userEvent.setup()
      renderPage()
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))
      act(() => clickMonthCell(4, 3))
      await screen.findByRole('dialog', { name: '새 일정' })

      // 오늘 칸 더블클릭의 두 번째 알림처럼 창이 열린 뒤 다시 칸 선택이 와도 무시한다
      act(() => clickMonthCell(2, 3))
      await new Promise((r) => setTimeout(r, 0))

      expect(screen.getAllByRole('dialog')).toHaveLength(1)
      expect(within(panel()).getByRole('heading', { name: '9월 30일 (수)' })).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: '취소' }))
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('onSelectSlot_dragAcrossDays_opensModalAndSelectsStartDay', async () => {
      renderPage()
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))

      // 9/14(월) ~ 9/16(수) 를 끌어 고른다 — 선택일과 달라도 바로 창이 열린다
      dragMonthCells(2, 1, 3)

      const dialog = await screen.findByRole('dialog', { name: '새 일정' })
      expect(within(dialog).getByLabelText('시작', { exact: true })).toHaveValue('2026-09-14')
      expect(within(dialog).getByLabelText('종료', { exact: true })).toHaveValue('2026-09-16')
      expect(within(panel()).getByRole('heading', { name: '9월 14일 (월)' })).toBeInTheDocument()
    })

    it('onCreateClick_afterSelectingDay_fillsSelectedDateWithNextHour', async () => {
      const user = userEvent.setup()
      renderPage()
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))
      act(() => clickMonthCell(2, 3))
      await waitFor(() => expect(within(panel()).getByRole('heading', { name: '9월 16일 (수)' })).toBeInTheDocument())

      await user.click(screen.getByRole('button', { name: /만들기/ }))

      const dialog = screen.getByRole('dialog', { name: '새 일정' })
      expect(within(dialog).getByLabelText('시작', { exact: true })).toHaveValue('2026-09-16')
      // 시간은 기존 규칙(지금 10:00 의 다음 정시부터 1시간)
      expect(within(dialog).getByLabelText('시작 시간')).toHaveValue('11:00')
      expect(within(dialog).getByLabelText('종료 시간')).toHaveValue('12:00')
    })

    it('onSelectSlot_expensesLayerOff_keepsSelectThenOpenRule', async () => {
      localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify({ expenses: false }))
      renderPage()
      await waitFor(() => expect(mockedSpecialDays).toHaveBeenCalled())

      act(() => clickMonthCell(2, 3))
      await waitFor(() => expect(monthCell(2, 3)).toHaveClass('lifelog-selected-day'))
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

      act(() => clickMonthCell(2, 3))
      expect(await screen.findByRole('dialog', { name: '새 일정' })).toBeInTheDocument()
    })

    it('dayPropGetter_weekView_highlightsSelectedDayColumnAndHeader', async () => {
      const user = userEvent.setup()
      renderPage()

      await switchView(user, '주')

      // 오늘(9/30 수) 열과 머리글이 강조된다
      const highlighted = Array.from(document.querySelectorAll('.lifelog-selected-day'))
      expect(highlighted.some((el) => el.classList.contains('rbc-day-slot'))).toBe(true)
      expect(highlighted.some((el) => el.classList.contains('rbc-header'))).toBe(true)
      expect(document.querySelectorAll('.rbc-day-slot.lifelog-selected-day')).toHaveLength(1)
    })

    it('dayPropGetter_dayView_doesNotHighlight', async () => {
      const user = userEvent.setup()
      renderPage()

      await switchView(user, '일')

      expect(document.querySelector('.lifelog-selected-day')).toBeNull()
    })

    it('onNavigate_afterSelectingDay_resetsToDefaultDay', async () => {
      const user = userEvent.setup()
      renderPage()
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))
      act(() => clickMonthCell(2, 3))
      await waitFor(() => expect(within(panel()).getByRole('heading', { name: '9월 16일 (수)' })).toBeInTheDocument())

      await user.click(screen.getByRole('button', { name: '다음' }))
      expect(within(panel()).getByRole('heading', { name: '10월 1일 (목)' })).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: '이전' }))
      // 다시 9월로 와도 고른 날(9/16)이 아니라 기본값(오늘)
      expect(within(panel()).getByRole('heading', { name: '9월 30일 (수)' })).toBeInTheDocument()
      expect(monthCell(4, 3)).toHaveClass('lifelog-selected-day')
      expect(monthCell(2, 3)).not.toHaveClass('lifelog-selected-day')
      // 초기화된 선택일(오늘)이 아닌 9/16 을 다시 누르면 선택만 된다
      act(() => clickMonthCell(2, 3))
      await waitFor(() => expect(within(panel()).getByRole('heading', { name: '9월 16일 (수)' })).toBeInTheDocument())
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('onNavigate_prevMonth_defaultsToFirstDayOfThatMonth', async () => {
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: '이전' }))

      expect(within(panel()).getByRole('heading', { name: '8월 1일 (토)' })).toBeInTheDocument()
      expect(within(panel()).getByRole('heading', { name: '8월 전체' })).toBeInTheDocument()
      expect(within(panel()).getByRole('link', { name: '가계부 보기' })).toHaveAttribute('href', '/expenses?month=2026-08')
    })

    it('render_expensesLayerSavedOff_hidesPanelAndRequestsNoSummaryInAnyView', async () => {
      const user = userEvent.setup()
      localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify({ expenses: false }))
      renderPage()
      await waitFor(() => expect(mockedSpecialDays).toHaveBeenCalled())

      await switchView(user, '주')
      await waitFor(() => expect(mockedSpecialDays).toHaveBeenCalledTimes(2))

      expect(screen.queryByRole('region', { name: '가계부 요약' })).not.toBeInTheDocument()
      expect(mockedDaily).not.toHaveBeenCalled()
      expect(mockedMonthly).not.toHaveBeenCalled()
    })

    it('toggleLayer_expensesOffInWeekView_hidesPanelWithoutRefetching', async () => {
      const user = userEvent.setup()
      mockedMonthly.mockResolvedValue(monthlyRes(1000, 0))
      renderPage()
      await switchView(user, '주')
      await waitFor(() => expect(mockedMonthly).toHaveBeenCalledTimes(1))

      await user.click(layerButton('가계부'))

      expect(screen.queryByRole('region', { name: '가계부 요약' })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: '다음' }))
      await waitFor(() => expect(mockedSpecialDays).toHaveBeenCalledTimes(3))
      expect(mockedMonthly).toHaveBeenCalledTimes(1)
    })

    it('quickEntry_expenseSavedInWeekView_refetchesMonthlySummary', async () => {
      const user = userEvent.setup()
      mockedCategories.mockResolvedValue({
        data: { success: true, data: [{ id: 3, type: 'EXPENSE', name: '식비' }] },
      } as never)
      mockedCreateExpense.mockResolvedValue({} as never)
      mockedMonthly.mockResolvedValueOnce(monthlyRes(1000, 2000)).mockResolvedValueOnce(monthlyRes(1000, 14000))
      renderPage()
      await switchView(user, '주')
      await waitFor(() => expect(totals('9월 전체')).toHaveTextContent('합계-1,000원'))

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(within(screen.getByRole('group', { name: '입력 종류' })).getByRole('button', { name: '가계부' }))
      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      await user.selectOptions(await within(dialog).findByLabelText('카테고리'), '3')
      await user.type(within(dialog).getByPlaceholderText('금액 (원)'), '12000')
      await user.click(within(dialog).getByRole('button', { name: '저장' }))

      await waitFor(() => expect(mockedMonthly).toHaveBeenCalledTimes(2))
      await waitFor(() => expect(totals('9월 전체')).toHaveTextContent('수입1,000원지출14,000원합계-13,000원'))
    })

    it('quickEntry_expenseSavedInMonthView_updatesTotalsFromReloadedDaily', async () => {
      const user = userEvent.setup()
      mockedCategories.mockResolvedValue({
        data: { success: true, data: [{ id: 3, type: 'EXPENSE', name: '식비' }] },
      } as never)
      mockedCreateExpense.mockResolvedValue({} as never)
      mockedDaily
        .mockResolvedValueOnce(dailyRes([]))
        .mockResolvedValueOnce(dailyRes([{ date: '2026-09-30', income: 0, expense: 12000, net: -12000 }]))
      renderPage()
      await waitFor(() => expect(totals('9월 전체')).toHaveTextContent('합계0원'))

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(within(screen.getByRole('group', { name: '입력 종류' })).getByRole('button', { name: '가계부' }))
      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      await user.selectOptions(await within(dialog).findByLabelText('카테고리'), '3')
      await user.type(within(dialog).getByPlaceholderText('금액 (원)'), '12000')
      await user.click(within(dialog).getByRole('button', { name: '저장' }))

      await waitFor(() => expect(totals('9월 전체')).toHaveTextContent('지출12,000원합계-12,000원'))
      expect(totals('9월 30일 (수)')).toHaveTextContent('지출12,000원합계-12,000원')
      // 월간 보기는 저장 후에도 월별 요약 API 를 부르지 않는다
      expect(mockedMonthly).not.toHaveBeenCalled()
    })

    it('load_monthlyFailsInWeekView_showsErrorOnlyInPanelAndKeepsCalendar', async () => {
      const user = userEvent.setup()
      mockedMonthly.mockRejectedValue(new Error('Network Error'))
      mockedDaily.mockResolvedValue(dailyRes([{ date: '2026-09-30', income: 0, expense: 3000, net: -3000 }]))
      mockedRange.mockResolvedValue([summary()])
      renderPage()

      await switchView(user, '주')

      await waitFor(() => expect(within(totals('9월 전체')).getByText('불러오지 못했습니다.')).toBeInTheDocument())
      // 선택한 날은 일별 합계로 정상 표시
      expect(totals('9월 30일 (수)')).toHaveTextContent('합계-3,000원')
      expect(await screen.findByText('팀 회의')).toBeInTheDocument()
      expect(screen.getByText('-3,000')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(screen.getByRole('status')).not.toHaveTextContent('가계부')
    })
  })

  describe('캘린더에서 바로 입력', () => {
    const entryTab = (name: string) => within(screen.getByRole('group', { name: '입력 종류' })).getByRole('button', { name })

    it('onCreateClick_showsEntryTabsWithEventSelected', async () => {
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /만들기/ }))

      expect(entryTab('일정')).toHaveAttribute('aria-pressed', 'true')
      expect(entryTab('구직활동')).toHaveAttribute('aria-pressed', 'false')
      expect(screen.getByRole('dialog', { name: '새 일정' })).toBeInTheDocument()
    })

    it('onSelectEvent_editMode_hasNoEntryTabs', async () => {
      const user = userEvent.setup()
      mockedRange.mockResolvedValue([summary()])
      mockedGet.mockResolvedValue({
        data: { success: true, data: { ...summary(), description: null, location: null, createdAt: '', updatedAt: '' } },
      } as never)
      renderPage()

      await user.click(await screen.findByText('팀 회의'))

      expect(await screen.findByRole('dialog', { name: '일정 수정' })).toBeInTheDocument()
      expect(screen.queryByRole('group', { name: '입력 종류' })).not.toBeInTheDocument()
    })

    it('onTabJobApplication_savesWithSelectedDateAndReloadsOverlay', async () => {
      const user = userEvent.setup()
      mockedCreateJob.mockResolvedValue({} as never)
      renderPage()
      await waitFor(() => expect(mockedJobs).toHaveBeenCalledTimes(1))

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('구직활동'))

      const dialog = screen.getByRole('dialog', { name: '구직활동 추가' })
      // 만들기는 오늘(9/30) 기준
      expect(within(dialog).getByLabelText('지원일')).toHaveValue('2026-09-30')
      await user.type(within(dialog).getByLabelText('회사명'), '라이프로그')
      await user.type(within(dialog).getByLabelText('지원 직무'), '백엔드')
      await user.click(within(dialog).getByRole('button', { name: '저장' }))

      await waitFor(() =>
        expect(mockedCreateJob).toHaveBeenCalledWith({
          companyName: '라이프로그',
          position: '백엔드',
          status: 'APPLIED',
          appliedAt: '2026-09-30',
          jobPostingUrl: undefined,
          memo: undefined,
        }),
      )
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      await waitFor(() => expect(mockedJobs).toHaveBeenCalledTimes(2))
    })

    it('onTabJobApplication_blankCompany_showsErrorWithoutSaving', async () => {
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('구직활동'))
      const dialog = screen.getByRole('dialog', { name: '구직활동 추가' })
      await user.type(within(dialog).getByLabelText('회사명'), '   ')
      await user.click(within(dialog).getByRole('button', { name: '저장' }))

      expect(await within(dialog).findByText('회사명은 필수입니다.')).toBeInTheDocument()
      expect(mockedCreateJob).not.toHaveBeenCalled()
    })

    it('onTabExpense_loadsCategoriesOnceAndSavesWithSelectedDate', async () => {
      const user = userEvent.setup()
      mockedCategories.mockResolvedValue({
        data: { success: true, data: [{ id: 3, type: 'EXPENSE', name: '식비' }] },
      } as never)
      mockedCreateExpense.mockResolvedValue({} as never)
      renderPage()
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('가계부'))

      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      await user.selectOptions(await within(dialog).findByLabelText('카테고리'), '3')
      await user.type(within(dialog).getByPlaceholderText('금액 (원)'), '12000')
      expect(within(dialog).getByLabelText('날짜')).toHaveValue('2026-09-30')
      // 탭을 오가도 카테고리는 다시 불러오지 않는다
      await user.click(entryTab('일정'))
      await user.click(entryTab('가계부'))
      await user.selectOptions(await screen.findByLabelText('카테고리'), '3')
      await user.type(screen.getByPlaceholderText('금액 (원)'), '12000')
      await user.click(within(screen.getByRole('dialog', { name: '내역 추가' })).getByRole('button', { name: '저장' }))

      await waitFor(() =>
        expect(mockedCreateExpense).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'EXPENSE', categoryId: 3, amount: 12000, transactionDate: '2026-09-30' }),
        ),
      )
      expect(mockedCategories).toHaveBeenCalledTimes(1)
      await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(2))
    })

    it('quickEntry_expenseTab_showsPaymentMethodSelect', async () => {
      const user = userEvent.setup()
      mockedCategories.mockResolvedValue({
        data: { success: true, data: [{ id: 3, type: 'EXPENSE', name: '식비' }] },
      } as never)
      mockedCreateExpense.mockResolvedValue({} as never)
      renderPage()

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('가계부'))

      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      await user.selectOptions(await within(dialog).findByLabelText('카테고리'), '3')
      await user.type(within(dialog).getByPlaceholderText('금액 (원)'), '12000')
      await user.selectOptions(within(dialog).getByLabelText('결제수단'), 'CREDIT_CARD')
      await user.click(within(dialog).getByRole('button', { name: '저장' }))

      await waitFor(() =>
        expect(mockedCreateExpense).toHaveBeenCalledWith(expect.objectContaining({ paymentMethod: 'CREDIT_CARD' })),
      )
    })

    it('onTabExpense_noCategories_addDefaultsShowsCategorySelect', async () => {
      const user = userEvent.setup()
      mockedCategories.mockResolvedValue({ data: { success: true, data: [] } } as never)
      mockedAddDefaults.mockResolvedValue({
        data: { success: true, data: [{ id: 5, type: 'EXPENSE', name: '식비' }] },
      } as never)
      renderPage()

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('가계부'))
      await user.click(await screen.findByRole('button', { name: '기본 카테고리 추가' }))

      const select = await screen.findByLabelText('카테고리')
      expect(within(select).getByRole('option', { name: '식비' })).toBeInTheDocument()
      expect(mockedAddDefaults).toHaveBeenCalledTimes(1)
    })

    it('onTabExpense_whileLoadingCategories_showsLoadingAndDisablesSave', async () => {
      const user = userEvent.setup()
      mockedCategories.mockReturnValue(new Promise(() => {}) as never)
      renderPage()

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('가계부'))

      const dialog = screen.getByRole('dialog', { name: '내역 추가' })
      expect(within(dialog).getByText('카테고리를 불러오는 중…')).toBeInTheDocument()
      expect(within(dialog).queryByText('먼저 카테고리를 추가하세요.')).not.toBeInTheDocument()
      expect(within(dialog).getByRole('button', { name: '저장' })).toBeDisabled()
    })

    it('onTabExpense_categoriesFail_showsErrorAndRetriesNextTime', async () => {
      const user = userEvent.setup()
      mockedCategories.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValue({
        data: { success: true, data: [] },
      } as never)
      renderPage()

      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('가계부'))
      expect(await screen.findByText('카테고리를 불러오지 못했습니다.')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: '취소' }))
      await user.click(screen.getByRole('button', { name: /만들기/ }))
      await user.click(entryTab('가계부'))

      expect(await screen.findByText('먼저 카테고리를 추가하세요.')).toBeInTheDocument()
      expect(mockedCategories).toHaveBeenCalledTimes(2)
    })
  })

  describe('메뉴 서랍(폰)', () => {
    it('onMenuOpen_showsSidebarInDialogAndClosesOnNavigate', async () => {
      const user = userEvent.setup()
      renderWithRoutes()

      await user.click(screen.getByRole('button', { name: '메뉴 열기' }))
      const drawer = screen.getByRole('dialog', { name: '메뉴' })
      await user.click(within(drawer).getByRole('link', { name: '가계부' }))

      expect(await screen.findByTestId('location')).toHaveTextContent('/expenses')
    })

    it('onMenuOpen_escapeCloses', async () => {
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: '메뉴 열기' }))
      expect(screen.getByRole('dialog', { name: '메뉴' })).toBeInTheDocument()
      await user.keyboard('{Escape}')

      expect(screen.queryByRole('dialog', { name: '메뉴' })).not.toBeInTheDocument()
    })

    it('onMenuCreate_closesDrawerAndOpensEntryModal', async () => {
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: '메뉴 열기' }))
      await user.click(within(screen.getByRole('dialog', { name: '메뉴' })).getByRole('button', { name: /만들기/ }))

      expect(screen.queryByRole('dialog', { name: '메뉴' })).not.toBeInTheDocument()
      expect(screen.getByRole('dialog', { name: '새 일정' })).toBeInTheDocument()
    })
  })
})
