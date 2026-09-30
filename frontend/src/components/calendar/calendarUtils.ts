import type { EventCategory, EventRequest, EventSummary } from '@/api/events'
import { resolveEventColor } from '@/constants/eventCategory'
import dayjs from 'dayjs'
import type { View } from 'react-big-calendar'

export interface CalendarEvent {
  id: number
  title: string
  start: Date
  end: Date
  allDay: boolean
  color: string
}

/** 보이는 날짜 범위 (월간 보기는 앞뒤 주의 다른 달 날짜 포함) */
export const visibleRange = (date: Date, view: View): { from: Date; to: Date } => {
  const d = dayjs(date)
  if (view === 'month') {
    return { from: d.startOf('month').startOf('week').toDate(), to: d.endOf('month').endOf('week').toDate() }
  }
  if (view === 'week') return { from: d.startOf('week').toDate(), to: d.endOf('week').toDate() }
  return { from: d.startOf('day').toDate(), to: d.endOf('day').toDate() }
}

/** 백엔드 일정 → 캘린더 표시용. 종료가 없으면 종일은 당일, 시간 일정은 1시간으로 본다 */
export const toCalendarEvent = (e: EventSummary): CalendarEvent => {
  const start = dayjs(e.startAt)
  const end = e.endAt ? dayjs(e.endAt) : e.allDay ? start.endOf('day') : start.add(1, 'hour')
  return {
    id: e.id,
    title: e.title,
    start: start.toDate(),
    end: end.toDate(),
    allDay: e.allDay,
    color: resolveEventColor(e.color, e.eventCategory),
  }
}

/** 일요일은 빨강, 토요일은 파랑 (네이버 캘린더식) */
export const weekdayTextClass = (date: Date): string => {
  const day = dayjs(date).day()
  if (day === 0) return 'text-red-500'
  if (day === 6) return 'text-blue-500'
  return 'text-gray-700'
}

export interface EventFormValues {
  title: string
  allDay: boolean
  startDate: string
  startTime: string
  endDate: string
  endTime: string
  eventCategory: EventCategory
  /** '' 이면 카테고리 기본 색 */
  color: string
  location: string
  description: string
}

/** 폼 값 → 요청 본문. 종일 일정은 시작일 00:00:00 ~ 종료일 23:59:59 로 저장한다 */
export const toEventRequest = (v: EventFormValues): EventRequest => {
  const startAt = v.allDay ? `${v.startDate}T00:00:00` : `${v.startDate}T${v.startTime}:00`
  const endAt = v.allDay ? `${v.endDate}T23:59:59` : `${v.endDate}T${v.endTime}:00`
  return {
    title: v.title.trim(),
    allDay: v.allDay,
    startAt,
    endAt,
    eventCategory: v.eventCategory,
    color: v.color || null,
    location: v.location.trim() || null,
    description: v.description.trim() || null,
  }
}
