import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { logoutSession } from './logout'
import { logout } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({ logout: vi.fn() }))

const mockedLogout = vi.mocked(logout)

describe('logoutSession', () => {
  beforeEach(() => {
    useAuthStore.getState().login('acc', 'ref')
  })

  afterEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
    useAuthStore.setState({ accessToken: null, sessionNotice: null })
  })

  it('logoutSession_withRefreshToken_clearsLocalBeforeCallingServer', async () => {
    let tokenDuringCall: string | null = 'unset'
    mockedLogout.mockImplementation(async () => {
      tokenDuringCall = useAuthStore.getState().accessToken
      return {} as never
    })

    await logoutSession()

    expect(mockedLogout).toHaveBeenCalledWith('ref')
    expect(tokenDuringCall).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })

  it('logoutSession_serverFails_stillLoggedOutAndDoesNotThrow', async () => {
    mockedLogout.mockRejectedValue(new Error('Network Error'))

    await expect(logoutSession()).resolves.toBeUndefined()
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('logoutSession_withoutRefreshToken_skipsServerCall', async () => {
    localStorage.removeItem('refreshToken')

    await logoutSession()

    expect(mockedLogout).not.toHaveBeenCalled()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })
})
