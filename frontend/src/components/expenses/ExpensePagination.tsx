import type { PageMeta } from '@/api/expenses'

interface Props {
  meta: PageMeta | null
  page: number
  onPageChange: (page: number) => void
  /** 폰(카드 목록)에서는 버튼을 44px 이상으로 키운다 */
  touch?: boolean
}

/** 가계부 목록(표·카드) 공통 페이지 이동 — 2페이지 이상일 때만 보인다 */
export default function ExpensePagination({ meta, page, onPageChange, touch = false }: Props) {
  if (!meta || meta.totalPages <= 1) return null
  const buttonClass = `rounded border text-sm disabled:opacity-40 ${
    touch ? 'inline-flex min-h-11 min-w-11 items-center justify-center px-4' : 'px-3 py-1'
  }`
  return (
    <div className="mt-4 flex items-center justify-center gap-2">
      <button
        type="button"
        onClick={() => onPageChange(Math.max(0, page - 1))}
        disabled={page === 0}
        className={buttonClass}
      >
        이전
      </button>
      <span className="text-sm text-gray-600">
        {page + 1} / {meta.totalPages}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(Math.min(meta.totalPages - 1, page + 1))}
        disabled={page >= meta.totalPages - 1}
        className={buttonClass}
      >
        다음
      </button>
    </div>
  )
}
