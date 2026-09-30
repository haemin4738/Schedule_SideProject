import { socialLink, socialLogin, type SocialLinkInfo, type SocialProvider } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errorMessage'
import {
  getProviderLabel,
  getRedirectUri,
  isSocialProvider,
  takeCodeVerifierAndNonce,
  verifyAndConsumeState,
} from '@/auth/social'
import type { LoginLocationState } from '@/pages/LoginPage'
import { useAuthStore } from '@/store/authStore'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'

type Phase =
  | { kind: 'processing' }
  | { kind: 'error'; message: string }
  | { kind: 'link'; provider: SocialProvider; link: SocialLinkInfo }

const loginFailedMessage = (label: string) => `${label} 로그인에 실패했습니다. 다시 시도해 주세요.`

type CallbackResult =
  | { kind: 'loggedIn'; accessToken: string; refreshToken: string }
  | { kind: 'backToLogin'; message: string }
  | Exclude<Phase, { kind: 'processing' }>

/**
 * 콜백 쿼리(code/state/error)를 검증하고 code 를 교환한다.
 * state 대조 후 저장값은 즉시 지우고, code_verifier·nonce 는 API 호출 직전에 꺼내 지운다.
 */
const handleCallback = async (providerParam: string | undefined, search: string): Promise<CallbackResult> => {
  if (!isSocialProvider(providerParam)) {
    return { kind: 'error', message: '지원하지 않는 소셜 로그인입니다.' }
  }
  const provider = providerParam
  const label = getProviderLabel(provider)
  const params = new URLSearchParams(search)
  const code = params.get('code')
  const state = params.get('state')
  const providerError = params.get('error')

  if (providerError) {
    verifyAndConsumeState(provider, null) // 남은 state·verifier·nonce 정리
    return {
      kind: 'backToLogin',
      message: providerError === 'access_denied' ? `${label} 로그인이 취소되었습니다.` : loginFailedMessage(label),
    }
  }

  if (!verifyAndConsumeState(provider, state)) {
    return { kind: 'error', message: '로그인 요청 정보가 일치하지 않습니다. 로그인 화면에서 다시 시도해 주세요.' }
  }

  const { codeVerifier, nonce } = takeCodeVerifierAndNonce(provider)
  if (!code) {
    return { kind: 'error', message: loginFailedMessage(label) }
  }

  try {
    const { data } = await socialLogin(provider, {
      grantType: 'AUTHORIZATION_CODE',
      code,
      redirectUri: getRedirectUri(provider),
      codeVerifier,
      nonce,
      state: state ?? undefined,
    })
    const result = data.data
    return result.status === 'LOGGED_IN'
      ? { kind: 'loggedIn', accessToken: result.token.accessToken, refreshToken: result.token.refreshToken }
      : { kind: 'link', provider, link: result.link }
  } catch (err) {
    return { kind: 'error', message: getApiErrorMessage(err, loginFailedMessage(label)) }
  }
}

export default function OAuthCallbackPage() {
  const { provider: providerParam } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const loginStore = useAuthStore((s) => s.login)
  const [phase, setPhase] = useState<Phase>({ kind: 'processing' })
  // StrictMode 개발 모드에서 effect 가 두 번 실행돼도 code 는 한 번만 교환한다 (ref 는 재실행 사이에 유지된다)
  const handledRef = useRef(false)

  useEffect(() => {
    if (handledRef.current) return
    handledRef.current = true

    // code 가 주소창·히스토리·Referer 에 남지 않도록 즉시 제거한다 (router state 는 유지)
    window.history.replaceState(window.history.state, '', location.pathname)

    void handleCallback(providerParam, location.search).then((result) => {
      switch (result.kind) {
        case 'loggedIn':
          loginStore(result.accessToken, result.refreshToken)
          navigate('/', { replace: true })
          break
        case 'backToLogin':
          navigate('/login', { replace: true, state: { socialError: result.message } satisfies LoginLocationState })
          break
        default:
          setPhase(result)
      }
    })
  }, [location.pathname, location.search, providerParam, navigate, loginStore])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow">
        {phase.kind === 'processing' && (
          <p role="status" className="text-center text-sm text-gray-600">
            로그인 처리 중입니다...
          </p>
        )}

        {phase.kind === 'error' && (
          <>
            <h1 className="mb-4 text-xl font-semibold">소셜 로그인 실패</h1>
            <p role="alert" className="mb-6 rounded bg-red-50 px-3 py-2 text-sm text-red-700">
              {phase.message}
            </p>
            <Link to="/login" replace className="text-sm text-blue-600 hover:underline">
              로그인 화면으로 돌아가기
            </Link>
          </>
        )}

        {phase.kind === 'link' && (
          <LinkAccountForm
            provider={phase.provider}
            link={phase.link}
            onLinked={(accessToken, refreshToken) => {
              loginStore(accessToken, refreshToken)
              navigate('/', { replace: true })
            }}
          />
        )}
      </div>
    </div>
  )
}

interface LinkAccountFormProps {
  provider: SocialProvider
  link: SocialLinkInfo
  onLinked: (accessToken: string, refreshToken: string) => void
}

/** LINK_REQUIRED: 기존 계정 비밀번호로 소셜 계정을 연결한다. linkToken 은 props(메모리)로만 다룬다. */
function LinkAccountForm({ provider, link, onLinked }: LinkAccountFormProps) {
  const label = getProviderLabel(provider)
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 409(그 사이 다른 계정이 연결됨)는 재시도해도 소용없으므로 입력을 막는다
  const [closed, setClosed] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!password || submitting || closed) return
    setSubmitting(true)
    setError(null)
    try {
      const { data } = await socialLink(link.linkToken, password)
      onLinked(data.data.accessToken, data.data.refreshToken)
    } catch (err) {
      const status = (err as { response?: { status?: number } } | null)?.response?.status
      // 401 은 오답(재입력 가능)과 만료·횟수 초과(서버 메시지가 재시도를 안내)를 구분할 코드가 없어 입력은 열어 둔다
      if (status === 409) setClosed(true)
      setError(getApiErrorMessage(err, '계정 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.'))
      setPassword('')
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={onSubmit} aria-label="소셜 계정 연결">
      <h1 className="mb-4 text-xl font-semibold">기존 계정과 연결</h1>
      <p className="mb-4 text-sm text-gray-700">
        <strong>{link.maskedEmail}</strong> 계정이 이미 있습니다. 기존 비밀번호를 입력하면 {label} 계정을
        연결합니다.
      </p>

      {error && (
        <p role="alert" className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <label htmlFor="link-password" className="mb-1 block text-sm font-medium text-gray-700">
        기존 비밀번호
      </label>
      <input
        id="link-password"
        type="password"
        autoFocus
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        disabled={closed}
        maxLength={128}
        required
        className="mb-4 w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400 disabled:bg-gray-100"
      />
      <button
        type="submit"
        disabled={submitting || closed || !password}
        className="mb-4 w-full rounded bg-blue-500 py-2 text-white hover:bg-blue-600 disabled:opacity-60"
      >
        {submitting ? '연결 중...' : '연결하고 로그인'}
      </button>
      <Link to="/login" replace className="text-sm text-blue-600 hover:underline">
        로그인 화면으로 돌아가기
      </Link>
    </form>
  )
}
