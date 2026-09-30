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
