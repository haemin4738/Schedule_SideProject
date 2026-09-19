import { getEvents } from '@/api/events'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { Calendar, dayjsLocalizer } from 'react-big-calendar'

const localizer = dayjsLocalizer(dayjs)

export default function CalendarPage() {
  const [events, setEvents] = useState<{ title: string; start: Date; end: Date }[]>([])

  useEffect(() => {
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

  return (
    <div className="h-screen p-4">
      <Calendar localizer={localizer} events={events} style={{ height: '100%' }} />
    </div>
  )
}
