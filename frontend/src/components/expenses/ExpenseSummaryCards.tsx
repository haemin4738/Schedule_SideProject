import type { MonthlySummaryResponse } from '@/api/expenses'
import { formatAmount } from '@/constants/expenseType'

interface Props {
  /** 현재 월 기준으로 불러온 요약. 로딩 중/실패면 null ('-' 표시) */
  monthly: MonthlySummaryResponse | null
}

export default function ExpenseSummaryCards({ monthly }: Props) {
  return (
    <div className="mb-6 grid grid-cols-3 gap-2 sm:gap-3">
      <div className="rounded-xl bg-white p-3 shadow sm:p-4">
        <p className="text-xs text-gray-500">수입</p>
        <p data-testid="summary-income" className="text-sm font-semibold break-all sm:text-lg text-blue-600">
          {monthly ? formatAmount(monthly.totalIncome) : '-'}
        </p>
      </div>
      <div className="rounded-xl bg-white p-3 shadow sm:p-4">
        <p className="text-xs text-gray-500">지출</p>
        <p data-testid="summary-expense" className="text-sm font-semibold break-all sm:text-lg text-red-500">
          {monthly ? formatAmount(monthly.totalExpense) : '-'}
        </p>
      </div>
      <div className="rounded-xl bg-white p-3 shadow sm:p-4">
        <p className="text-xs text-gray-500">합계</p>
        <p
          data-testid="summary-net"
          className={`text-sm font-semibold break-all sm:text-lg ${monthly && monthly.net < 0 ? 'text-red-500' : ''}`}
        >
          {monthly ? formatAmount(monthly.net) : '-'}
        </p>
      </div>
    </div>
  )
}
