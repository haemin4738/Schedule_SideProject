import { useEffect, useRef } from 'react'
import { useDialog } from '@/components/expenses/useDialog'

interface Props {
  message: string
  onClose: () => void
}

/**
 * 보안 사유 로그아웃 등 반드시 확인해야 하는 안내 팝업.
 * 포커스는 '확인' 버튼에 두고 Tab 으로 팝업 밖으로 나가지 못하게 한다 (배경은 호출하는 쪽에서 inert 처리).
 */
export default function SessionNoticeDialog({ message, onClose }: Props) {
  const ref = useDialog<HTMLDivElement>(onClose)
  const confirmRef = useRef<HTMLButtonElement>(null)

  // useDialog 가 대화상자에 준 포커스를 확인 버튼으로 옮긴다 (선언 순서상 이 effect 가 나중에 실행된다)
  useEffect(() => {
    confirmRef.current?.focus()
  }, [])

  // 포커스 가능한 요소가 확인 버튼 하나뿐이므로 Tab/Shift+Tab 은 항상 버튼에 머문다
  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Tab') {
      e.preventDefault()
      confirmRef.current?.focus()
    }
  }
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        ref={ref}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="session-notice-title"
        aria-describedby="session-notice-message"
        tabIndex={-1}
        onKeyDown={onKeyDown}
        className="w-full max-w-sm rounded-xl bg-white p-6 shadow-lg outline-none"
      >
        <h2 id="session-notice-title" className="mb-2 text-lg font-semibold">
          로그아웃 안내
        </h2>
        <p id="session-notice-message" className="mb-6 text-sm text-gray-700">
          {message}
        </p>
        <button
          ref={confirmRef}
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
