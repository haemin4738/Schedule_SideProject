import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import client from './client'
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
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh expired'))

    await expect(client.get('/api/v1/events')).rejects.toMatchObject({ response: { status: 401 } })
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(useAuthStore.getState().accessToken).toBeNull()
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
