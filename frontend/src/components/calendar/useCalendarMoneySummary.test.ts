import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getMonthlySummary, type DailyItem } from '@/api/expenses'
import useCalendarMoneySummary, { defaultSelectedDay } from './useCalendarMoneySummary'
import type { CalendarOverlays } from './useCalendarOverlays'

vi.mock('@/api/expenses', () => ({ getMonthlySummary: vi.fn() }))

const mockedMonthly = vi.mocked(getMonthlySummary)

type Overlays = Pick<CalendarOverlays, 'expenses' | 'expensesStatus'>
type Range = { from: Date; to: Date }

const endOf = (y: number, m: number, d: number) => new Date(y, m, d, 23, 59, 59, 999)
/** 2026년 9월 월간 격자 — 8/30(일) ~ 10/3(토) */
const SEPT_GRID: Range = { from: new Date(2026, 7, 30), to: endOf(2026, 9, 3) }
/** 2026년 10월 월간 격자 — 9/27(일) ~ 10/31(토) */
const OCT_GRID: Range = { from: new Date(2026, 8, 27), to: endOf(2026, 9, 31) }
/** 2026년 12월 월간 격자 — 11/29(일) ~ 2027-01-02(토) */
const DEC_GRID: Range = { from: new Date(2026, 10, 29), to: endOf(2027, 0, 2) }
/** 2027년 1월 월간 격자 — 2026-12-27(일) ~ 2027-02-06(토) */
const JAN_GRID: Range = { from: new Date(2026, 11, 27), to: endOf(2027, 1, 6) }
/** 9/30 이 들어 있는 주 — 9/27(일) ~ 10/3(토), 9월과 10월에 걸친다 */
const WEEK_SEPT_OCT: Range = { from: new Date(2026, 8, 27), to: endOf(2026, 9, 3) }

const item = (date: string, income: number, expense: number): DailyItem => ({ date, income, expense, net: income - expense })
const overlays = (items: DailyItem[], expensesStatus: Overlays['expensesStatus'] = 'ready'): Overlays => ({
  expenses: new Map(items.map((d) => [d.date, d])),
  expensesStatus,
})
const monthlyRes = (totalIncome: number, totalExpense: number) =>
  ({
    data: { success: true, data: { from: '', to: '', totalIncome, totalExpense, net: totalIncome - totalExpense, months: [] } },
  }) as never

