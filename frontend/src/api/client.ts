import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'

// 기본은 같은 출처 상대경로(/api/...) — 개발 시 Vite 프록시가 백엔드로 전달한다.
// API를 다른 출처에 둘 때만 VITE_API_BASE_URL 을 지정한다 (이 경우 백엔드 CORS 설정 필요)
const baseURL = import.meta.env.VITE_API_BASE_URL ?? ''

const client = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 이 경로들의 401(잘못된 비밀번호, 만료된 refresh 토큰 등)은 재발급 대상이 아니다
const AUTH_PATHS = new Set(['/api/v1/auth/login', '/api/v1/auth/signup', '/api/v1/auth/refresh', '/api/v1/auth/logout'])

// 동시에 여러 요청이 401을 받아도 refresh는 한 번만 호출한다
let refreshing: Promise<string> | null = null

const refreshAccessToken = async (): Promise<string> => {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) throw new Error('refresh token 없음')
  const { data } = await axios.post(`${baseURL}/api/v1/auth/refresh`, { refreshToken })
  // 백엔드가 refresh 토큰도 새로 발급하므로 둘 다 저장한다
  useAuthStore.getState().login(data.data.accessToken, data.data.refreshToken)
  return data.data.accessToken
}

/** JWT의 exp(초)를 읽어 만료 여부를 판단한다. 형식이 잘못된 토큰은 만료로 본다. */
export const isTokenExpired = (token: string, nowMs: number = Date.now()): boolean => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp !== 'number' || payload.exp * 1000 <= nowMs
  } catch {
    return true
  }
}

/**
 * access 토큰을 재발급한다. 진행 중인 refresh가 있으면 그 결과를 공유한다.
 * refresh 토큰이 없거나 만료/무효(401/403)면 로그아웃한다. 네트워크 오류·5xx 같은 일시 장애로는 로그아웃하지 않는다.
 */
export const refreshSession = async (): Promise<string> => {
  refreshing ??= refreshAccessToken().finally(() => {
    refreshing = null
  })
  try {
    return await refreshing
  } catch (refreshError) {
    const status = axios.isAxiosError(refreshError) ? refreshError.response?.status : undefined
    if (!axios.isAxiosError(refreshError) || status === 401 || status === 403) {
      useAuthStore.getState().logout()
    }
    throw refreshError
  }
}

client.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const isAuthRequest = AUTH_PATHS.has((original?.url ?? '').split('?')[0])
    if (error.response?.status !== 401 || !original || original._retry || isAuthRequest) {
      return Promise.reject(error)
    }

    original._retry = true
    try {
      const accessToken = await refreshSession()
      original.headers.Authorization = `Bearer ${accessToken}`
      return client(original)
    } catch {
      return Promise.reject(error)
    }
  },
)

export default client
