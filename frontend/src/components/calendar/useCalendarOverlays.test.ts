import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getDailySummary } from '@/api/expenses'
import { getJobApplicationsInRange, type JobApplicationSummary } from '@/api/jobApplications'
import { getSpecialDays, type SpecialDay } from '@/api/specialDays'
import { DEFAULT_LAYERS, type CalendarLayers } from './calendarLayers'
import useCalendarOverlays from './useCalendarOverlays'

vi.mock('@/api/specialDays', () => ({ getSpecialDays: vi.fn() }))
vi.mock('@/api/expenses', () => ({ getDailySummary: vi.fn() }))
vi.mock('@/api/jobApplications', () => ({ getJobApplicationsInRange: vi.fn() }))

const mockedSpecialDays = vi.mocked(getSpecialDays)
const mockedDaily = vi.mocked(getDailySummary)
const mockedJobs = vi.mocked(getJobApplicationsInRange)

const SEPT = { from: new Date(2026, 7, 30), to: new Date(2026, 9, 3, 23, 59, 59, 999) }
const OCT = { from: new Date(2026, 8, 27), to: new Date(2026, 9, 31, 23, 59, 59, 999) }

const chuseok: SpecialDay = { date: '2026-09-25', name: '추석', kind: 'HOLIDAY', holiday: true }
const solarTerm: SpecialDay = { date: '2026-09-23', name: '추분', kind: 'SOLAR_TERM', holiday: false }
const anniversary: SpecialDay = { date: '2026-09-25', name: '기념일', kind: 'ANNIVERSARY', holiday: false }
const job: JobApplicationSummary = {
  id: 7,
  companyName: '라이프로그',
  position: '백엔드',
  status: 'APPLIED',
  appliedAt: '2026-09-10',
}

const specialDaysRes = (data: SpecialDay[]) => ({ data: { success: true, data } }) as never
const dailyRes = (days: { date: string; income: number; expense: number; net: number }[]) =>
  ({ data: { success: true, data: { from: '', to: '', totalIncome: 0, totalExpense: 0, net: 0, days } } }) as never

