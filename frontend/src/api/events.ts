import client from './client'
import dayjs from 'dayjs'

export type EventCategory = 'PERSONAL' | 'WORK' | 'REMINDER' | 'OTHER'

/** 목록 조회 응답 (백엔드 EventSummary) */
export interface EventSummary {
  id: number
  title: string
  startAt: string
  endAt: string | null
  allDay: boolean
  color: string | null
  eventCategory: EventCategory | null
}

/** 단건 조회 응답 (백엔드 EventResponse) */
export interface EventDetail extends EventSummary {
  description: string | null
  location: string | null
  createdAt: string
  updatedAt: string
}

/** 생성/수정 요청 (백엔드 EventRequest) — 날짜는 타임존 없는 LocalDateTime 문자열 */
export interface EventRequest {
  title: string
  description?: string | null
  startAt: string
  endAt?: string | null
  allDay: boolean
  location?: string | null
  color?: string | null
  eventCategory?: EventCategory | null
}

interface PagedEnvelope<T> {
  success: boolean
  data: T[]
  meta: { page: number; size: number; total: number; totalPages: number }
}

/** 백엔드 LocalDateTime 형식(타임존 없음)으로 변환한다 */
export const toLocalDateTime = (date: Date): string => dayjs(date).format('YYYY-MM-DDTHH:mm:ss')

// 백엔드 목록 size 상한
const PAGE_SIZE = 100
// 비정상 응답으로 무한 반복하지 않도록 페이지 수 상한을 둔다 (100 × 20 = 한 화면 2000건)
const MAX_PAGES = 20

/**
 * [from, to] 기간과 겹치는 일정을 모든 페이지에 걸쳐 가져온다.
 * 페이지 상한(2000건)에 걸려 일부만 가져왔으면 onTruncated 를 부른다
 */
export const getEventsInRange = async (
  from: Date,
  to: Date,
  onTruncated?: () => void,
): Promise<EventSummary[]> => {
  const params = { from: toLocalDateTime(from), to: toLocalDateTime(to), size: PAGE_SIZE }
  const events: EventSummary[] = []
  for (let page = 0; page < MAX_PAGES; page++) {
    const { data } = await client.get<PagedEnvelope<EventSummary>>('/api/v1/events', { params: { ...params, page } })
    events.push(...data.data)
    if (page + 1 >= data.meta.totalPages) return events
  }
  onTruncated?.()
  return events
}

export const getEvent = (id: number) => client.get<{ success: boolean; data: EventDetail }>(`/api/v1/events/${id}`)

export const createEvent = (body: EventRequest) =>
  client.post<{ success: boolean; data: EventDetail }>('/api/v1/events', body)

export const updateEvent = (id: number, body: EventRequest) =>
  client.put<{ success: boolean; data: EventDetail }>(`/api/v1/events/${id}`, body)

export const deleteEvent = (id: number) => client.delete(`/api/v1/events/${id}`)
