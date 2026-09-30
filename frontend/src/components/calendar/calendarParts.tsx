import dayjs from 'dayjs'
import { weekdayTextClass } from './calendarUtils'
import type { DateHeaderProps, HeaderProps, NavigateAction, ToolbarProps, View } from 'react-big-calendar'

const VIEW_LABELS: Partial<Record<View, string>> = { month: '월', week: '주', day: '일' }

/** 구글 캘린더식 상단 툴바: 오늘 / < > / 제목 / 월·주·일 전환 */
export function CalendarToolbar<TEvent extends object>({ label, onNavigate, onView, view, views }: ToolbarProps<TEvent>) {
  const nav = (action: NavigateAction) => () => onNavigate(action)
  const viewList = (Array.isArray(views) ? views : (Object.keys(views) as View[])).filter((v) => VIEW_LABELS[v])
  return (
    <div className="mb-3 flex flex-wrap items-center gap-2">
      <button
        type="button"
        onClick={nav('TODAY')}
        className="rounded-md border border-gray-300 px-4 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100"
      >
        오늘
      </button>
      <div className="flex items-center">
        <button
          type="button"
          onClick={nav('PREV')}
          aria-label="이전"
          className="flex h-9 w-9 items-center justify-center rounded-full text-xl text-gray-600 hover:bg-gray-100"
        >
          ‹
        </button>
        <button
          type="button"
          onClick={nav('NEXT')}
          aria-label="다음"
          className="flex h-9 w-9 items-center justify-center rounded-full text-xl text-gray-600 hover:bg-gray-100"
        >
          ›
        </button>
      </div>
      <h2 className="mr-auto text-xl font-normal text-gray-800" aria-live="polite">
        {label}
      </h2>
      <div role="group" aria-label="보기 전환" className="flex overflow-hidden rounded-md border border-gray-300">
        {viewList.map((v) => (
          <button
            key={v}
            type="button"
            onClick={() => onView(v)}
            aria-pressed={view === v}
            className={`px-4 py-1.5 text-sm ${view === v ? 'bg-blue-50 font-medium text-blue-700' : 'text-gray-700 hover:bg-gray-100'}`}
          >
            {VIEW_LABELS[v]}
          </button>
        ))}
      </div>
    </div>
  )
}

/** 월간 보기 요일 머리글 (일 월 화 …) */
export function MonthWeekdayHeader({ date, label }: HeaderProps) {
  return <span className={`text-xs font-medium ${weekdayTextClass(date)}`}>{label}</span>
}

/** 월간 보기 날짜 칸 머리글: 오늘은 파란 원, 다른 달 날짜는 흐리게 */
export function MonthDateHeader({ date, label, isOffRange, onDrillDown }: DateHeaderProps) {
  const isToday = dayjs(date).isSame(dayjs(), 'day')
  const colorClass = isToday ? 'bg-blue-600 text-white' : `${weekdayTextClass(date)} hover:bg-gray-100`
  return (
    <button
      type="button"
      onClick={onDrillDown}
      aria-label={dayjs(date).format('M월 D일 dddd')}
      aria-current={isToday ? 'date' : undefined}
      className={`mt-1 inline-flex h-6 min-w-6 items-center justify-center rounded-full px-1 text-xs ${colorClass} ${isOffRange && !isToday ? 'opacity-40' : ''}`}
    >
      {label}
    </button>
  )
}

/** 주간·일간 보기 날짜 머리글: 요일 + 큰 날짜 숫자 (오늘은 파란 원) */
export function DayColumnHeader({ date }: HeaderProps) {
  const d = dayjs(date)
  const isToday = d.isSame(dayjs(), 'day')
  return (
    <div className="flex flex-col items-center py-1" aria-current={isToday ? 'date' : undefined}>
      <span className={`text-xs ${isToday ? 'text-blue-600' : weekdayTextClass(date)}`}>{d.format('ddd')}</span>
      <span
        className={`mt-0.5 flex h-9 w-9 items-center justify-center rounded-full text-xl ${
          isToday ? 'bg-blue-600 text-white' : weekdayTextClass(date)
        }`}
      >
        {d.date()}
      </span>
    </div>
  )
}
