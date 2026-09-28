import { useEffect, useRef } from 'react'

/**
 * 모달 기본 접근성: 열릴 때 대화상자로 포커스 이동, Esc로 닫기, 닫힌 뒤 원래 위치로 포커스 복귀.
 * 반환된 ref 를 role="dialog" 요소에 연결하고 tabIndex={-1} 을 준다.
 */
export function useDialog<T extends HTMLElement>(onClose: () => void) {
  const ref = useRef<T>(null)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null
    ref.current?.focus()
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocused?.focus?.()
    }
  }, [])

  return ref
}
