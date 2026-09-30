import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import OAuthCallbackPage from './OAuthCallbackPage'
import LoginPage from './LoginPage'
import { socialLink, socialLogin, type SocialLoginResponse, type SocialProvider } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  socialLogin: vi.fn(),
  socialLink: vi.fn(),
}))

const mockedSocialLogin = vi.mocked(socialLogin)
const mockedSocialLink = vi.mocked(socialLink)

const LINK_TOKEN = 'link-token-secret-value'
const key = (provider: SocialProvider, name: string) => `lifelog.oauth.${provider}.${name}`

const seedSession = (provider: SocialProvider, values: { state?: string; codeVerifier?: string; nonce?: string }) => {
  Object.entries(values).forEach(([name, value]) => {
    if (value !== undefined) sessionStorage.setItem(key(provider, name), value)
  })
}

const apiError = (status: number, error?: string) =>
  Object.assign(new Error(`Request failed with status code ${status}`), {
    response: { status, data: { success: false, data: null, error } },
  })

const loggedIn = (): { data: { success: boolean; data: SocialLoginResponse } } => ({
  data: {
    success: true,
    data: {
      status: 'LOGGED_IN',
      newUser: false,
      token: { accessToken: 'social-access', refreshToken: 'social-refresh', tokenType: 'Bearer' },
      link: null,
    },
  },
})

const linkRequired = (provider: 'KAKAO' | 'NAVER' | 'GOOGLE' = 'GOOGLE') => ({
  data: {
    success: true,
    data: {
      status: 'LINK_REQUIRED',
      newUser: false,
      token: null,
      link: { linkToken: LINK_TOKEN, provider, maskedEmail: 'te**@example.com', expiresInSeconds: 600 },
    } satisfies SocialLoginResponse,
  },
})

const renderCallback = (url: string, { strict = false } = {}) => {
  const tree = (
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/oauth/callback/:provider" element={<OAuthCallbackPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>HOME</div>} />
      </Routes>
    </MemoryRouter>
  )
  return render(strict ? <StrictMode>{tree}</StrictMode> : tree)
}

const oauthKeysLeft = () =>
  Object.keys({ ...sessionStorage }).filter((k) => k.startsWith('lifelog.oauth.'))

const storageContains = (needle: string) =>
  [localStorage, sessionStorage].some((storage) =>
    Array.from({ length: storage.length }, (_, i) => storage.key(i)!).some(
      (k) => k.includes(needle) || (storage.getItem(k) ?? '').includes(needle),
    ),
  )

