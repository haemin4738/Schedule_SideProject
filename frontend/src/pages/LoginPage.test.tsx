import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import { login } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  socialLogin: vi.fn(),
  socialLink: vi.fn(),
}))

const mockedLogin = vi.mocked(login)

const ORIGIN = 'http://localhost:3000'

function LocationStateProbe() {
  const location = useLocation()
  return <output data-testid="location-state">{JSON.stringify(location.state)}</output>
}

const renderLogin = (state?: unknown) =>
  render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>HOME</div>} />
      </Routes>
      <LocationStateProbe />
    </MemoryRouter>,
  )

describe('LoginPage', () => {
  let assign: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.setState({ accessToken: null })
    vi.stubEnv('VITE_KAKAO_CLIENT_ID', 'kakao-client')
    vi.stubEnv('VITE_NAVER_CLIENT_ID', 'naver-client')
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'google-client')
    // jsdom 의 location.assign 은 재정의할 수 없어 location 전체를 대체한다
    assign = vi.fn()
    vi.stubGlobal('location', { origin: ORIGIN, href: `${ORIGIN}/login`, pathname: '/login', assign })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
    vi.resetAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.setState({ accessToken: null })
  })

  describe('이메일 로그인', () => {
    it('onSubmit_validCredentials_storesTokensAndNavigatesHome', async () => {
      const user = userEvent.setup()
      mockedLogin.mockResolvedValue({ data: { data: { accessToken: 'acc', refreshToken: 'ref' } } } as never)
      renderLogin()

      await user.type(screen.getByLabelText('이메일'), 'a@b.com')
      await user.type(screen.getByLabelText('비밀번호'), 'pw')
      await user.click(screen.getByRole('button', { name: '로그인' }))

      expect(await screen.findByText('HOME')).toBeInTheDocument()
      expect(mockedLogin).toHaveBeenCalledWith('a@b.com', 'pw')
      expect(localStorage.getItem('accessToken')).toBe('acc')
      expect(localStorage.getItem('refreshToken')).toBe('ref')
    })

    it('onSubmit_serverRejects_showsServerMessageAsAlert', async () => {
      const user = userEvent.setup()
      mockedLogin.mockRejectedValue({
        response: { status: 401, data: { success: false, data: null, error: '이메일 또는 비밀번호가 올바르지 않습니다.' } },
      })
      renderLogin()

      await user.type(screen.getByLabelText('이메일'), 'a@b.com')
      await user.type(screen.getByLabelText('비밀번호'), 'bad')
      await user.click(screen.getByRole('button', { name: '로그인' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다.')
      expect(localStorage.getItem('accessToken')).toBeNull()
    })

    it('onSubmit_networkError_showsFallbackMessage', async () => {
      const user = userEvent.setup()
      mockedLogin.mockRejectedValue(new Error('Network Error'))
      renderLogin()

      await user.type(screen.getByLabelText('이메일'), 'a@b.com')
      await user.type(screen.getByLabelText('비밀번호'), 'pw')
      await user.click(screen.getByRole('button', { name: '로그인' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.')
    })

    it('onSubmit_emptyFields_doesNotCallApi', async () => {
      const user = userEvent.setup()
      renderLogin()

      await user.click(screen.getByRole('button', { name: '로그인' }))

      expect(mockedLogin).not.toHaveBeenCalled()
    })
  })

  describe('소셜 로그인 버튼', () => {
    it.each([
      ['카카오', 'https://kauth.kakao.com/oauth/authorize', 'kakao'],
      ['네이버', 'https://nid.naver.com/oauth2.0/authorize', 'naver'],
      ['구글', 'https://accounts.google.com/o/oauth2/v2/auth', 'google'],
    ])('onSocialLogin_%s_redirectsToProviderAuthorizeUrl', async (label, authorizeUrl, provider) => {
      const user = userEvent.setup()
      renderLogin()

      await user.click(screen.getByRole('button', { name: `${label}로 로그인` }))

      await waitFor(() => expect(assign).toHaveBeenCalledTimes(1))
      const url = new URL(assign.mock.calls[0][0] as string)
      expect(`${url.origin}${url.pathname}`).toBe(authorizeUrl)
      expect(url.searchParams.get('client_id')).toBe(`${provider}-client`)
      expect(url.searchParams.get('redirect_uri')).toBe(`${ORIGIN}/oauth/callback/${provider}`)
      expect(url.searchParams.get('state')).toBe(sessionStorage.getItem(`lifelog.oauth.${provider}.state`))
      // 이동 중에는 버튼을 모두 막아 중복 요청을 방지한다
      expect(screen.getByRole('button', { name: `${label}로 이동 중...` })).toBeDisabled()
    })

    it('socialButtons_clientIdMissing_disabledWithSetupHint', () => {
      vi.stubEnv('VITE_NAVER_CLIENT_ID', '')
      renderLogin()

      const naver = screen.getByRole('button', { name: /네이버로 로그인/ })
      expect(naver).toBeDisabled()
      expect(naver).toHaveTextContent('(설정 필요)')
      expect(naver).toHaveAttribute('title', expect.stringContaining('client_id'))
      expect(screen.getByRole('button', { name: '카카오로 로그인' })).toBeEnabled()
      expect(screen.getByRole('button', { name: '구글로 로그인' })).toBeEnabled()
    })

    it('onSocialLogin_buildFails_showsErrorAndReenablesButtons', async () => {
      const user = userEvent.setup()
      vi.spyOn(crypto.subtle, 'digest').mockRejectedValueOnce(new Error('crypto 사용 불가'))
      renderLogin()

      await user.click(screen.getByRole('button', { name: '구글로 로그인' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('crypto 사용 불가')
      expect(assign).not.toHaveBeenCalled()
      expect(screen.getByRole('button', { name: '구글로 로그인' })).toBeEnabled()
    })

    it('pageshow_persistedAfterRedirect_resetsRedirectingState', async () => {
      const user = userEvent.setup()
      renderLogin()

      await user.click(screen.getByRole('button', { name: '카카오로 로그인' }))
      await waitFor(() => expect(assign).toHaveBeenCalled())
      expect(screen.getByRole('button', { name: '카카오로 이동 중...' })).toBeDisabled()

      act(() => {
        const event = new Event('pageshow') as PageTransitionEvent
        Object.defineProperty(event, 'persisted', { value: true })
        fireEvent(window, event)
      })

      expect(screen.getByRole('button', { name: '카카오로 로그인' })).toBeEnabled()
    })
  })

  describe('콜백에서 넘어온 오류', () => {
    it('LoginPage_socialErrorInRouterState_showsMessageAndClearsHistoryState', async () => {
      renderLogin({ socialError: '카카오 로그인이 취소되었습니다.' })

      expect(screen.getByRole('alert')).toHaveTextContent('카카오 로그인이 취소되었습니다.')
      await waitFor(() => expect(screen.getByTestId('location-state')).toHaveTextContent('null'))
      // state 를 비운 뒤에도 메시지는 유지된다
      expect(screen.getByRole('alert')).toHaveTextContent('카카오 로그인이 취소되었습니다.')
    })

    it('LoginPage_noRouterState_showsNoAlert', () => {
      renderLogin()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })
})
