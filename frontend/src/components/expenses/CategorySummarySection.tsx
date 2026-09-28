import type { CategorySummaryResponse } from '@/api/expenses'
import { formatAmount } from '@/constants/expenseType'

interface Props {
  byCategory: CategorySummaryResponse | null
}

export default function CategorySummarySection({ byCategory }: Props) {
  return (
    <section className="mt-6 rounded-xl bg-white p-4 shadow">
      <h2 className="mb-3 text-lg font-medium">카테고리별 지출</h2>
      {byCategory && byCategory.categories.length > 0 ? (
        <ul className="space-y-2">
          {byCategory.categories.map((c) => {
            const ratio = byCategory.total > 0 ? (c.amount / byCategory.total) * 100 : 0
            return (
              <li key={c.categoryId} className="text-sm">
                <div className="mb-1 flex justify-between">
                  <span>
                    {c.categoryName} <span className="text-gray-400">({c.count}건)</span>
                  </span>
                  <span>
                    {formatAmount(c.amount)}{' '}
                    {/* Flutter 화면과 동일하게 정수 % 로 표기 */}
                    <span className="text-gray-400">{Math.round(ratio)}%</span>
                  </span>
                </div>
                <div className="h-2 rounded bg-gray-100">
                  <div className="h-2 rounded bg-red-400" style={{ width: `${ratio}%` }} />
                </div>
              </li>
            )
          })}
        </ul>
      ) : (
        <p className="text-sm text-gray-400">지출 내역이 없습니다</p>
      )}
    </section>
  )
}
