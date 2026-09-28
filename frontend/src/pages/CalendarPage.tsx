import { isTokenExpired, refreshSession } from '@/api/client'
import { getEvents } from '@/api/events'
import { useAuthStore } from '@/store/authStore'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { Calendar, dayjsLocalizer } from 'react-big-calendar'
import { Link } from 'react-router-dom'

const localizer = dayjsLocalizer(dayjs)

export default function CalendarPage() {
  const [events, setEvents] = useState<{ title: string; start: Date; end: Date }[]>([])

  const loadEvents = useCallback(() => {
    getEvents().then(({ data }) => {
      setEvents(
        data.data.map((e) => ({
          title: e.title,
          start: new Date(e.startAt),
          end: new Date(e.endAt),
        })),
      )
    }).catch(() => {
      // 401은 client 인터셉터가 재발급/로그아웃을 처리한다. 그 외 오류는 다음 SSE 알림 때 다시 조회한다
    })
  }, [])

  useEffect(() => {
    loadEvents()
  }, [loadEvents])

  // 토큰이 재발급되면 새 토큰으로 SSE를 다시 연결한다 (만료 토큰으로 재연결 반복 방지)
  const accessToken = useAuthStore((state) => state.accessToken)

  useEffect(() => {
    if (!accessToken) return
    const es = new EventSource(`/api/v1/sse/events?token=${accessToken}`)
    // 재연결(토큰 재발급 등) 시 끊겨 있던 동안의 변경을 반영한다
    es.addEventListener('open', loadEvents)
    es.addEventListener('REFRESH', loadEvents)
    // 만료된 토큰으로 재연결하면 401을 받고 EventSource가 재시도를 멈춘다 → 토큰을 재발급해 새 토큰으로 다시 연결한다
    // SSE 서버 오류로 끊긴 경우엔 재발급하지 않는다 (재발급 → 재연결 → 실패 무한 반복 방지)
    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED && isTokenExpired(accessToken)) refreshSession().catch(() => {})
    }
    return () => es.close()
  }, [loadEvents, accessToken])

  return (
    <div className="flex h-screen flex-col p-4">
      <div className="mb-2 flex justify-end gap-3">
        <Link to="/job-applications" className="text-sm text-blue-500 hover:underline">
          구직활동
        </Link>
        <Link to="/expenses" className="text-sm text-blue-500 hover:underline">
          가계부
        </Link>
      </div>
      <div className="flex-1">
        <Calendar localizer={localizer} events={events} style={{ height: '100%' }} />
      </div>
    </div>
  )
}
