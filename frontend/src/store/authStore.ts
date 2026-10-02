import { create } from 'zustand'

interface AuthState {
  accessToken: string | null
  /** 보안 사유 로그아웃 등 로그인 화면에서 팝업으로 알려야 할 안내 (메모리 전용 — 새로고침하면 사라진다) */
  sessionNotice: string | null
  login: (accessToken: string, refreshToken: string) => void
  logout: (notice?: string) => void
  clearSessionNotice: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem('accessToken'),
  sessionNotice: null,
  login: (accessToken, refreshToken) => {
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    set({ accessToken })
  },
  logout: (notice) => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    set(notice ? { accessToken: null, sessionNotice: notice } : { accessToken: null })
  },
  clearSessionNotice: () => set({ sessionNotice: null }),
}))

/**
 * 다른 탭의 로그인·로그아웃·토큰 갱신을 이 탭 상태에 반영한다 (storage 이벤트는 같은 출처의 다른 탭 변경에만 발생).
 * 저장소는 이미 바뀌어 있으므로 다시 쓰지 않고 상태만 맞춘다 — 라우터 가드가 로그인/캘린더 화면으로 옮긴다.
 * key 가 null 이면 localStorage.clear() 다.
 */
export const syncAuthFromStorage = (event: StorageEvent) => {
  if (event.storageArea !== localStorage) return
  if (event.key !== null && event.key !== 'accessToken') return
  const accessToken = localStorage.getItem('accessToken')
  if (useAuthStore.getState().accessToken !== accessToken) useAuthStore.setState({ accessToken })
}

window.addEventListener('storage', syncAuthFromStorage)