describe('OAuthCallbackPage', () => {
  let replaceState: MockInstance<History['replaceState']>

  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.setState({ accessToken: null })
    vi.stubEnv('VITE_KAKAO_CLIENT_ID', 'kakao-client')
    vi.stubEnv('VITE_NAVER_CLIENT_ID', 'naver-client')
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'google-client')
    // jsdom URL 을 바꾸지 않도록 실제 동작은 막고 호출만 확인한다
    replaceState = vi.spyOn(window.history, 'replaceState').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.resetAllMocks()
    vi.unstubAllEnvs()
    localStorage.clear()
    sessionStorage.clear()
    useAuthStore.setState({ accessToken: null })
  })

  describe('code 교환', () => {
    it('OAuthCallbackPage_renderedInStrictMode_callsSocialLoginOnce', async () => {
      seedSession('google', { state: 'st', codeVerifier: 'cv', nonce: 'nn' })
      mockedSocialLogin.mockResolvedValue(loggedIn() as never)

      renderCallback('/oauth/callback/google?code=abc&state=st', { strict: true })

      expect(await screen.findByText('HOME')).toBeInTheDocument()
      expect(mockedSocialLogin).toHaveBeenCalledTimes(1)
      // effect 재실행 시 ref 가드로 콜백 처리 자체가 한 번만 일어난다 (state 소비에 의한 우연한 1회가 아님)
      expect(replaceState).toHaveBeenCalledTimes(1)
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('OAuthCallbackPage_onMount_removesCodeFromUrlWithReplaceState', async () => {
      seedSession('kakao', { state: 'st', codeVerifier: 'cv' })
      mockedSocialLogin.mockResolvedValue(loggedIn() as never)

      renderCallback('/oauth/callback/kakao?code=secret-code&state=st')

      await screen.findByText('HOME')
      expect(replaceState).toHaveBeenCalledWith(window.history.state, '', '/oauth/callback/kakao')
      replaceState.mock.calls.forEach((call) => expect(String(call[2])).not.toContain('secret-code'))
    })

    it('OAuthCallbackPage_google_sendsCodeVerifierNonceAndState', async () => {
      seedSession('google', { state: 'st-g', codeVerifier: 'cv-g', nonce: 'nonce-g' })
      mockedSocialLogin.mockResolvedValue(loggedIn() as never)

      renderCallback('/oauth/callback/google?code=code-g&state=st-g')

      await screen.findByText('HOME')
      expect(mockedSocialLogin).toHaveBeenCalledWith('google', {
        grantType: 'AUTHORIZATION_CODE',
        code: 'code-g',
        redirectUri: `${window.location.origin}/oauth/callback/google`,
        codeVerifier: 'cv-g',
        nonce: 'nonce-g',
        state: 'st-g',
      })
    })

    it('OAuthCallbackPage_kakao_sendsCodeVerifierAndStateWithoutNonce', async () => {
      seedSession('kakao', { state: 'st-k', codeVerifier: 'cv-k' })
      mockedSocialLogin.mockResolvedValue(loggedIn() as never)

      renderCallback('/oauth/callback/kakao?code=code-k&state=st-k')

      await screen.findByText('HOME')
      const [provider, body] = mockedSocialLogin.mock.calls[0]
      expect(provider).toBe('kakao')
      expect(body).toMatchObject({
        grantType: 'AUTHORIZATION_CODE',
        code: 'code-k',
        redirectUri: `${window.location.origin}/oauth/callback/kakao`,
        codeVerifier: 'cv-k',
        state: 'st-k',
      })
      expect(body.nonce).toBeUndefined()
    })

    it('OAuthCallbackPage_naver_sendsStateOnlyWithoutCodeVerifier', async () => {
      seedSession('naver', { state: 'st-n' })
      mockedSocialLogin.mockResolvedValue(loggedIn() as never)

      renderCallback('/oauth/callback/naver?code=code-n&state=st-n')

      await screen.findByText('HOME')
      const [provider, body] = mockedSocialLogin.mock.calls[0]
      expect(provider).toBe('naver')
      expect(body).toMatchObject({ code: 'code-n', state: 'st-n', grantType: 'AUTHORIZATION_CODE' })
      expect(body.codeVerifier).toBeUndefined()
      expect(body.nonce).toBeUndefined()
    })

    it('OAuthCallbackPage_loggedIn_storesTokensAndNavigatesHome', async () => {
      seedSession('google', { state: 'st', codeVerifier: 'cv', nonce: 'nn' })
      mockedSocialLogin.mockResolvedValue(loggedIn() as never)

      renderCallback('/oauth/callback/google?code=abc&state=st')

      expect(await screen.findByText('HOME')).toBeInTheDocument()
      expect(useAuthStore.getState().accessToken).toBe('social-access')
      expect(localStorage.getItem('accessToken')).toBe('social-access')
      expect(localStorage.getItem('refreshToken')).toBe('social-refresh')
      expect(oauthKeysLeft()).toEqual([])
    })
  })

  describe('제공자 오류 / 검증 실패', () => {
    it('OAuthCallbackPage_accessDenied_navigatesToLoginWithCancelMessage', async () => {
      seedSession('kakao', { state: 'st', codeVerifier: 'cv' })

      renderCallback('/oauth/callback/kakao?error=access_denied&state=st')

      expect(await screen.findByRole('alert')).toHaveTextContent('카카오 로그인이 취소되었습니다.')
      expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
      expect(mockedSocialLogin).not.toHaveBeenCalled()
      expect(oauthKeysLeft()).toEqual([])
    })

    it('OAuthCallbackPage_otherProviderError_navigatesToLoginWithFailureMessage', async () => {
      seedSession('naver', { state: 'st' })

      renderCallback('/oauth/callback/naver?error=server_error&state=st')

      expect(await screen.findByRole('alert')).toHaveTextContent('네이버 로그인에 실패했습니다. 다시 시도해 주세요.')
      expect(mockedSocialLogin).not.toHaveBeenCalled()
      expect(oauthKeysLeft()).toEqual([])
    })

    it('OAuthCallbackPage_stateMismatch_showsErrorWithoutApiCall', async () => {
      seedSession('google', { state: 'saved', codeVerifier: 'cv', nonce: 'nn' })

      renderCallback('/oauth/callback/google?code=abc&state=tampered')

      expect(await screen.findByRole('alert')).toHaveTextContent('로그인 요청 정보가 일치하지 않습니다.')
      expect(screen.getByRole('link', { name: '로그인 화면으로 돌아가기' })).toHaveAttribute('href', '/login')
      expect(mockedSocialLogin).not.toHaveBeenCalled()
      expect(oauthKeysLeft()).toEqual([])
    })

    it('OAuthCallbackPage_noSavedSession_showsErrorWithoutApiCall', async () => {
      renderCallback('/oauth/callback/kakao?code=abc&state=st')

      expect(await screen.findByRole('alert')).toHaveTextContent('로그인 요청 정보가 일치하지 않습니다.')
      expect(screen.getByRole('link', { name: '로그인 화면으로 돌아가기' })).toBeInTheDocument()
      expect(mockedSocialLogin).not.toHaveBeenCalled()
    })

    it('OAuthCallbackPage_stateMissingInQuery_showsErrorWithoutApiCall', async () => {
      seedSession('kakao', { state: 'st', codeVerifier: 'cv' })

      renderCallback('/oauth/callback/kakao?code=abc')

      expect(await screen.findByRole('alert')).toHaveTextContent('로그인 요청 정보가 일치하지 않습니다.')
      expect(mockedSocialLogin).not.toHaveBeenCalled()
      expect(oauthKeysLeft()).toEqual([])
    })

    it('OAuthCallbackPage_codeMissing_showsErrorAndClearsSession', async () => {
      seedSession('google', { state: 'st', codeVerifier: 'cv', nonce: 'nn' })

      renderCallback('/oauth/callback/google?state=st')

      expect(await screen.findByRole('alert')).toHaveTextContent('구글 로그인에 실패했습니다. 다시 시도해 주세요.')
      expect(mockedSocialLogin).not.toHaveBeenCalled()
      expect(oauthKeysLeft()).toEqual([])
    })

    it('OAuthCallbackPage_unknownProvider_showsError', async () => {
      renderCallback('/oauth/callback/apple?code=abc&state=st')

      expect(await screen.findByRole('alert')).toHaveTextContent('지원하지 않는 소셜 로그인입니다.')
      expect(screen.getByRole('link', { name: '로그인 화면으로 돌아가기' })).toBeInTheDocument()
      expect(mockedSocialLogin).not.toHaveBeenCalled()
    })
  })

  describe('서버 오류', () => {
    it.each([
      [400, 'redirectUri 가 허용되지 않습니다.'],
      [502, '카카오 서버와 통신하지 못했습니다.'],
      [503, '카카오 로그인이 일시적으로 비활성화되었습니다.'],
    ])('OAuthCallbackPage_socialLogin%s_showsServerMessageAndBackLink', async (status, message) => {
      seedSession('kakao', { state: 'st', codeVerifier: 'cv' })
      mockedSocialLogin.mockRejectedValue(apiError(status, message))

      renderCallback('/oauth/callback/kakao?code=abc&state=st')

      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByRole('link', { name: '로그인 화면으로 돌아가기' })).toBeInTheDocument()
      expect(useAuthStore.getState().accessToken).toBeNull()
      expect(oauthKeysLeft()).toEqual([])
    })

    it('OAuthCallbackPage_networkErrorWithoutMessage_showsFallbackMessage', async () => {
      seedSession('kakao', { state: 'st', codeVerifier: 'cv' })
      mockedSocialLogin.mockRejectedValue(new Error('Network Error'))

      renderCallback('/oauth/callback/kakao?code=abc&state=st')

      expect(await screen.findByRole('alert')).toHaveTextContent('카카오 로그인에 실패했습니다. 다시 시도해 주세요.')
    })
  })

  describe('LINK_REQUIRED', () => {
    const renderLinkRequired = async () => {
      seedSession('google', { state: 'st', codeVerifier: 'cv', nonce: 'nn' })
      mockedSocialLogin.mockResolvedValue(linkRequired() as never)
      renderCallback('/oauth/callback/google?code=abc&state=st')
      await screen.findByRole('form', { name: '소셜 계정 연결' })
    }

    it('OAuthCallbackPage_linkRequired_showsMaskedEmailAndProvider', async () => {
      await renderLinkRequired()

      expect(screen.getByText('te**@example.com')).toBeInTheDocument()
      expect(screen.getByText(/구글 계정을/)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '연결하고 로그인' })).toBeDisabled()
      expect(oauthKeysLeft()).toEqual([])
    })

    it('OAuthCallbackPage_linkRequired_doesNotPersistLinkToken', async () => {
      await renderLinkRequired()

      expect(storageContains(LINK_TOKEN)).toBe(false)
    })

    it('OAuthCallbackPage_linkPasswordSubmitted_linksAndLogsIn', async () => {
      const user = userEvent.setup()
      mockedSocialLink.mockResolvedValue({
        data: { success: true, data: { accessToken: 'linked-access', refreshToken: 'linked-refresh', tokenType: 'Bearer' } },
      } as never)
      await renderLinkRequired()

      await user.type(screen.getByLabelText('기존 비밀번호'), 'pw1234!')
      await user.click(screen.getByRole('button', { name: '연결하고 로그인' }))

      expect(await screen.findByText('HOME')).toBeInTheDocument()
      expect(mockedSocialLink).toHaveBeenCalledWith(LINK_TOKEN, 'pw1234!')
      expect(localStorage.getItem('accessToken')).toBe('linked-access')
      expect(localStorage.getItem('refreshToken')).toBe('linked-refresh')
      expect(storageContains(LINK_TOKEN)).toBe(false)
    })

    it('OAuthCallbackPage_linkUnauthorized_showsMessageAndAllowsRetry', async () => {
      const user = userEvent.setup()
      mockedSocialLink
        .mockRejectedValueOnce(apiError(401, '비밀번호가 일치하지 않습니다.'))
        .mockResolvedValueOnce({
          data: { success: true, data: { accessToken: 'a2', refreshToken: 'r2', tokenType: 'Bearer' } },
        } as never)
      await renderLinkRequired()

      await user.type(screen.getByLabelText('기존 비밀번호'), 'wrong')
      await user.click(screen.getByRole('button', { name: '연결하고 로그인' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('비밀번호가 일치하지 않습니다.')
      const input = screen.getByLabelText('기존 비밀번호')
      expect(input).toBeEnabled()
      expect(input).toHaveValue('')
      expect(localStorage.getItem('accessToken')).toBeNull()

      await user.type(input, 'right')
      await user.click(screen.getByRole('button', { name: '연결하고 로그인' }))

      expect(await screen.findByText('HOME')).toBeInTheDocument()
      expect(mockedSocialLink).toHaveBeenLastCalledWith(LINK_TOKEN, 'right')
      expect(mockedSocialLink).toHaveBeenCalledTimes(2)
    })

    it('OAuthCallbackPage_linkConflict_disablesInput', async () => {
      const user = userEvent.setup()
      mockedSocialLink.mockRejectedValue(apiError(409, '이미 다른 계정에 연결된 소셜 계정입니다.'))
      await renderLinkRequired()

      await user.type(screen.getByLabelText('기존 비밀번호'), 'pw')
      await user.click(screen.getByRole('button', { name: '연결하고 로그인' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('이미 다른 계정에 연결된 소셜 계정입니다.')
      expect(screen.getByLabelText('기존 비밀번호')).toBeDisabled()
      expect(screen.getByRole('button', { name: '연결하고 로그인' })).toBeDisabled()
      expect(screen.getByRole('link', { name: '로그인 화면으로 돌아가기' })).toBeInTheDocument()
      expect(mockedSocialLink).toHaveBeenCalledTimes(1)
    })

    it('OAuthCallbackPage_linkSubmitting_disablesButtonAndPreventsDoubleSubmit', async () => {
      const user = userEvent.setup()
      let resolve!: (v: unknown) => void
      mockedSocialLink.mockReturnValue(new Promise((r) => (resolve = r)) as never)
      await renderLinkRequired()

      await user.type(screen.getByLabelText('기존 비밀번호'), 'pw')
      await user.click(screen.getByRole('button', { name: '연결하고 로그인' }))

      const busy = screen.getByRole('button', { name: '연결 중...' })
      expect(busy).toBeDisabled()
      await user.click(busy)
      expect(mockedSocialLink).toHaveBeenCalledTimes(1)

      resolve({ data: { success: true, data: { accessToken: 'a', refreshToken: 'r', tokenType: 'Bearer' } } })
      await waitFor(() => expect(screen.getByText('HOME')).toBeInTheDocument())
    })
  })
})
