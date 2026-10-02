import { isTokenExpired, refreshSession } from '@/api/client'
import { getApiErrorMessage } from '@/api/errorMessage'
import { getEvent, getEventsInRange, MAX_EVENTS_IN_RANGE, type EventDetail } from '@/api/events'
import { addDefaultExpenseCategories, getExpenseCategories, type ExpenseCategory } from '@/api/expenses'
import {
  jobApplicationToCalendarEvent,
  toCalendarEvent,
  visibleRange,
  type CalendarEvent,
} from '@/components/calendar/calendarUtils'
import { loadLayers, saveLayers, type CalendarLayer, type CalendarLayers } from '@/components/calendar/calendarLayers'
import CalendarSidebar from '@/components/calendar/CalendarSidebar'
import EntryTypeTabs, { type EntryKind } from '@/components/calendar/EntryTypeTabs'
import { CalendarOverlayContext, type CalendarOverlayValue } from '@/components/calendar/calendarOverlayContext'
import useCalendarOverlays from '@/components/calendar/useCalendarOverlays'
import EventFormModal from '@/components/calendar/EventFormModal'
import {
  CalendarToolbar,
  DayColumnHeader,
  MonthDateHeader,
  MonthWeekdayHeader,
} from '@/components/calendar/calendarParts'
import ExpenseFormModal from '@/components/expenses/ExpenseFormModal'
import { useDialog } from '@/components/expenses/useDialog'
import JobApplicationFormModal from '@/components/jobApplications/JobApplicationFormModal'
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
import { useNavigate } from 'react-router-dom'

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

/** 새로 입력할 때는 입력 종류(일정·구직활동·가계부)를 탭으로 바꿀 수 있고, 고른 날짜는 그대로 쓴다 */
type ModalState =
  | { mode: 'create'; kind: EntryKind; start: Date; end: Date; allDay: boolean; switched?: boolean }
  | { mode: 'edit'; event: EventDetail }
  | null

/** 가계부 입력에 쓰는 카테고리 — 가계부 탭을 처음 열 때 불러온다 */
type CategoriesState = { list: ExpenseCategory[]; error: string | null } | null

