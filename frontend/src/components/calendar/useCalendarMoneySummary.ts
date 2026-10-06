import { getMonthlySummary, type DailyItem } from '@/api/expenses'
import dayjs from 'dayjs'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { CalendarOverlays } from './useCalendarOverlays'

const DATE_FORMAT = 'YYYY-MM-DD'

export interface MoneyTotals {
  income: number
  expense: number
  net: number
}

export type MoneyStatus = 'loading' | 'ready' | 'error'

export interface CalendarMoneySummary {
  /** yyyy-MM — 선택한 날이 속한 달 (기본 선택일은 캘린더가 보고 있는 달 안이라, 고르지 않았으면 보고 있는 달) */
  month: string
  monthTotals: MoneyTotals | null
  monthStatus: MoneyStatus
  /** yyyy-MM-dd */
  day: string
  dayTotals: MoneyTotals | null
  dayStatus: MoneyStatus
}

const ZERO: MoneyTotals = { income: 0, expense: 0, net: 0 }

const toTotals = (item: DailyItem | undefined): MoneyTotals =>
  item ? { income: item.income, expense: item.expense, net: item.net } : ZERO

/**
 * 선택한 날이 없거나 화면 밖이면 고르는 기본 날짜.
 * 오늘 → 그 달 1일 → 캘린더 기준 날짜 순으로, 보고 있는 달에 속하고 화면(일별 합계를 불러온 기간) 안에 있는 첫 날짜.
 * 기준 날짜는 항상 그 달·화면 안이라 마지막 후보에서 반드시 정해진다 (화면 밖 날짜는 일별 합계가 없어 추가 요청이 필요해지므로 피한다)
 */
export const defaultSelectedDay = (anchor: Date, range: { from: Date; to: Date }, today = new Date()): string => {
  const month = dayjs(anchor)
  const from = dayjs(range.from).startOf('day')
  const to = dayjs(range.to).endOf('day')
  const candidates = [dayjs(today), month.startOf('month'), month]
  const pick = candidates.find(
    (d) => d.isSame(month, 'month') && !d.isBefore(from) && !d.isAfter(to),
  )
  return (pick ?? month).format(DATE_FORMAT)
}

/**
 * 사이드바 가계부 요약 — 데이터는 useCalendarOverlays 가 이미 불러온 화면 기간의 일별 합계를 재사용한다.
 * - 선택한 날: 일별 합계에서 꺼낸다 (내역이 없는 날은 0)
 * - 이번 달(선택한 날의 달): 화면이 그 달 전체를 덮으면(월간 보기의 같은 달) 일별 합계 중 그 달 날짜만 더한다 (6주 격자의 앞뒤 달 날짜 제외).
 *   주·일 보기나 격자의 앞뒤 달 칸을 골라 달 일부만 보일 때만 월별 요약 API 를 따로 부른다
 * - reloadKey 가 바뀌면(캘린더에서 가계부를 새로 입력한 뒤) 월별 요약도 다시 부른다
 */
export default function useCalendarMoneySummary(
  anchor: Date,
  range: { from: Date; to: Date },
  overlays: Pick<CalendarOverlays, 'expenses' | 'expensesStatus'>,
  selectedDay: string | null,
  reloadKey = 0,
): CalendarMoneySummary {
  const rangeFrom = dayjs(range.from).format(DATE_FORMAT)
  const rangeTo = dayjs(range.to).format(DATE_FORMAT)
  const inRange = selectedDay !== null && selectedDay >= rangeFrom && selectedDay <= rangeTo
  const day = inRange ? selectedDay : defaultSelectedDay(anchor, range)

  // '이번 달'은 선택한 날의 달 — 두 달에 걸친 주나 월간 격자의 앞뒤 달 칸을 고르면 그 달 합계를 보여 준다
  const monthStart = dayjs(day).startOf('month')
  const month = monthStart.format('YYYY-MM')
  const coversMonth =
    rangeFrom <= monthStart.format(DATE_FORMAT) && rangeTo >= monthStart.endOf('month').format(DATE_FORMAT)
  const needsMonthly = !coversMonth && overlays.expensesStatus !== 'off'

  const [monthly, setMonthly] = useState<{ month: string; totals: MoneyTotals | null } | null>(null)
  const seq = useRef(0)
  useEffect(() => {
    const mine = ++seq.current
    if (!needsMonthly) return
    getMonthlySummary({ from: month, to: month })
      .then(({ data }) => {
        const { totalIncome, totalExpense, net } = data.data
        if (mine === seq.current) setMonthly({ month, totals: { income: totalIncome, expense: totalExpense, net } })
      })
      .catch(() => {
        if (mine === seq.current) setMonthly({ month, totals: null })
      })
  }, [month, needsMonthly, reloadKey])

  const fromDaily = useMemo(() => {
    if (!coversMonth) return null
    const sum = { ...ZERO }
    for (const [date, item] of overlays.expenses) {
      if (!date.startsWith(month)) continue
      sum.income += item.income
      sum.expense += item.expense
      sum.net += item.net
    }
    return sum
  }, [coversMonth, overlays.expenses, month])

  const dailyStatus: MoneyStatus =
    overlays.expensesStatus === 'ready' ? 'ready' : overlays.expensesStatus === 'error' ? 'error' : 'loading'

  let monthTotals: MoneyTotals | null
  let monthStatus: MoneyStatus
  if (coversMonth) {
    monthTotals = dailyStatus === 'ready' ? fromDaily : null
    monthStatus = dailyStatus
  } else if (monthly?.month === month) {
    monthTotals = monthly.totals
    monthStatus = monthly.totals ? 'ready' : 'error'
  } else {
    monthTotals = null
    monthStatus = 'loading'
  }

  return {
    month,
    monthTotals,
    monthStatus,
    day,
    dayTotals: dailyStatus === 'ready' ? toTotals(overlays.expenses.get(day)) : null,
    dayStatus: dailyStatus,
  }
}
