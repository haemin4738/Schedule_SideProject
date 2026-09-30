import { logout } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'

/**
 * 로컬 세션을 먼저 비우고(화면은 즉시 로그아웃) 서버 세션 무효화를 시도한다.
 * 서버 호출 실패(네트워크/5xx)는 무시한다 — 서버 세션은 비활성 만료로 정리된다.
 */
export async function logoutSession(): Promise<void> {
  const refreshToken = localStorage.getItem('refreshToken')
  useAuthStore.getState().logout()
  if (!refreshToken) return
  try {
    await logout(refreshToken)
  } catch {
    // 로컬 로그아웃은 이미 끝났다
  }
}
