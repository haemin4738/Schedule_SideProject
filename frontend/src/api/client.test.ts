import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import client, { SESSION_REVOKED_NOTICE, isAuthPath, isTokenExpired } from './client'
import { useAuthStore } from '@/store/authStore'

const unauthorized = (config: InternalAxiosRequestConfig) =>
  new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
    status: 401,
    statusText: 'Unauthorized',
    data: { success: false, data: null, error: '인증이 필요합니다.' },
    headers: {},
    config,
  })

const ok = (config: InternalAxiosRequestConfig, data: unknown) => ({
  status: 200,
  statusText: 'OK',
  data,
  headers: {},
  config,
})

describe('api client 401 처리', () => {
  let adapter: ReturnType<typeof vi.fn<AxiosAdapter>>

  beforeEach(() => {
    localStorage.setItem('accessToken', 'expired-access')
    localStorage.setItem('refreshToken', 'valid-refresh')
    useAuthStore.setState({ accessToken: 'expired-access' })
    adapter = vi.fn<AxiosAdapter>()
    client.defaults.adapter = adapter
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    useAuthStore.setState({ sessionNotice: null })
  })

  it('401이면 refresh 후 새 토큰으로 원 요청을 재시도하고 두 토큰을 모두 저장한다', async () => {
    adapter.mockImplementation(async (config) => {
      if (config.headers.Authorization === 'Bearer new-access') return ok(config, { data: 'events' })
      throw unauthorized(config)
    })
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    })

    const res = await client.get('/api/v1/events')

    expect(res.data).toEqual({ data: 'events' })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(refresh).toHaveBeenCalledWith(expect.stringContaining('/api/v1/auth/refresh'), {
      refreshToken: 'valid-refresh',
    })
    expect(localStorage.getItem('accessToken')).toBe('new-access')
    expect(localStorage.getItem('refreshToken')).toBe('new-refresh')
  })

  it('동시에 여러 요청이 401을 받아도 refresh는 한 번만 호출한다', async () => {
    adapter.mockImplementation(async (config) => {
      if (config.headers.Authorization === 'Bearer new-access') return ok(config, {})
      throw unauthorized(config)
    })
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    })

    await Promise.all([client.get('/api/v1/events'), client.get('/api/v1/expenses')])

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('로그인 요청의 401(잘못된 비밀번호)은 refresh를 시도하지 않는다', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    const refresh = vi.spyOn(axios, 'post')

    await expect(client.post('/api/v1/auth/login', {})).rejects.toMatchObject({
      response: { status: 401 },
    })
    expect(refresh).not.toHaveBeenCalled()
  })

  it('소셜 로그인·계정 연결 요청의 401도 refresh를 시도하지 않는다', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    const refresh = vi.spyOn(axios, 'post')

    await expect(client.post('/api/v1/auth/social/kakao/login', {})).rejects.toMatchObject({
      response: { status: 401 },
    })
    await expect(client.post('/api/v1/auth/social/link', {})).rejects.toMatchObject({
      response: { status: 401 },
    })
    expect(refresh).not.toHaveBeenCalled()
  })

  it('refresh도 실패하면 로그아웃 처리하고 원래 에러를 반환한다', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    vi.spyOn(axios, 'post').mockRejectedValue(
      new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', undefined, null, {
        status: 401,
        statusText: 'Unauthorized',
        data: {},
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      }),
    )

    await expect(client.get('/api/v1/events')).rejects.toMatchObject({ response: { status: 401 } })
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('refresh가 네트워크 오류로 실패하면 로그아웃하지 않는다', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    vi.spyOn(axios, 'post').mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'))

    await expect(client.get('/api/v1/events')).rejects.toMatchObject({ response: { status: 401 } })
    expect(localStorage.getItem('refreshToken')).toBe('valid-refresh')
    expect(useAuthStore.getState().accessToken).toBe('expired-access')
  })

  const refreshRejected = (status: number, data: unknown) =>
    new AxiosError('Refresh failed', 'ERR_BAD_REQUEST', undefined, null, {
      status,
      statusText: String(status),
      data,
      headers: {},
      config: {} as InternalAxiosRequestConfig,
    })

  it('refreshSession_refresh401WithSessionRevokedCode_logsOutWithNotice', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    vi.spyOn(axios, 'post').mockRejectedValue(
      refreshRejected(401, { success: false, data: null, error: '보안 안내 문구', code: 'SESSION_REVOKED' }),
    )

    await expect(client.get('/api/v1/events')).rejects.toMatchObject({ response: { status: 401 } })
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(useAuthStore.getState().sessionNotice).toBe('보안 안내 문구')
  })

  it('refreshSession_sessionRevokedWithoutMessage_usesFallbackNotice', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    vi.spyOn(axios, 'post').mockRejectedValue(refreshRejected(401, { success: false, data: null, code: 'SESSION_REVOKED' }))

    await expect(client.get('/api/v1/events')).rejects.toBeDefined()
    expect(useAuthStore.getState().sessionNotice).toBe(SESSION_REVOKED_NOTICE)
  })

  it('refreshSession_refresh401WithInvalidTokenCode_logsOutWithoutNotice', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    vi.spyOn(axios, 'post').mockRejectedValue(
      refreshRejected(401, { success: false, data: null, error: '유효하지 않은 refresh 토큰입니다.', code: 'INVALID_REFRESH_TOKEN' }),
    )

    await expect(client.get('/api/v1/events')).rejects.toBeDefined()
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().sessionNotice).toBeNull()
  })

  it('refreshSession_refresh409RefreshInProgress_doesNotLogout', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    vi.spyOn(axios, 'post').mockRejectedValue(
      refreshRejected(409, { success: false, data: null, error: '갱신 중', code: 'REFRESH_IN_PROGRESS' }),
    )

    await expect(client.get('/api/v1/events')).rejects.toMatchObject({ response: { status: 401 } })
    expect(useAuthStore.getState().accessToken).toBe('expired-access')
    expect(localStorage.getItem('refreshToken')).toBe('valid-refresh')
  })

  it('refreshSession_refresh503_doesNotLogout', async () => {
    adapter.mockImplementation(async (config) => {
      throw unauthorized(config)
    })
    vi.spyOn(axios, 'post').mockRejectedValue(refreshRejected(503, { success: false, data: null, error: '일시 장애' }))

    await expect(client.get('/api/v1/events')).rejects.toBeDefined()
    expect(useAuthStore.getState().accessToken).toBe('expired-access')
  })

  it('쿼리스트링이 붙은 일반 요청도 인증 경로로 오인하지 않고 refresh한다', async () => {
    adapter.mockImplementation(async (config) => {
      if (config.headers.Authorization === 'Bearer new-access') return ok(config, {})
      throw unauthorized(config)
    })
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { data: { accessToken: 'new-access', refreshToken: 'new-refresh' } },
    })

    await client.get('/api/v1/events?redirect=/api/v1/auth/login')

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('403(권한 없음)은 refresh 없이 그대로 에러를 반환한다', async () => {
    adapter.mockImplementation(async (config) => {
      throw new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, null, {
        status: 403,
        statusText: 'Forbidden',
        data: {},
        headers: {},
        config,
      })
    })
    const refresh = vi.spyOn(axios, 'post')

    await expect(client.get('/api/v1/events/1')).rejects.toMatchObject({ response: { status: 403 } })
    expect(refresh).not.toHaveBeenCalled()
  })
})

