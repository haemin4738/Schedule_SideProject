import { EXPENSE_TYPE_LABELS, formatSignedAmount } from '@/constants/expenseType'
import { paymentMethodLabel } from '@/constants/paymentMethod'
import dayjs from 'dayjs'
import type { ExpenseListProps } from './ExpenseListTable'
import ExpensePagination from './ExpensePagination'

/**
 * 작은 화면용 가계부 목록 — 표와 같은 정보(날짜·카테고리·결제수단·설명·금액).
 * 카드를 누르면(Enter/Space 포함) 수정 모달, 삭제는 카드 아래 별도 버튼(버튼 중첩 없음)
 */
export default function ExpenseCardList({
  items,
  meta,
  page,
  isLoading,
  error,
  onEdit,
  onDelete,
  onPageChange,
}: ExpenseListProps) {
  return (
    <>
      {items.length > 0 && (
        <ul aria-label="가계부 내역 목록" className="space-y-3">
          {items.map((item) => {
            const date = dayjs(item.transactionDate).format('M/D')
            const income = item.type === 'INCOME'
            const amount = formatSignedAmount(item.type, item.amount)
            const payment = item.paymentMethod ? paymentMethodLabel(item.paymentMethod) : null
            // 줄마다 span 이라 내용만으로는 이름이 붙어 읽히므로, 유형(지출/수입)까지 글자로 담은 이름을 준다
            const label = [date, item.categoryName, payment, item.description, EXPENSE_TYPE_LABELS[item.type], amount]
              .filter(Boolean)
              .join(' ')
            return (
              <li key={item.id} className="overflow-hidden rounded-xl bg-white shadow">
                <button
                  type="button"
                  onClick={() => onEdit(item)}
                  aria-label={`${label} 수정`}
                  className="block min-h-11 w-full px-4 pt-3 pb-2 text-left hover:bg-gray-50 focus-visible:bg-gray-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-blue-500"
                >
                  <span className="flex items-start justify-between gap-3">
                    <span className="min-w-0 break-words font-semibold text-gray-900">{item.categoryName}</span>
                    <span
                      data-testid="card-amount"
                      className={`shrink-0 whitespace-nowrap text-right font-semibold ${
                        income ? 'text-blue-600' : 'text-red-500'
                      }`}
                    >
                      {amount}
                    </span>
                  </span>
                  {item.description && (
                    <span className="mt-0.5 block break-words text-sm text-gray-700">{item.description}</span>
                  )}
                  <span className="mt-1 block text-xs text-gray-500">
                    {date}
                    {payment && ` · ${payment}`}
                  </span>
                </button>
                <div className="flex items-center justify-end border-t border-gray-100 px-2">
                  <button
                    type="button"
                    onClick={() => onDelete(item)}
                    aria-label={`${date} ${item.categoryName}${item.description ? ` ${item.description}` : ''} 삭제`}
                    className="inline-flex min-h-11 min-w-11 items-center justify-center px-3 text-sm text-red-500 hover:underline"
                  >
                    삭제
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {isLoading && (
        <p className="rounded-xl bg-white px-4 py-6 text-center text-sm text-gray-400 shadow">불러오는 중...</p>
      )}
      {!isLoading && items.length === 0 && !error && (
        <p className="rounded-xl bg-white px-4 py-6 text-center text-sm text-gray-400 shadow">내역이 없습니다</p>
      )}
      <ExpensePagination meta={meta} page={page} onPageChange={onPageChange} touch />
    </>
  )
}