const deferred = <T,>() => {
  let resolve: (v: T) => void = () => {}
  let reject: (e: unknown) => void = () => {}
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

interface HookProps {
  anchor: Date
  range: Range
  overlays: Overlays
  selectedDay: string | null
  reloadKey?: number
}

const renderSummary = (initialProps: HookProps) =>
  renderHook((p: HookProps) => useCalendarMoneySummary(p.anchor, p.range, p.overlays, p.selectedDay, p.reloadKey), {
    initialProps,
  })

describe('defaultSelectedDay', () => {
  const today = new Date(2026, 8, 30, 10, 0)

  it('defaultSelectedDay_todayInViewedMonthAndRange_returnsToday', () => {
    expect(defaultSelectedDay(new Date(2026, 8, 15), SEPT_GRID, today)).toBe('2026-09-30')
  })

  it('defaultSelectedDay_todayInRangeButOtherMonth_returnsFirstDayOfMonth', () => {
    // 10월 격자에는 9/30 이 보이지만 보는 달은 10월
    expect(defaultSelectedDay(new Date(2026, 9, 20), OCT_GRID, today)).toBe('2026-10-01')
  })

  it('defaultSelectedDay_todayAndFirstDayOutOfRange_returnsAnchor', () => {
    // 9/13~9/19 주 보기 — 오늘(9/30)도 9/1 도 화면 밖
    const week = { from: new Date(2026, 8, 13), to: endOf(2026, 8, 19) }
    expect(defaultSelectedDay(new Date(2026, 8, 16), week, today)).toBe('2026-09-16')
  })

  it('defaultSelectedDay_weekSpanningMonthsAnchoredNextMonth_returnsFirstDayOfAnchorMonth', () => {
    // 9/27~10/3 주를 10/2 기준으로 보면 보는 달은 10월 — 오늘(9/30)은 다른 달이라 10/1
    expect(defaultSelectedDay(new Date(2026, 9, 2), WEEK_SEPT_OCT, today)).toBe('2026-10-01')
  })

  it('defaultSelectedDay_dayView_returnsThatDay', () => {
    const day = { from: new Date(2026, 9, 15), to: endOf(2026, 9, 15) }
    expect(defaultSelectedDay(new Date(2026, 9, 15), day, today)).toBe('2026-10-15')
  })

  it('defaultSelectedDay_decemberGridWithTodayInJanuary_returnsDecemberFirst', () => {
    // 연도 경계 — 오늘이 2027-01-01 이라 12월 격자에 보이지만 보는 달(12월)이 아니다
    expect(defaultSelectedDay(new Date(2026, 11, 10), DEC_GRID, new Date(2027, 0, 1, 9))).toBe('2026-12-01')
  })
})

describe('useCalendarMoneySummary', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 30, 10, 0))
    mockedMonthly.mockResolvedValue(monthlyRes(0, 0))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.resetAllMocks()
  })

  describe('월간 보기', () => {
    it('useCalendarMoneySummary_monthGrid_sumsOnlyViewedMonthDaysWithoutMonthlyRequest', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([
          item('2026-08-30', 0, 111),
          item('2026-08-31', 0, 999),
          item('2026-09-05', 50000, 12000),
          item('2026-09-30', 0, 3000),
          item('2026-10-01', 0, 777),
          item('2026-10-03', 222, 0),
        ]),
        selectedDay: null,
      })

      expect(result.current).toEqual({
        month: '2026-09',
        monthTotals: { income: 50000, expense: 15000, net: 35000 },
        monthStatus: 'ready',
        day: '2026-09-30',
        dayTotals: { income: 0, expense: 3000, net: -3000 },
        dayStatus: 'ready',
      })
      expect(mockedMonthly).not.toHaveBeenCalled()
    })

    it('useCalendarMoneySummary_selectedDayWithoutEntries_returnsZeroTotals', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([item('2026-09-05', 50000, 12000)]),
        selectedDay: '2026-09-10',
      })

      expect(result.current.day).toBe('2026-09-10')
      expect(result.current.dayTotals).toEqual({ income: 0, expense: 0, net: 0 })
    })

    it('useCalendarMoneySummary_noEntriesInMonth_returnsZeroMonthTotals', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([item('2026-08-31', 0, 999)]),
        selectedDay: null,
      })

      expect(result.current.monthTotals).toEqual({ income: 0, expense: 0, net: 0 })
      expect(result.current.monthStatus).toBe('ready')
    })

    it('useCalendarMoneySummary_selectedOffRangeDayInGrid_returnsThatDayTotals', () => {
      // 9월 격자 안의 10/1 칸을 고르면 달 합계는 9월, 날 합계는 10/1
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([item('2026-10-01', 0, 777)]),
        selectedDay: '2026-10-01',
      })

      expect(result.current.month).toBe('2026-09')
      expect(result.current.day).toBe('2026-10-01')
      expect(result.current.dayTotals).toEqual({ income: 0, expense: 777, net: -777 })
    })

    it('useCalendarMoneySummary_selectedDayOutsideRange_fallsBackToDefaultDay', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([]),
        selectedDay: '2026-11-15',
      })

      expect(result.current.day).toBe('2026-09-30')
    })

    it('useCalendarMoneySummary_expensesNegativeNet_returnsNegativeMonthAndDayNet', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([item('2026-09-01', 1000, 5000), item('2026-09-30', 0, 2500)]),
        selectedDay: null,
      })

      expect(result.current.monthTotals).toEqual({ income: 1000, expense: 7500, net: -6500 })
      expect(result.current.dayTotals?.net).toBe(-2500)
    })

    it('useCalendarMoneySummary_decemberGrid_includesFirstAndLastDayAndExcludesNextYear', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 11, 15),
        range: DEC_GRID,
        overlays: overlays([
          item('2026-11-30', 0, 100),
          item('2026-12-01', 10000, 0),
          item('2026-12-31', 0, 4000),
          item('2027-01-01', 0, 50000),
        ]),
        selectedDay: '2026-12-31',
      })

      expect(result.current.month).toBe('2026-12')
      expect(result.current.monthTotals).toEqual({ income: 10000, expense: 4000, net: 6000 })
      expect(result.current.day).toBe('2026-12-31')
      expect(result.current.dayTotals).toEqual({ income: 0, expense: 4000, net: -4000 })
      expect(mockedMonthly).not.toHaveBeenCalled()
    })

    it('useCalendarMoneySummary_navigateDecemberToJanuary_switchesMonthAndExcludesPreviousYear', () => {
      const data = overlays([item('2026-12-31', 0, 4000), item('2027-01-01', 0, 50000), item('2027-01-31', 3000, 0)])
      const { result, rerender } = renderSummary({
        anchor: new Date(2026, 11, 15),
        range: DEC_GRID,
        overlays: data,
        selectedDay: null,
      })
      expect(result.current.day).toBe('2026-12-01')

      rerender({ anchor: new Date(2027, 0, 1), range: JAN_GRID, overlays: data, selectedDay: null })

      expect(result.current.month).toBe('2027-01')
      expect(result.current.monthTotals).toEqual({ income: 3000, expense: 50000, net: -47000 })
      expect(result.current.day).toBe('2027-01-01')
      expect(result.current.dayTotals).toEqual({ income: 0, expense: 50000, net: -50000 })
    })

    it('useCalendarMoneySummary_dailyLoading_returnsLoadingWithoutTotals', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([], 'loading'),
        selectedDay: null,
      })

      expect(result.current).toMatchObject({ monthTotals: null, monthStatus: 'loading', dayTotals: null, dayStatus: 'loading' })
    })

    it('useCalendarMoneySummary_dailyError_returnsErrorWithoutTotals', () => {
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: SEPT_GRID,
        overlays: overlays([], 'error'),
        selectedDay: null,
      })

      expect(result.current).toMatchObject({ monthTotals: null, monthStatus: 'error', dayTotals: null, dayStatus: 'error' })
    })
  })

  describe('주·일 보기', () => {
    it('useCalendarMoneySummary_weekView_loadsMonthTotalsFromMonthlySummary', async () => {
      mockedMonthly.mockResolvedValue(monthlyRes(1000, 2000))
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: WEEK_SEPT_OCT,
        overlays: overlays([item('2026-09-30', 0, 3000), item('2026-10-01', 0, 777)]),
        selectedDay: null,
      })

      expect(result.current.monthStatus).toBe('loading')
      expect(result.current.monthTotals).toBeNull()
      // 날 합계는 일별 합계에서 바로 꺼낸다
      expect(result.current.dayTotals).toEqual({ income: 0, expense: 3000, net: -3000 })

      await waitFor(() => expect(result.current.monthStatus).toBe('ready'))
      expect(result.current.monthTotals).toEqual({ income: 1000, expense: 2000, net: -1000 })
      expect(mockedMonthly).toHaveBeenCalledTimes(1)
      expect(mockedMonthly).toHaveBeenCalledWith({ from: '2026-09', to: '2026-09' })
    })

    it('useCalendarMoneySummary_dayView_requestsMonthOfThatDay', async () => {
      mockedMonthly.mockResolvedValue(monthlyRes(5, 0))
      const { result } = renderSummary({
        anchor: new Date(2026, 11, 31),
        range: { from: new Date(2026, 11, 31), to: endOf(2026, 11, 31) },
        overlays: overlays([]),
        selectedDay: null,
      })

      await waitFor(() => expect(result.current.monthStatus).toBe('ready'))
      expect(mockedMonthly).toHaveBeenCalledWith({ from: '2026-12', to: '2026-12' })
      expect(result.current.day).toBe('2026-12-31')
      expect(result.current.dayTotals).toEqual({ income: 0, expense: 0, net: 0 })
    })

    it('useCalendarMoneySummary_monthlyFails_returnsErrorOnlyForMonth', async () => {
      mockedMonthly.mockRejectedValue(new Error('Network Error'))
      const { result } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: WEEK_SEPT_OCT,
        overlays: overlays([item('2026-09-30', 0, 3000)]),
        selectedDay: null,
      })

      await waitFor(() => expect(result.current.monthStatus).toBe('error'))
      expect(result.current.monthTotals).toBeNull()
      expect(result.current.dayStatus).toBe('ready')
      expect(result.current.dayTotals).toEqual({ income: 0, expense: 3000, net: -3000 })
    })

    it('useCalendarMoneySummary_reloadKeyChanged_refetchesMonthlySummary', async () => {
      mockedMonthly.mockResolvedValueOnce(monthlyRes(1000, 0)).mockResolvedValueOnce(monthlyRes(1000, 9000))
      const props: HookProps = {
        anchor: new Date(2026, 8, 30),
        range: WEEK_SEPT_OCT,
        overlays: overlays([]),
        selectedDay: null,
        reloadKey: 0,
      }
      const { result, rerender } = renderSummary(props)
      await waitFor(() => expect(result.current.monthTotals?.net).toBe(1000))

      rerender({ ...props, reloadKey: 1 })

      await waitFor(() => expect(result.current.monthTotals).toEqual({ income: 1000, expense: 9000, net: -8000 }))
      expect(mockedMonthly).toHaveBeenCalledTimes(2)
    })

    it('useCalendarMoneySummary_expensesLayerOff_doesNotRequestMonthlySummary', () => {
      renderSummary({
        anchor: new Date(2026, 8, 30),
        range: WEEK_SEPT_OCT,
        overlays: overlays([], 'off'),
        selectedDay: null,
      })

      expect(mockedMonthly).not.toHaveBeenCalled()
    })

    it('useCalendarMoneySummary_monthChangedBeforeResponse_ignoresStaleResponse', async () => {
      const sept = deferred<unknown>()
      const oct = deferred<unknown>()
      mockedMonthly.mockReturnValueOnce(sept.promise as never).mockReturnValueOnce(oct.promise as never)
      const octWeek = { from: new Date(2026, 9, 4), to: endOf(2026, 9, 10) }
      const { result, rerender } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: WEEK_SEPT_OCT,
        overlays: overlays([]),
        selectedDay: null,
      })

      rerender({ anchor: new Date(2026, 9, 7), range: octWeek, overlays: overlays([]), selectedDay: null })
      oct.resolve(monthlyRes(700, 0))
      await waitFor(() => expect(result.current.monthTotals?.income).toBe(700))
      sept.resolve(monthlyRes(1, 0))
      await sept.promise
      await Promise.resolve()

      expect(result.current.month).toBe('2026-10')
      expect(result.current.monthTotals?.income).toBe(700)
    })

    it('useCalendarMoneySummary_switchToMonthView_usesDailyTotalsAndIgnoresPendingMonthly', async () => {
      const pending = deferred<unknown>()
      mockedMonthly.mockReturnValueOnce(pending.promise as never)
      const data = overlays([item('2026-09-05', 100, 0)])
      const { result, rerender } = renderSummary({
        anchor: new Date(2026, 8, 30),
        range: WEEK_SEPT_OCT,
        overlays: data,
        selectedDay: null,
      })

      rerender({ anchor: new Date(2026, 8, 30), range: SEPT_GRID, overlays: data, selectedDay: null })
      pending.resolve(monthlyRes(9999, 0))
      await pending.promise
      await Promise.resolve()

      expect(result.current.monthTotals).toEqual({ income: 100, expense: 0, net: 100 })
      expect(mockedMonthly).toHaveBeenCalledTimes(1)
    })
  })
})