describe('isAuthPath', () => {
  it('/api/v1/auth/ 로 시작하는 경로는 인증 경로로 본다', () => {
    expect(isAuthPath('/api/v1/auth/login')).toBe(true)
    expect(isAuthPath('/api/v1/auth/refresh')).toBe(true)
    expect(isAuthPath('/api/v1/auth/social/google/login')).toBe(true)
    expect(isAuthPath('/api/v1/auth/social/link?x=1')).toBe(true)
  })

  it('쿼리스트링에만 인증 경로가 있거나 prefix가 다르면 인증 경로가 아니다', () => {
    expect(isAuthPath('/api/v1/events?redirect=/api/v1/auth/login')).toBe(false)
    expect(isAuthPath('/api/v1/authors')).toBe(false)
    expect(isAuthPath(undefined)).toBe(false)
  })

  it('isAuthPath_similarPrefixWithoutSlash_returnsFalse', () => {
    expect(isAuthPath('/api/v1/authx')).toBe(false)
    expect(isAuthPath('/api/v1/authx/login')).toBe(false)
    expect(isAuthPath('/api/v1/auth')).toBe(false)
    expect(isAuthPath('')).toBe(false)
  })

  it('isAuthPath_queryStringOnAuthPath_ignoresQuery', () => {
    expect(isAuthPath('/api/v1/auth/social/kakao/login?foo=bar&baz=/api/v1/events')).toBe(true)
    expect(isAuthPath('/api/v1/authx?next=/api/v1/auth/login')).toBe(false)
  })
})

describe('isTokenExpired', () => {
  const token = (payload: object) =>
    `header.${btoa(JSON.stringify(payload)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')}.sig`
  const now = Date.UTC(2026, 8, 28, 12, 0, 0)

  it('exp가 현재 이후면 만료되지 않은 것으로 본다', () => {
    expect(isTokenExpired(token({ exp: now / 1000 + 60 }), now)).toBe(false)
  })

  it('exp가 현재 이전이면 만료로 본다', () => {
    expect(isTokenExpired(token({ exp: now / 1000 - 1 }), now)).toBe(true)
  })

  it('형식이 잘못되었거나 exp가 없으면 만료로 본다', () => {
    expect(isTokenExpired('not-a-jwt', now)).toBe(true)
    expect(isTokenExpired(token({ sub: '1' }), now)).toBe(true)
  })
})
