import { getEvents } from '@/api/events'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { Calendar, dayjsLocalizer } from 'react-big-calendar'

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

  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    if (!token) return
    const es = new EventSource(`/api/v1/sse/events?token=${token}`)
    es.addEventListener('REFRESH', loadEvents)
    es.onerror = () => es.close()
    return () => es.close()
  }, [loadEvents])

  return (
    <div className="h-screen p-4">
      <Calendar localizer={localizer} events={events} style={{ height: '100%' }} />
    </div>
  )
}
