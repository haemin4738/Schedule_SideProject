import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DESKTOP_MEDIA_QUERY, useMediaQuery } from './useMediaQuery'

/** change 이벤트를 직접 일으킬 수 있는 matchMedia 대역 */
function stubMatchMedia(initial: boolean) {
  let matches = initial
  const listeners = new Set<() => void>()
  const addEventListener = vi.fn((_type: string, cb: () => void) => listeners.add(cb))
  const removeEventListener = vi.fn((_type: string, cb: () => void) => listeners.delete(cb))
  const matchMedia = vi.fn((query: string) => ({
    get matches() {
      return matches
    },
    media: query,
    addEventListener,
    removeEventListener,
  }))
  vi.stubGlobal('matchMedia', matchMedia)
  return {
    matchMedia,
    addEventListener,
    removeEventListener,
    listenerCount: () => listeners.size,
    change(next: boolean) {
      matches = next
      listeners.forEach((cb) => cb())
    },
  }
}

describe('useMediaQuery', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('useMediaQuery_whenQueryMatches_returnsTrue', () => {
    const media = stubMatchMedia(true)

    const { result } = renderHook(() => useMediaQuery(DESKTOP_MEDIA_QUERY))

    expect(result.current).toBe(true)
    expect(media.matchMedia).toHaveBeenCalledWith('(min-width: 768px)')
  })

  it('useMediaQuery_whenQueryDoesNotMatch_returnsFalseIgnoringFallback', () => {
    stubMatchMedia(false)

    const { result } = renderHook(() => useMediaQuery(DESKTOP_MEDIA_QUERY, true))

    expect(result.current).toBe(false)
  })

  it('useMediaQuery_onChangeEvent_updatesValue', () => {
    const media = stubMatchMedia(false)
    const { result } = renderHook(() => useMediaQuery(DESKTOP_MEDIA_QUERY))

    act(() => media.change(true))
    expect(result.current).toBe(true)

    act(() => media.change(false))
    expect(result.current).toBe(false)
  })

  it('useMediaQuery_onUnmount_removesListener', () => {
    const media = stubMatchMedia(true)
    const { unmount } = renderHook(() => useMediaQuery(DESKTOP_MEDIA_QUERY))
    expect(media.listenerCount()).toBe(1)

    unmount()

    expect(media.removeEventListener).toHaveBeenCalledWith('change', expect.any(Function))
    expect(media.listenerCount()).toBe(0)
  })

  it('useMediaQuery_whenQueryChanges_resubscribesToNewQuery', () => {
    const media = stubMatchMedia(true)
    const { rerender } = renderHook(({ q }) => useMediaQuery(q), { initialProps: { q: '(min-width: 768px)' } })

    rerender({ q: '(min-width: 1024px)' })

    expect(media.matchMedia).toHaveBeenCalledWith('(min-width: 1024px)')
    expect(media.removeEventListener).toHaveBeenCalledTimes(1)
    expect(media.listenerCount()).toBe(1)
  })

  it.each([true, false])('useMediaQuery_withoutMatchMedia_returnsFallback_%s', (fallback) => {
    vi.stubGlobal('matchMedia', undefined)

    const { result } = renderHook(() => useMediaQuery(DESKTOP_MEDIA_QUERY, fallback))

    expect(result.current).toBe(fallback)
  })

  it('useMediaQuery_withoutMatchMediaAndNoFallback_returnsFalse', () => {
    vi.stubGlobal('matchMedia', undefined)

    const { result } = renderHook(() => useMediaQuery(DESKTOP_MEDIA_QUERY))

    expect(result.current).toBe(false)
  })
})
