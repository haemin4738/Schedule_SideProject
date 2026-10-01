import { useEffect, useRef } from 'react'

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]'

/** Tab 순서에 들어가는 요소들 (tabindex=-1 인 roving 라디오, hidden·inert 안의 요소는 제외) */
const tabbables = (root: HTMLElement): HTMLElement[] =>
  Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (el) => el.tabIndex >= 0 && !el.closest('[hidden], [inert]'),
  )

/**
 * 모달 기본 접근성: 열릴 때 대화상자로 포커스 이동, Esc로 닫기, Tab/Shift+Tab 을 대화상자 안에 가두기,
 * 닫힌 뒤 원래 위치로 포커스 복귀.
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
      if (e.key === 'Escape') {
        onCloseRef.current()
        return
      }
      // 대화상자가 직접 Tab 을 처리했으면(예: 버튼 하나뿐인 안내 팝업) 건드리지 않는다
      const dialog = ref.current
      if (e.key !== 'Tab' || e.defaultPrevented || !dialog) return
      const items = tabbables(dialog)
      if (items.length === 0) {
        e.preventDefault()
        dialog.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      const outside = !dialog.contains(active)
      if (e.shiftKey && (outside || active === first || active === dialog)) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && (outside || active === last)) {
        e.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocused?.focus?.()
    }
  }, [])

  return ref
}
