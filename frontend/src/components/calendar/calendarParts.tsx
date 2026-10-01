import dayjs from 'dayjs'
import type { MouseEvent, TouchEvent } from 'react'
import { Link } from 'react-router-dom'
import type { DailyItem } from '@/api/expenses'
import { useCalendarOverlay } from './calendarOverlayContext'
import { dateKey, weekdayTextClass } from './calendarUtils'
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

/** 그날의 공휴일(쉬는 날) 이름들 — 토글과 무관하게 날짜 숫자 색과 날짜 버튼 이름에 쓴다 */
const useHolidayNames = (date: Date): string[] => {
  const { specialDays } = useCalendarOverlay()
  return (specialDays.get(dateKey(date)) ?? []).filter((d) => d.holiday).map((d) => d.name)
}

const won = (amount: number) => amount.toLocaleString('ko-KR')

// react-big-calendar 는 document 의 mousedown/touchstart 로 칸 선택을 시작한다 → 링크를 누를 때 일정 만들기 창이 함께 뜨지 않게 막는다
const stopSlotSelection = (e: MouseEvent | TouchEvent) => e.stopPropagation()

const moneyLabel = (date: Date, money: DailyItem) =>
  // 보이는 글자(-12,345 +50,000)를 이름에 그대로 포함한다 (WCAG 2.5.3 Label in Name)
  `${dayjs(date).format('M월 D일')} 가계부${money.expense > 0 ? ` 지출 -${won(money.expense)}` : ''}${
    money.income > 0 ? ` 수입 +${won(money.income)}` : ''
  }`

function MoneyText({ money }: { money: DailyItem }) {
  return (
    <>
      {money.expense > 0 && <span className="text-red-500">-{won(money.expense)}</span>}
      {money.expense > 0 && money.income > 0 && ' '}
      {money.income > 0 && <span className="text-blue-500">+{won(money.income)}</span>}
    </>
  )
}

/**
 * 월간 칸 머리글 아래 특일(1줄)·가계부 합계(1줄).
 * react-big-calendar 는 칸에 일정을 몇 줄 그릴지 처음 그릴 때 머리글 높이로 한 번만 잰다.
 * 데이터가 늦게 와서 머리글이 커지면 넘친 일정이 '+N개 더보기' 없이 잘리므로, 내용과 상관없이 항상 두 줄 높이를 차지한다.
 */
function MonthDayOverlay({ date }: { date: Date }) {
  const { specialDays, expenses, showSpecialDayNames } = useCalendarOverlay()
  const key = dateKey(date)
  const names = showSpecialDayNames ? (specialDays.get(key) ?? []) : []
  const money = expenses.get(key)
  const line = 'block h-[15px] max-w-full truncate whitespace-nowrap'
  return (
    <div className="mt-0.5 flex flex-col items-center px-1 text-[11px] leading-[15px]">
      <span className={line} title={names.length > 0 ? names.map((d) => d.name).join(', ') : undefined}>
        {names.map((d, i) => (
          <span key={`${d.kind}-${d.name}`}>
            {i > 0 && <span className="text-gray-400"> · </span>}
            <span className={d.holiday ? 'text-red-500' : 'text-gray-500'}>{d.name}</span>
          </span>
        ))}
      </span>
      {money ? (
        <Link
          to={`/expenses?month=${dayjs(date).format('YYYY-MM')}`}
          onMouseDown={stopSlotSelection}
          onTouchStart={stopSlotSelection}
          aria-label={moneyLabel(date, money)}
          className={`${line} rounded px-1 hover:bg-gray-100`}
        >
          <MoneyText money={money} />
        </Link>
      ) : (
        <span className={line} aria-hidden="true" />
      )}
    </div>
  )
}

/**
 * 주·일 보기 머리글 아래 특일 이름·가계부 합계.
 * 머리글을 react-big-calendar 가 드릴다운 button 으로 감싸므로 링크 없이 글자로만 보여준다 (button 안 링크 중첩 방지)
 */
function ColumnDayOverlay({ date }: { date: Date }) {
  const { specialDays, expenses, showSpecialDayNames } = useCalendarOverlay()
  const key = dateKey(date)
  const names = showSpecialDayNames ? (specialDays.get(key) ?? []) : []
  const money = expenses.get(key)
  if (names.length === 0 && !money) return null
  return (
    <div className="mt-0.5 flex flex-col items-center gap-px px-1 text-[11px] leading-tight">
      {names.map((d) => (
        <span
          key={`${d.kind}-${d.name}`}
          title={d.name}
          className={`block max-w-full truncate ${d.holiday ? 'text-red-500' : 'text-gray-500'}`}
        >
          {d.name}
        </span>
      ))}
      {money && (
        <span className="max-w-full">
          <MoneyText money={money} />
        </span>
      )}
    </div>
  )
}

/** 월간 보기 날짜 칸 머리글: 오늘은 파란 원, 다른 달 날짜는 흐리게, 아래에 특일·가계부 */
export function MonthDateHeader({ date, label, isOffRange, onDrillDown }: DateHeaderProps) {
  const isToday = dayjs(date).isSame(dayjs(), 'day')
  const holidayNames = useHolidayNames(date)
  const colorClass = isToday
    ? 'bg-blue-600 text-white'
    : `${weekdayTextClass(date, holidayNames.length > 0)} hover:bg-gray-100`
  return (
    <div className={isOffRange && !isToday ? 'opacity-40' : undefined}>
      <button
        type="button"
        onClick={onDrillDown}
        // 공휴일 이름을 숨겨도 색만으로 구분되지 않게 버튼 이름에 공휴일을 넣는다 (WCAG 1.4.1)
        aria-label={[dayjs(date).format('M월 D일 dddd'), ...holidayNames].join(' ')}
        aria-current={isToday ? 'date' : undefined}
        className={`mt-1 inline-flex h-6 min-w-6 items-center justify-center rounded-full px-1 text-xs ${colorClass}`}
      >
        {label}
      </button>
      <MonthDayOverlay date={date} />
    </div>
  )
}

/** 주간·일간 보기 날짜 머리글: 요일 + 큰 날짜 숫자 (오늘은 파란 원), 아래에 특일·가계부 */
export function DayColumnHeader({ date }: HeaderProps) {
  const d = dayjs(date)
  const isToday = d.isSame(dayjs(), 'day')
  const textClass = weekdayTextClass(date, useHolidayNames(date).length > 0)
  return (
    <div className="flex flex-col items-center py-1" aria-current={isToday ? 'date' : undefined}>
      <span className={`text-xs ${isToday ? 'text-blue-600' : textClass}`}>{d.format('ddd')}</span>
      <span
        className={`mt-0.5 flex h-9 w-9 items-center justify-center rounded-full text-xl ${
          isToday ? 'bg-blue-600 text-white' : textClass
        }`}
      >
        {d.date()}
      </span>
      <ColumnDayOverlay date={date} />
    </div>
  )
}
