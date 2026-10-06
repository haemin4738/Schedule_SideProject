import { formatAmount } from '@/constants/expenseType'
import dayjs from 'dayjs'
import 'dayjs/locale/ko'
import { useId } from 'react'
import { Link } from 'react-router-dom'
import type { CalendarMoneySummary, MoneyStatus, MoneyTotals } from './useCalendarMoneySummary'

/** 수입·지출·합계 세 줄. 색(수입 파랑/지출 빨강)과 함께 글자 라벨을 두어 색만으로 구분하지 않는다 */
function TotalsList({ title, totals, status }: { title: string; totals: MoneyTotals | null; status: MoneyStatus }) {
  return (
    <div>
      <h3 className="mb-1 text-xs font-medium text-gray-600">{title}</h3>
      {status === 'error' ? (
        <p className="text-xs text-red-600">불러오지 못했습니다.</p>
      ) : status === 'loading' || !totals ? (
        <p className="text-xs text-gray-400">불러오는 중…</p>
      ) : (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-sm">
          <dt className="text-gray-500">수입</dt>
          <dd className="text-right tabular-nums text-blue-600">{formatAmount(totals.income)}</dd>
          <dt className="text-gray-500">지출</dt>
          <dd className="text-right tabular-nums text-red-500">{formatAmount(totals.expense)}</dd>
          <dt className="text-gray-500">합계</dt>
          <dd className={`text-right font-medium tabular-nums ${totals.net < 0 ? 'text-red-500' : 'text-gray-800'}`}>
            {formatAmount(totals.net)}
          </dd>
        </dl>
      )}
    </div>
  )
}

interface Props {
  summary: CalendarMoneySummary
  onNavigate?: () => void
}

/** 캘린더 사이드바의 가계부 요약 — 보고 있는 달과 선택한 날의 수입·지출 */
export default function MoneySummaryPanel({ summary, onNavigate }: Props) {
  const headingId = useId()
  const month = dayjs(`${summary.month}-01`)
  return (
    <section aria-labelledby={headingId} className="pl-4">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <h2 id={headingId} className="text-xs font-semibold text-gray-500">
          가계부 요약
        </h2>
        <Link
          to={`/expenses?month=${summary.month}`}
          onClick={onNavigate}
          className="rounded text-xs text-blue-600 hover:underline"
        >
          가계부 보기
        </Link>
      </div>
      <div className="flex flex-col gap-3 rounded-lg bg-gray-50 px-3 py-2">
        <TotalsList title={`${month.format('M월')} 전체`} totals={summary.monthTotals} status={summary.monthStatus} />
        <TotalsList
          title={dayjs(summary.day).locale('ko').format('M월 D일 (ddd)')}
          totals={summary.dayTotals}
          status={summary.dayStatus}
        />
      </div>
      {/* 첫 클릭은 선택만 하므로 두 번째 클릭에 입력 창이 열린다는 것을 짧게 알려 준다 */}
      <p className="mt-1.5 text-[11px] text-gray-400">날짜를 한 번 더 누르면 바로 입력할 수 있어요</p>
    </section>
  )
}