/** 폰에서 왼쪽 패널을 여는 서랍 (데스크톱은 고정 사이드바) */
function SidebarDrawer({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  const ref = useDialog<HTMLDivElement>(onClose)
  return (
    <div
      className="fixed inset-0 z-40 bg-black/40 md:hidden"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label="메뉴"
        tabIndex={-1}
        className="h-full w-72 max-w-[85vw] overflow-y-auto bg-white shadow-xl outline-none"
      >
        {children}
      </div>
    </div>
  )
}

export default function CalendarPage() {
  const [date, setDate] = useState(() => new Date())
  const [view, setView] = useState<View>('month')
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [error, setError] = useState<string | null>(null)
  // 보이는 기간의 일정이 페이지 상한을 넘어 일부만 표시 중인지
  const [truncated, setTruncated] = useState(false)
  const [modal, setModal] = useState<ModalState>(null)
  const [layers, setLayers] = useState<CalendarLayers>(loadLayers)
  const [overlayReload, setOverlayReload] = useState(0)
  const [categories, setCategories] = useState<CategoriesState>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)

  // 서랍이 열린 채 화면이 넓어지면(창 크기 조절·태블릿 회전) 보이지 않는 서랍이 키보드를 가두지 않게 닫는다
  useEffect(() => {
    if (!drawerOpen || typeof window.matchMedia !== 'function') return
    const desktop = window.matchMedia('(min-width: 768px)')
    const close = () => {
      if (desktop.matches) setDrawerOpen(false)
    }
    close()
    desktop.addEventListener('change', close)
    return () => desktop.removeEventListener('change', close)
  }, [drawerOpen])
  const navigate = useNavigate()

  const range = useMemo(() => visibleRange(date, view), [date, view])
  const overlays = useCalendarOverlays(range, layers, overlayReload)

  const notices = [
    ...(truncated && layers.events ? [`일정이 너무 많아 앞의 ${MAX_EVENTS_IN_RANGE.toLocaleString('ko-KR')}개만 표시합니다.`] : []),
    ...overlays.errors,
  ]

  const toggleLayer = (layer: CalendarLayer) => {
    const next = { ...layers, [layer]: !layers[layer] }
    setLayers(next)
    saveLayers(next)
  }

  // 일정과 구직활동(지원일 종일 항목)을 한 달력에 함께 그린다.
  // 일정은 토글을 꺼도 계속 불러온다(SSE 재조회 유지, 다시 켤 때 바로 보이게). SSE 는 일정 전용이라 오버레이는 화면 이동 시에만 갱신된다
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
    let cut = false
    getEventsInRange(range.from, range.to, () => {
      cut = true
    })
      .then((list) => {
        if (seq !== requestSeq.current) return
        setEvents(list.map(toCalendarEvent))
        setTruncated(cut)
        setError(null)
      })
      .catch((err) => {
        // 401은 client 인터셉터가 재발급/로그아웃을 처리한다
        if (seq !== requestSeq.current) return
        // 이전 기간의 일정이 오류와 함께 남아 있지 않게 비운다
        setEvents([])
        setTruncated(false)
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
  const sawFirstOpen = useRef(false)

  useEffect(() => {
    if (!accessToken) return
    const es = new EventSource(`/api/v1/sse/events?token=${accessToken}`)
    const reload = () => loadRef.current()
    // 재연결(토큰 재발급 등) 시 끊겨 있던 동안의 변경을 반영한다.
    // 서버가 연결 즉시 CONNECTED 를 보내 open 이 바로 오므로, 화면을 처음 열 때의 open 은 이미 불러온 직후라 건너뛴다
    es.addEventListener('open', () => {
      if (!sawFirstOpen.current) {
        sawFirstOpen.current = true
        return
      }
      reload()
    })
    es.addEventListener('REFRESH', reload)
    // 만료된 토큰으로 재연결하면 401을 받고 EventSource가 재시도를 멈춘다 → 토큰을 재발급해 새 토큰으로 다시 연결한다
    // SSE 서버 오류로 끊긴 경우엔 재발급하지 않는다 (재발급 → 재연결 → 실패 무한 반복 방지)
    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED && isTokenExpired(accessToken)) refreshSession().catch(() => {})
    }
    return () => es.close()
  }, [accessToken])

  const openCreate = (start: Date, end: Date, allDay: boolean) =>
    setModal({ mode: 'create', kind: 'event', start, end, allDay })

  const changeEntryKind = (kind: EntryKind) =>
    setModal((current) =>
      current?.mode === 'create' && current.kind !== kind ? { ...current, kind, switched: true } : current,
    )

  // 가계부 탭을 처음 열 때 카테고리를 불러온다 (실패 상태는 입력 창을 닫을 때 지워 다음에 다시 시도한다)
  const needsCategories = modal?.mode === 'create' && modal.kind === 'expense'
  useEffect(() => {
    if (!needsCategories || categories !== null) return
    let cancelled = false
    getExpenseCategories()
      .then(({ data }) => {
        if (!cancelled) setCategories({ list: data.data, error: null })
      })
      .catch((err) => {
        if (!cancelled) setCategories({ list: [], error: getApiErrorMessage(err, '카테고리를 불러오지 못했습니다.') })
      })
    return () => {
      cancelled = true
    }
  }, [needsCategories, categories])

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

  const closeModal = () => {
    setModal(null)
    setCategories((current) => (current?.error ? null : current))
  }

  const onSaved = () => {
    setModal(null)
    loadEvents()
  }

  const onOverlaySaved = () => {
    setModal(null)
    setOverlayReload((n) => n + 1)
  }

  const entryTabs =
    modal?.mode === 'create' ? (
      <EntryTypeTabs value={modal.kind} onChange={changeEntryKind} focusSelected={modal.switched} />
    ) : undefined

  const sidebar = (onNavigate?: () => void) => (
    <CalendarSidebar layers={layers} onToggleLayer={toggleLayer} onCreate={onCreateClick} onNavigate={onNavigate} />
  )

  return (
    <div className="flex h-screen flex-col bg-white">
      <header className="flex items-center gap-2 border-b border-gray-200 px-3 py-2">
        <button
          type="button"
          aria-label="메뉴 열기"
          onClick={() => setDrawerOpen(true)}
          className="rounded-full p-2 text-gray-600 hover:bg-gray-100 md:hidden"
        >
          <span aria-hidden="true" className="block text-xl leading-none">
            ☰
          </span>
        </button>
        <h1 className="px-1 text-lg font-semibold text-gray-800">Lifelog</h1>
      </header>

      <div className="flex min-h-0 flex-1">
        {/* 데스크톱: 왼쪽 고정 패널 (약 20%) */}
        <aside className="hidden w-60 shrink-0 overflow-y-auto border-r border-gray-100 md:block">{sidebar()}</aside>

        <div className="flex min-w-0 flex-1 flex-col">
          {error && (
            <p role="alert" className="mx-4 mt-2 rounded bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </p>
          )}

          {/* live region 은 항상 두고 내용만 바꾼다 (내용과 함께 새로 삽입되면 스크린리더가 읽지 않을 수 있다) */}
          <div role="status" className={notices.length > 0 ? 'mx-4 mt-2' : 'sr-only'}>
            {notices.length > 0 && (
              <p className="rounded bg-amber-50 px-3 py-2 text-sm text-amber-800">{notices.join(' ')}</p>
            )}
          </div>

          <main className="lifelog-calendar min-h-0 flex-1 p-2 md:p-4">
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
        </div>
      </div>

      {drawerOpen && (
        <SidebarDrawer onClose={() => setDrawerOpen(false)}>{sidebar(() => setDrawerOpen(false))}</SidebarDrawer>
      )}

      {modal?.mode === 'edit' && (
        <EventFormModal
          event={modal.event}
          defaultStart={new Date()}
          defaultEnd={new Date()}
          onClose={closeModal}
          onSaved={onSaved}
        />
      )}
      {modal?.mode === 'create' && modal.kind === 'event' && (
        <EventFormModal
          event={null}
          defaultStart={modal.start}
          defaultEnd={modal.end}
          defaultAllDay={modal.allDay}
          header={entryTabs}
          onClose={closeModal}
          onSaved={onSaved}
        />
      )}
      {modal?.mode === 'create' && modal.kind === 'jobApplication' && (
        <JobApplicationFormModal
          defaultAppliedAt={dayjs(modal.start).format('YYYY-MM-DD')}
          header={entryTabs}
          onClose={closeModal}
          onSaved={onOverlaySaved}
        />
      )}
      {modal?.mode === 'create' && modal.kind === 'expense' && (
        <ExpenseFormModal
          expense={null}
          defaultDate={dayjs(modal.start).format('YYYY-MM-DD')}
          categories={categories?.list ?? []}
          categoriesLoading={categories === null}
          categoriesError={categories?.error ?? null}
          header={entryTabs}
          onAddDefaultCategories={async () => {
            const { data } = await addDefaultExpenseCategories()
            setCategories({ list: data.data, error: null })
          }}
          onClose={closeModal}
          onSaved={onOverlaySaved}
          onManageCategories={() => navigate('/expenses')}
        />
      )}
    </div>
  )
}
