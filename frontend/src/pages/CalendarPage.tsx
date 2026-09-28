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
    es.addEventListener('REFRESH', loadEvents)
    return () => es.close()
  }, [loadEvents, accessToken])

  return (
    <div className="flex h-screen flex-col p-4">
      <div className="mb-2 flex justify-end">
        <Link to="/job-applications" className="text-sm text-blue-500 hover:underline">
          구직활동
        </Link>
      </div>
      <div className="flex-1">
        <Calendar localizer={localizer} events={events} style={{ height: '100%' }} />
      </div>
    </div>
  )
}
