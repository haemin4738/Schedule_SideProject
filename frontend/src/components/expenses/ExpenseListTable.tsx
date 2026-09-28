import type { ExpenseSummary, PageMeta } from '@/api/expenses'
import { formatSignedAmount } from '@/constants/expenseType'
import dayjs from 'dayjs'

interface Props {
  items: ExpenseSummary[]
  meta: PageMeta | null
  page: number
  isLoading: boolean
  error: string | null
  onEdit: (item: ExpenseSummary) => void
  onDelete: (item: ExpenseSummary) => void
  onPageChange: (page: number) => void
}

export default function ExpenseListTable({
  items,
  meta,
  page,
  isLoading,
  error,
  onEdit,
  onDelete,
  onPageChange,
}: Props) {
  return (
    <>
      <div className="overflow-hidden rounded-xl bg-white shadow">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-left text-gray-600">
            <tr>
              <th className="px-4 py-2">날짜</th>
              <th className="px-4 py-2">카테고리</th>
              <th className="px-4 py-2">설명</th>
              <th className="px-4 py-2 text-right">금액</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr
                key={item.id}
                onClick={() => onEdit(item)}
                onKeyDown={(e) => {
                  if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) {
                    e.preventDefault()
                    onEdit(item)
                  }
                }}
                tabIndex={0}
                aria-label={`${item.categoryName} ${item.description ?? ''} 수정`}
                className="cursor-pointer border-t hover:bg-gray-50 focus:bg-blue-50 focus:outline-none"
              >
                <td className="px-4 py-2">{dayjs(item.transactionDate).format('M/D')}</td>
                <td className="px-4 py-2">{item.categoryName}</td>
                <td className="px-4 py-2">{item.description || '-'}</td>
                <td
                  className={`px-4 py-2 text-right ${
                    item.type === 'INCOME' ? 'text-blue-600' : 'text-red-500'
                  }`}
                >
                  {formatSignedAmount(item.type, item.amount)}
                </td>
                <td className="px-4 py-2 text-right">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onDelete(item)
                    }}
                    className="text-red-500 hover:underline"
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
            {isLoading && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                  불러오는 중...
                </td>
              </tr>
            )}
            {!isLoading && items.length === 0 && !error && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                  내역이 없습니다
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {meta && meta.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <button
            onClick={() => onPageChange(Math.max(0, page - 1))}
            disabled={page === 0}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-sm text-gray-600">
            {page + 1} / {meta.totalPages}
          </span>
          <button
            onClick={() => onPageChange(Math.min(meta.totalPages - 1, page + 1))}
            disabled={page >= meta.totalPages - 1}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </>
  )
}
