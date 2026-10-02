import { getDailySummary, type DailyItem } from '@/api/expenses'
import { getJobApplicationsInRange, type JobApplicationSummary } from '@/api/jobApplications'
import { getSpecialDays, type SpecialDay } from '@/api/specialDays'
import dayjs from 'dayjs'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { CalendarLayers } from './calendarLayers'

const DATE_FORMAT = 'YYYY-MM-DD'

export interface CalendarOverlays {
  /** yyyy-MM-dd → 그날의 특일 */
  specialDays: Map<string, SpecialDay[]>
  /** yyyy-MM-dd → 그날의 수입·지출 합계 (내역이 있는 날만) */
  expenses: Map<string, DailyItem>
  jobApplications: JobApplicationSummary[]
  /** 레이어별 오류 문구 — 한 레이어가 실패해도 다른 레이어는 그대로 보인다 */
  errors: string[]
}

type Layer = 'specialDays' | 'expenses' | 'jobApplications'

interface LayerResult<T> {
  /** 어떤 기간(from|to)으로 불러온 결과인지 — 현재 기간과 다르면 보여주지 않는다 */
  key: string
  data: T
  error: string | null
}

const ERROR_MESSAGES: Record<Layer, string> = {
  specialDays: '공휴일·기념일을 불러오지 못했습니다.',
  expenses: '가계부 합계를 불러오지 못했습니다.',
  jobApplications: '구직활동을 불러오지 못했습니다.',
}

const groupByDate = <T extends { date: string }>(items: T[]): Map<string, T[]> => {
  const map = new Map<string, T[]>()
  for (const item of items) {
    const list = map.get(item.date)
    if (list) list.push(item)
    else map.set(item.date, [item])
  }
  return map
}

/**
 * 캘린더 위에 겹쳐 그리는 특일·가계부·구직활동을 보이는 기간에 맞춰 불러온다.
 * - 특일은 공휴일 토글을 꺼도 날짜 숫자를 빨갛게 칠하는 데 쓰므로 항상 불러온다
 * - 가계부·구직활동은 토글이 켜져 있을 때만 요청한다
 * - 레이어마다 요청 순번을 두어 늦게 도착한 이전 기간 응답이 현재 화면을 덮어쓰지 않게 한다
 * - reloadKey 가 바뀌면 가계부·구직활동을 다시 불러온다 (캘린더에서 새로 입력한 뒤)
 */
export default function useCalendarOverlays(
  range: { from: Date; to: Date },
  layers: CalendarLayers,
  reloadKey = 0,
): CalendarOverlays {
  const from = dayjs(range.from).format(DATE_FORMAT)
  const to = dayjs(range.to).format(DATE_FORMAT)
  const key = `${from}|${to}`

  const [specialDays, setSpecialDays] = useState<LayerResult<SpecialDay[]> | null>(null)
  const [expenses, setExpenses] = useState<LayerResult<DailyItem[]> | null>(null)
  const [jobApplications, setJobApplications] = useState<LayerResult<JobApplicationSummary[]> | null>(null)
  const seq = useRef<Record<Layer, number>>({ specialDays: 0, expenses: 0, jobApplications: 0 })

  useEffect(() => {
    const mine = ++seq.current.specialDays
    getSpecialDays(from, to)
      .then(({ data }) => {
        if (mine === seq.current.specialDays) setSpecialDays({ key, data: data.data, error: null })
      })
      .catch(() => {
        if (mine === seq.current.specialDays) setSpecialDays({ key, data: [], error: ERROR_MESSAGES.specialDays })
      })
  }, [from, to, key])

  const showExpenses = layers.expenses
  useEffect(() => {
    const mine = ++seq.current.expenses
    if (!showExpenses) return
    getDailySummary({ from, to })
      .then(({ data }) => {
        if (mine === seq.current.expenses) setExpenses({ key, data: data.data.days, error: null })
      })
      .catch(() => {
        if (mine === seq.current.expenses) setExpenses({ key, data: [], error: ERROR_MESSAGES.expenses })
      })
  }, [from, to, key, showExpenses, reloadKey])

  const showJobApplications = layers.jobApplications
  useEffect(() => {
    const mine = ++seq.current.jobApplications
    if (!showJobApplications) return
    getJobApplicationsInRange(from, to)
      .then((items) => {
        if (mine === seq.current.jobApplications) setJobApplications({ key, data: items, error: null })
      })
      .catch(() => {
        if (mine === seq.current.jobApplications)
          setJobApplications({ key, data: [], error: ERROR_MESSAGES.jobApplications })
      })
  }, [from, to, key, showJobApplications, reloadKey])

  return useMemo(() => {
    // 기간이 바뀐 직후(새 응답 전)나 토글을 끈 레이어는 비워 둔다
    const current = <T,>(result: LayerResult<T[]> | null, visible: boolean) =>
      visible && result?.key === key ? result : null
    const sd = current(specialDays, true)
    const ex = current(expenses, showExpenses)
    const ja = current(jobApplications, showJobApplications)
    return {
      specialDays: groupByDate(sd?.data ?? []),
      expenses: new Map((ex?.data ?? []).map((d) => [d.date, d])),
      jobApplications: ja?.data ?? [],
      errors: [sd?.error, ja?.error, ex?.error].filter((e): e is string => !!e),
    }
  }, [key, specialDays, expenses, jobApplications, showExpenses, showJobApplications])
}
