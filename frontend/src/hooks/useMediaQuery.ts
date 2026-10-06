import { useCallback, useSyncExternalStore } from 'react'

/** Tailwind `md` 와 같은 기준 — 캘린더 사이드바(폰은 서랍)와 맞춘다 */
export const DESKTOP_MEDIA_QUERY = '(min-width: 768px)'

const hasMatchMedia = () => typeof window !== 'undefined' && typeof window.matchMedia === 'function'

/**
 * 미디어 쿼리 일치 여부를 구독한다.
 * matchMedia 가 없는 환경(jsdom 등)에서는 fallback 을 돌려준다.
 */
export function useMediaQuery(query: string, fallback = false): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      if (!hasMatchMedia()) return () => {}
      const mql = window.matchMedia(query)
      mql.addEventListener('change', onChange)
      return () => mql.removeEventListener('change', onChange)
    },
    [query],
  )
  const getSnapshot = () => (hasMatchMedia() ? window.matchMedia(query).matches : fallback)
  return useSyncExternalStore(subscribe, getSnapshot, () => fallback)
}
