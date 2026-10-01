import { isTokenExpired, refreshSession } from '@/api/client'
import { getApiErrorMessage } from '@/api/errorMessage'
import { getEvent, getEventsInRange, type EventDetail } from '@/api/events'
import {
  jobApplicationToCalendarEvent,
  toCalendarEvent,
  visibleRange,
  type CalendarEvent,
} from '@/components/calendar/calendarUtils'
import {
  CALENDAR_LAYER_OPTIONS,
  loadLayers,
  saveLayers,
  type CalendarLayer,
  type CalendarLayers,
} from '@/components/calendar/calendarLayers'
import { CalendarOverlayContext, type CalendarOverlayValue } from '@/components/calendar/calendarOverlayContext'
import useCalendarOverlays from '@/components/calendar/useCalendarOverlays'
import EventFormModal from '@/components/calendar/EventFormModal'
import {
  CalendarToolbar,
  DayColumnHeader,
  MonthDateHeader,
  MonthWeekdayHeader,
} from '@/components/calendar/calendarParts'
import LogoutButton from '@/components/LogoutButton'
import { readableTextColor } from '@/constants/eventCategory'
import { useAuthStore } from '@/store/authStore'
import dayjs from 'dayjs'
import 'dayjs/locale/ko'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Calendar,
  dayjsLocalizer,
  type EventProps,
  type Formats,
  type Messages,
  type SlotInfo,
  type View,
} from 'react-big-calendar'
import { Link, useNavigate } from 'react-router-dom'

dayjs.locale('ko')
const localizer = dayjsLocalizer(dayjs)

const VIEWS: View[] = ['month', 'week', 'day']

const messages: Messages<CalendarEvent> = {
  today: '오늘',
  previous: '이전',
  next: '다음',
  month: '월',
  week: '주',
  day: '일',
  date: '날짜',
  time: '시간',
  event: '일정',
  allDay: '종일',
  noEventsInRange: '이 기간에 일정이 없습니다.',
  showMore: (count) => `+${count}개 더보기`,
}

const formats: Formats = {
  monthHeaderFormat: 'YYYY년 M월',
  dayHeaderFormat: 'YYYY년 M월 D일 dddd',
  dayRangeHeaderFormat: ({ start, end }) =>
    dayjs(start).isSame(end, 'month')
      ? dayjs(start).format('YYYY년 M월')
      : `${dayjs(start).format('YYYY년 M월')} – ${dayjs(end).format('M월')}`,
  weekdayFormat: 'ddd',
  dayFormat: 'D ddd',
  timeGutterFormat: 'A h시',
  eventTimeRangeFormat: ({ start }) => dayjs(start).format('A h:mm'),
  agendaDateFormat: 'M월 D일 (ddd)',
}

type ModalState =
  | { mode: 'create'; start: Date; end: Date; allDay: boolean }
  | { mode: 'edit'; event: EventDetail }
  | null

const NAV_LINK = 'rounded-md px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100'