const deferred = <T,>() => {
  let resolve: (v: T) => void = () => {}
  let reject: (e: unknown) => void = () => {}
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const renderOverlays = (range = SEPT, layers: CalendarLayers = DEFAULT_LAYERS) =>
  renderHook(({ range, layers }) => useCalendarOverlays(range, layers), { initialProps: { range, layers } })

describe('useCalendarOverlays', () => {
  beforeEach(() => {
    mockedSpecialDays.mockResolvedValue(specialDaysRes([]))
    mockedDaily.mockResolvedValue(dailyRes([]))
    mockedJobs.mockResolvedValue([])
  })

  afterEach(() => vi.resetAllMocks())

  it('useCalendarOverlays_allLayersOn_requestsEachLayerWithDateRange', async () => {
    renderOverlays()

    await waitFor(() => expect(mockedJobs).toHaveBeenCalled())
    expect(mockedSpecialDays).toHaveBeenCalledWith('2026-08-30', '2026-10-03')
    expect(mockedDaily).toHaveBeenCalledWith({ from: '2026-08-30', to: '2026-10-03' })
    expect(mockedJobs).toHaveBeenCalledWith('2026-08-30', '2026-10-03')
  })

  it('useCalendarOverlays_responses_groupsByDate', async () => {
    mockedSpecialDays.mockResolvedValue(specialDaysRes([solarTerm, chuseok, anniversary]))
    mockedDaily.mockResolvedValue(dailyRes([{ date: '2026-09-05', income: 0, expense: 12000, net: -12000 }]))
    mockedJobs.mockResolvedValue([job])

    const { result } = renderOverlays()

    await waitFor(() => expect(result.current.jobApplications).toEqual([job]))
    await waitFor(() => expect(result.current.expenses.size).toBe(1))
    expect(result.current.specialDays.get('2026-09-25')?.map((d) => d.name)).toEqual(['추석', '기념일'])
    expect(result.current.specialDays.get('2026-09-23')).toEqual([solarTerm])
    expect(result.current.expenses.get('2026-09-05')).toMatchObject({ expense: 12000 })
    expect(result.current.errors).toEqual([])
  })

  it('useCalendarOverlays_expensesAndJobsOff_doesNotRequestThemButStillRequestsSpecialDays', async () => {
    renderOverlays(SEPT, { ...DEFAULT_LAYERS, expenses: false, jobApplications: false, specialDays: false })

    await waitFor(() => expect(mockedSpecialDays).toHaveBeenCalledTimes(1))
    expect(mockedDaily).not.toHaveBeenCalled()
    expect(mockedJobs).not.toHaveBeenCalled()
  })

  it('useCalendarOverlays_toggledOff_hidesLoadedLayerAndIgnoresInFlightResponse', async () => {
    mockedDaily.mockResolvedValue(dailyRes([{ date: '2026-09-05', income: 0, expense: 1, net: -1 }]))
    const jobs = deferred<JobApplicationSummary[]>()
    mockedJobs.mockReturnValue(jobs.promise)
    const { result, rerender } = renderOverlays()
    await waitFor(() => expect(result.current.expenses.size).toBe(1))

    rerender({ range: SEPT, layers: { ...DEFAULT_LAYERS, expenses: false, jobApplications: false } })
    expect(result.current.expenses.size).toBe(0)

    await act(async () => jobs.resolve([job]))
    expect(result.current.jobApplications).toEqual([])
  })

  it('useCalendarOverlays_toggledOn_requestsLayer', async () => {
    const { rerender } = renderOverlays(SEPT, { ...DEFAULT_LAYERS, expenses: false })
    await waitFor(() => expect(mockedSpecialDays).toHaveBeenCalled())
    expect(mockedDaily).not.toHaveBeenCalled()

    rerender({ range: SEPT, layers: DEFAULT_LAYERS })

    await waitFor(() => expect(mockedDaily).toHaveBeenCalledTimes(1))
  })

  it('useCalendarOverlays_lateResponseForPreviousRange_isIgnored', async () => {
    const sept = deferred<unknown>()
    const oct = deferred<unknown>()
    mockedSpecialDays.mockReturnValueOnce(sept.promise as never).mockReturnValueOnce(oct.promise as never)
    const { result, rerender } = renderOverlays()

    rerender({ range: OCT, layers: DEFAULT_LAYERS })
    await act(async () => oct.resolve(specialDaysRes([{ ...chuseok, date: '2026-10-03', name: '개천절' }])))
    await act(async () => sept.resolve(specialDaysRes([chuseok])))

    expect(result.current.specialDays.has('2026-10-03')).toBe(true)
    expect(result.current.specialDays.has('2026-09-25')).toBe(false)
  })

  it('useCalendarOverlays_rangeChanged_hidesPreviousResultUntilNewResponse', async () => {
    mockedSpecialDays.mockResolvedValueOnce(specialDaysRes([chuseok]))
    const { result, rerender } = renderOverlays()
    await waitFor(() => expect(result.current.specialDays.has('2026-09-25')).toBe(true))

    mockedSpecialDays.mockReturnValueOnce(new Promise(() => {}) as never)
    rerender({ range: OCT, layers: DEFAULT_LAYERS })

    expect(result.current.specialDays.size).toBe(0)
  })

  it('useCalendarOverlays_oneLayerFails_isolatesErrorAndKeepsOthers', async () => {
    mockedSpecialDays.mockResolvedValue(specialDaysRes([chuseok]))
    mockedDaily.mockRejectedValue(new Error('500'))
    mockedJobs.mockResolvedValue([job])

    const { result } = renderOverlays()

    await waitFor(() => expect(result.current.errors).toEqual(['가계부 합계를 불러오지 못했습니다.']))
    await waitFor(() => expect(result.current.jobApplications).toEqual([job]))
    expect(result.current.specialDays.has('2026-09-25')).toBe(true)
    expect(result.current.expenses.size).toBe(0)
  })

  it('useCalendarOverlays_allLayersFail_reportsEachError', async () => {
    mockedSpecialDays.mockRejectedValue(new Error('x'))
    mockedDaily.mockRejectedValue(new Error('x'))
    mockedJobs.mockRejectedValue(new Error('x'))

    const { result } = renderOverlays()

    await waitFor(() => expect(result.current.errors).toHaveLength(3))
    expect(result.current.errors).toEqual([
      '공휴일·기념일을 불러오지 못했습니다.',
      '구직활동을 불러오지 못했습니다.',
      '가계부 합계를 불러오지 못했습니다.',
    ])
  })

  it('useCalendarOverlays_failedLayerToggledOff_dropsItsError', async () => {
    mockedDaily.mockRejectedValue(new Error('x'))
    const { result, rerender } = renderOverlays()
    await waitFor(() => expect(result.current.errors).toHaveLength(1))

    rerender({ range: SEPT, layers: { ...DEFAULT_LAYERS, expenses: false } })

    expect(result.current.errors).toEqual([])
  })
})
