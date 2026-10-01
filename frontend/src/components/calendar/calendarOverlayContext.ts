import type { DailyItem } from '@/api/expenses'
import type { SpecialDay } from '@/api/specialDays'
import { createContext, useContext } from 'react'

export interface CalendarOverlayValue {
  specialDays: Map<string, SpecialDay[]>
  expenses: Map<string, DailyItem>
  /** 공휴일·기념일 토글 — 꺼도 공휴일 날짜 숫자는 빨갛게 유지하고 이름만 숨긴다 */
  showSpecialDayNames: boolean
}

const EMPTY: CalendarOverlayValue = { specialDays: new Map(), expenses: new Map(), showSpecialDayNames: false }

/** react-big-calendar 머리글 컴포넌트에는 임의 props 를 넘길 수 없어 Context 로 전달한다 */
export const CalendarOverlayContext = createContext<CalendarOverlayValue>(EMPTY)

export const useCalendarOverlay = () => useContext(CalendarOverlayContext)
