import { useDialog } from '@/components/expenses/useDialog'

interface Props {
  message: string
  onClose: () => void
}

/** 보안 사유 로그아웃 등 반드시 확인해야 하는 안내 팝업 */
export default function SessionNoticeDialog({ message, onClose }: Props) {
  const ref = useDialog<HTMLDivElement>(onClose)
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        ref={ref}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="session-notice-title"
        aria-describedby="session-notice-message"
        tabIndex={-1}
        className="w-full max-w-sm rounded-xl bg-white p-6 shadow-lg outline-none"
      >
        <h2 id="session-notice-title" className="mb-2 text-lg font-semibold">
          로그아웃 안내
        </h2>
        <p id="session-notice-message" className="mb-6 text-sm text-gray-700">
          {message}
        </p>
        <button
          type="button"
          onClick={onClose}
          className="w-full rounded bg-blue-500 py-2 text-white hover:bg-blue-600"
        >
          확인
        </button>
      </div>
    </div>
  )
}
