import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { getApiErrorCode, getApiErrorMessage } from '@/api/errorMessage'
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

// 인증 API(/api/v1/auth/**)의 401(잘못된 비밀번호, 만료된 refresh·link 토큰, 제공자 자격증명 오류 등)은 재발급 대상이 아니다
// 주의: /api/v1/auth/ 아래에 로그인이 필요한 API(연동 해제 등)를 추가하면 그 401 도 재발급되지 않는다 — 다른 경로에 둘 것
const AUTH_PATH_PREFIX = '/api/v1/auth/'

/** 쿼리스트링을 뗀 경로가 인증 API 인지 판단한다. */
export const isAuthPath = (url: string | undefined): boolean =>
  (url ?? '').split('?')[0].startsWith(AUTH_PATH_PREFIX)

const SESSION_REVOKED_CODE = 'SESSION_REVOKED'
export const SESSION_REVOKED_NOTICE = '보안을 위해 모든 기기에서 로그아웃되었습니다. 다시 로그인해 주세요.'

// 동시에 여러 요청이 401을 받아도 refresh는 한 번만 호출한다
let refreshing: Promise<string> | null = null

const refreshAccessToken = async (): Promise<string> => {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) throw new Error('refresh token 없음')
  const { data } = await axios.post(`${baseURL}/api/v1/auth/refresh`, { refreshToken })
  // 응답을 기다리는 동안 로그아웃(또는 다른 탭의 재로그인)으로 토큰이 바뀌었으면 결과를 버린다 — 로그아웃한 화면이 되살아나지 않게
  if (localStorage.getItem('refreshToken') !== refreshToken) throw new Error('refresh 중 세션이 바뀜')
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
 * 회전 직후 30초 동안은 서버가 직전 토큰으로도 재발급하므로 탭 동시 refresh 는 정상 200 이다.
 * 401 + code SESSION_REVOKED(토큰 재사용 탐지로 모든 기기 로그아웃)면 로그인 화면 팝업 안내와 함께 로그아웃한다.
 */
export const refreshSession = async (): Promise<string> => {
  refreshing ??= refreshAccessToken().finally(() => {
    refreshing = null
  })
  try {
    return await refreshing
  } catch (refreshError) {
    const status = axios.isAxiosError(refreshError) ? refreshError.response?.status : undefined
    if (status === 401 && getApiErrorCode(refreshError) === SESSION_REVOKED_CODE) {
      useAuthStore.getState().logout(getApiErrorMessage(refreshError, SESSION_REVOKED_NOTICE))
    } else if (!axios.isAxiosError(refreshError) || status === 401 || status === 403) {
      useAuthStore.getState().logout()
    }
    throw refreshError
  }
}

client.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const isAuthRequest = isAuthPath(original?.url)
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