export default function CalendarPage() {
  const [date, setDate] = useState(() => new Date())
  const [view, setView] = useState<View>('month')
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [error, setError] = useState<string | null>(null)
  const [modal, setModal] = useState<ModalState>(null)
  const [layers, setLayers] = useState<CalendarLayers>(loadLayers)
  const navigate = useNavigate()

  const range = useMemo(() => visibleRange(date, view), [date, view])
  const overlays = useCalendarOverlays(range, layers)

  const toggleLayer = (layer: CalendarLayer) => {
    const next = { ...layers, [layer]: !layers[layer] }
    setLayers(next)
    saveLayers(next)
  }

  // 일정과 구직활동(지원일 종일 항목)을 한 달력에 함께 그린다
  const calendarItems = useMemo(
    () => [
      ...(layers.events ? events : []),
      ...(layers.jobApplications ? overlays.jobApplications.map(jobApplicationToCalendarEvent) : []),
    ],
    [events, layers.events, layers.jobApplications, overlays.jobApplications],
  )

  const overlayValue = useMemo<CalendarOverlayValue>(
    () => ({
      specialDays: overlays.specialDays,
      expenses: overlays.expenses,
      showSpecialDayNames: layers.specialDays,
    }),
    [overlays.specialDays, overlays.expenses, layers.specialDays],
  )
  // 늦게 도착한 이전 범위 응답이 현재 화면을 덮어쓰지 않도록 요청 순번을 둔다
  const requestSeq = useRef(0)

  const loadEvents = useCallback(() => {
    const seq = ++requestSeq.current
    getEventsInRange(range.from, range.to)
      .then((list) => {
        if (seq !== requestSeq.current) return
        setEvents(list.map(toCalendarEvent))
        setError(null)
      })
      .catch((err) => {
        // 401은 client 인터셉터가 재발급/로그아웃을 처리한다
        if (seq !== requestSeq.current) return
        // 이전 기간의 일정이 오류와 함께 남아 있지 않게 비운다
        setEvents([])
        setError(getApiErrorMessage(err, '일정을 불러오지 못했습니다.'))
      })
  }, [range])

  useEffect(() => {
    loadEvents()
  }, [loadEvents])

  // SSE 알림 때는 항상 현재 보고 있는 범위를 다시 불러온다
  const loadRef = useRef(loadEvents)
  useEffect(() => {
    loadRef.current = loadEvents
  }, [loadEvents])

  // 토큰이 재발급되면 새 토큰으로 SSE를 다시 연결한다 (만료 토큰으로 재연결 반복 방지)
  const accessToken = useAuthStore((state) => state.accessToken)

  useEffect(() => {
    if (!accessToken) return
    const es = new EventSource(`/api/v1/sse/events?token=${accessToken}`)
    const reload = () => loadRef.current()
    // 재연결(토큰 재발급 등) 시 끊겨 있던 동안의 변경을 반영한다
    es.addEventListener('open', reload)
    es.addEventListener('REFRESH', reload)
    // 만료된 토큰으로 재연결하면 401을 받고 EventSource가 재시도를 멈춘다 → 토큰을 재발급해 새 토큰으로 다시 연결한다
    // SSE 서버 오류로 끊긴 경우엔 재발급하지 않는다 (재발급 → 재연결 → 실패 무한 반복 방지)
    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED && isTokenExpired(accessToken)) refreshSession().catch(() => {})
    }
    return () => es.close()
  }, [accessToken])

  const openCreate = (start: Date, end: Date, allDay: boolean) => setModal({ mode: 'create', start, end, allDay })

  const onSelectSlot = (slot: SlotInfo) => {
    if (view === 'month') {
      // 월간 보기에서 고른 날짜(들)는 종일 일정으로 만든다. slot.end 는 다음 날 00:00(배타)이다
      openCreate(slot.start, dayjs(slot.end).subtract(1, 'millisecond').toDate(), true)
    } else {
      openCreate(slot.start, slot.end, false)
    }
  }

  // 일정을 빠르게 연달아 누르면 마지막으로 누른 일정만 연다
  const selectSeq = useRef(0)
  const onSelectEvent = useCallback(
    async (event: CalendarEvent) => {
      const seq = ++selectSeq.current
      // 구직활동은 구직활동 화면의 수정 폼으로 보낸다
      if (event.kind === 'jobApplication') {
        navigate(`/job-applications?id=${event.id}`)
        return
      }
      try {
      // 목록에는 설명·장소가 없으므로 수정 전에 단건을 조회한다
        const { data } = await getEvent(event.id)
        if (seq === selectSeq.current) setModal({ mode: 'edit', event: data.data })
      } catch (err) {
        if (seq === selectSeq.current) setError(getApiErrorMessage(err, '일정을 불러오지 못했습니다.'))
      }
    },
    [navigate],
  )

  // 월간 보기와 주·일 보기의 종일 줄 일정은 react-big-calendar 가 포커스를 주지 않으므로 키보드로 열 수 있는 요소로 감싼다
  const EventLabel = useMemo(
    () =>
      function EventLabel({ event, title }: EventProps<CalendarEvent>) {
        return (
          <span
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                e.stopPropagation()
                void onSelectEvent(event)
              }
            }}
            className="block truncate outline-none focus-visible:ring-2 focus-visible:ring-gray-900 focus-visible:ring-offset-1 focus-visible:ring-offset-white"
          >
            {title}
          </span>
        )
      },
    [onSelectEvent],
  )

  const onCreateClick = () => {
    const start = dayjs().add(1, 'hour').startOf('hour')
    openCreate(start.toDate(), start.add(1, 'hour').toDate(), false)
  }

  const onSaved = () => {
    setModal(null)
    loadEvents()
  }

  return (
    <div className="flex h-screen flex-col bg-white">
      <header className="flex flex-wrap items-center gap-3 border-b border-gray-200 px-4 py-2">
        <h1 className="text-lg font-semibold text-gray-800">Lifelog</h1>
        <button
          type="button"
          onClick={onCreateClick}
          className="flex items-center gap-1 rounded-full bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-md ring-1 ring-gray-200 hover:bg-gray-50"
        >
          <span aria-hidden="true" className="text-lg leading-none text-blue-600">
            +
          </span>
          만들기
        </button>
        <nav aria-label="주요 메뉴" className="ml-auto flex items-center gap-1">
          <Link to="/" aria-current="page" className={`${NAV_LINK} bg-blue-50 font-medium text-blue-700 hover:bg-blue-50`}>
            캘린더
          </Link>
          <Link to="/job-applications" className={NAV_LINK}>
            구직활동
          </Link>
          <Link to="/expenses" className={NAV_LINK}>
            가계부
          </Link>
        </nav>
        <LogoutButton />
      </header>

      {error && (
        <p role="alert" className="mx-4 mt-2 rounded bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {/* live region 은 항상 두고 내용만 바꾼다 (내용과 함께 새로 삽입되면 스크린리더가 읽지 않을 수 있다) */}
      <div role="status" className={overlays.errors.length > 0 ? 'mx-4 mt-2' : 'sr-only'}>
        {overlays.errors.length > 0 && (
          <p className="rounded bg-amber-50 px-3 py-2 text-sm text-amber-800">{overlays.errors.join(' ')}</p>
        )}
      </div>

      <div role="group" aria-label="캘린더에 표시할 항목" className="flex flex-wrap gap-2 px-4 pt-3">
        {CALENDAR_LAYER_OPTIONS.map(([layer, label]) => (
          <button
            key={layer}
            type="button"
            aria-pressed={layers[layer]}
            onClick={() => toggleLayer(layer)}
            className={`rounded-full border px-3 py-1 text-xs ${
              layers[layer]
                ? 'border-blue-200 bg-blue-50 font-medium text-blue-700'
                : 'border-gray-300 text-gray-500 hover:bg-gray-50'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <main className="lifelog-calendar min-h-0 flex-1 p-4">
        <CalendarOverlayContext value={overlayValue}>
          <Calendar<CalendarEvent>
            localizer={localizer}
            culture="ko"
            events={calendarItems}
            date={date}
            view={view}
            views={VIEWS}
            onNavigate={setDate}
            onView={setView}
            messages={messages}
            formats={formats}
            selectable
            popup
            onSelectSlot={onSelectSlot}
            onSelectEvent={onSelectEvent}
            // 주·일 보기의 일정은 포커스는 되지만 Enter 로 열리지 않아 직접 연결한다
            onKeyPressEvent={(event, e) => {
              const ke = e as React.KeyboardEvent<HTMLElement>
              if (ke.key === 'Enter' || ke.key === ' ') {
                ke.preventDefault()
                void onSelectEvent(event)
              }
            }}
            scrollToTime={dayjs().hour(8).minute(0).toDate()}
            eventPropGetter={(event) => ({
              style: { backgroundColor: event.color, color: readableTextColor(event.color) },
            })}
            components={{
              toolbar: CalendarToolbar,
              month: { header: MonthWeekdayHeader, dateHeader: MonthDateHeader, event: EventLabel },
              week: { header: DayColumnHeader, event: EventLabel },
              day: { header: DayColumnHeader, event: EventLabel },
            }}
            style={{ height: '100%' }}
          />
        </CalendarOverlayContext>
      </main>

      {modal && (
        <EventFormModal
          event={modal.mode === 'edit' ? modal.event : null}
          defaultStart={modal.mode === 'create' ? modal.start : new Date()}
          defaultEnd={modal.mode === 'create' ? modal.end : new Date()}
          defaultAllDay={modal.mode === 'create' ? modal.allDay : false}
          onClose={() => setModal(null)}
          onSaved={onSaved}
        />
      )}
    </div>
  )
}
