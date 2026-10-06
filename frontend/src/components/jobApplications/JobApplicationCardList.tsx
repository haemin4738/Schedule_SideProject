import type { JobApplicationSummary } from '@/api/jobApplications'
import JobApplicationStatusBadge from './JobApplicationStatusBadge'

interface Props {
  items: JobApplicationSummary[]
  isLoading: boolean
  /** 목록 오류가 있으면 빈 목록 문구를 숨긴다 (오류 문구는 페이지가 보여준다) */
  hasError: boolean
  editingId: number | null
  onEdit: (id: number) => void
  onDelete: (id: number) => void
}

/** 작은 화면용 구직활동 목록 — 표와 같은 정보(회사명·직무·상태·지원일). 카드를 누르면 수정, 삭제는 카드 아래 버튼 */
export default function JobApplicationCardList({ items, isLoading, hasError, editingId, onEdit, onDelete }: Props) {
  return (
    <div>
      {items.length > 0 && (
        <ul aria-label="지원 내역 목록" className="space-y-3">
          {items.map((item) => {
            const editing = item.id === editingId
            return (
              <li
                key={item.id}
                className={`overflow-hidden rounded-xl bg-white shadow ${editing ? 'ring-2 ring-blue-400' : ''}`}
              >
                <button
                  type="button"
                  onClick={() => onEdit(item.id)}
                  aria-current={editing ? 'true' : undefined}
                  className="block min-h-11 w-full px-4 pt-3 pb-2 text-left hover:bg-gray-50 focus-visible:bg-gray-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-blue-500"
                >
                  <span className="flex items-start justify-between gap-2">
                    <span className="min-w-0 break-words font-semibold text-gray-900">{item.companyName}</span>
                    <JobApplicationStatusBadge status={item.status} />
                  </span>
                  <span className="mt-0.5 block break-words text-sm text-gray-700">{item.position}</span>
                  <span className="mt-1 block text-xs text-gray-500">지원일 {item.appliedAt}</span>
                  <span className="sr-only">, 눌러서 수정</span>
                </button>
                <div className="flex items-center justify-end gap-1 border-t border-gray-100 px-2">
                  <button
                    type="button"
                    onClick={() => onDelete(item.id)}
                    aria-label={`${item.companyName} 삭제`}
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
      {isLoading && <p className="rounded-xl bg-white px-4 py-6 text-center text-sm text-gray-400 shadow">불러오는 중...</p>}
      {!isLoading && items.length === 0 && !hasError && (
        <p className="rounded-xl bg-white px-4 py-6 text-center text-sm text-gray-400 shadow">지원 내역이 없습니다.</p>
      )}
    </div>
  )
}
