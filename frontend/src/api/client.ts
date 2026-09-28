import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const client = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

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

client.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    // 로그인/가입/refresh 자체의 401(잘못된 비밀번호 등)은 재발급 대상이 아니다
    const isAuthRequest = original?.url?.includes('/api/v1/auth/') ?? false
    if (error.response?.status !== 401 || !original || original._retry || isAuthRequest) {
      return Promise.reject(error)
    }

    original._retry = true
    try {
      refreshing ??= refreshAccessToken().finally(() => {
        refreshing = null
      })
      const accessToken = await refreshing
      original.headers.Authorization = `Bearer ${accessToken}`
      return client(original)
    } catch {
      // refresh 토큰도 만료/무효 → 로그아웃 처리 (저장된 토큰 삭제, 로그인 화면으로 유도)
      useAuthStore.getState().logout()
      return Promise.reject(error)
    }
  },
)

export default client
