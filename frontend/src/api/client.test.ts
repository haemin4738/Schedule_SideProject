import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import client, { isTokenExpired } from './client'
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
