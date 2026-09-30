import { login, type SocialProvider } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errorMessage'
import {
  SOCIAL_PROVIDERS,
  buildAuthorizeUrl,
  getProviderLabel,
  isProviderConfigured,
} from '@/auth/social'
import SessionNoticeDialog from '@/components/SessionNoticeDialog'
import { useAuthStore } from '@/store/authStore'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'

interface FormValues {
  email: string
  password: string
}

/** 콜백 페이지가 navigate('/login', { state: { socialError } }) 로 넘기는 안내 메시지 */
export interface LoginLocationState {
  socialError?: string
  /** 회원가입 직후 자동 로그인에 실패했을 때 등 오류가 아닌 안내 */
  notice?: string
}

// 제공자 브랜드 색 (카카오 노랑, 네이버 초록, 구글 흰 바탕 + 테두리)
const SOCIAL_BUTTON_STYLES: Record<SocialProvider, string> = {
  kakao: 'bg-[#FEE500] text-black/85 hover:brightness-95',
  naver: 'bg-[#03C75A] text-white hover:brightness-95',
  google: 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50',
}

export default function LoginPage() {
  const {
    register,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<FormValues>()
  const loginStore = useAuthStore((s) => s.login)
  const sessionNotice = useAuthStore((s) => s.sessionNotice)
  const clearSessionNotice = useAuthStore((s) => s.clearSessionNotice)
  const navigate = useNavigate()
  const location = useLocation()
  const { socialError, notice } = (location.state as LoginLocationState | null) ?? {}
  const [error, setError] = useState<string | null>(socialError ?? null)
  const [info, setInfo] = useState<string | null>(notice ?? null)
  const [redirecting, setRedirecting] = useState<SocialProvider | null>(null)

  // 콜백에서 넘어온 메시지는 한 번만 보여 준다 (새로고침 시 history state 로 다시 뜨지 않게 비운다)
  useEffect(() => {
    if (socialError || notice) navigate(location.pathname, { replace: true, state: null })
  }, [socialError, notice, location.pathname, navigate])

  // 제공자 화면에서 뒤로 가기로 bfcache 복원되면 '이동 중' 상태를 풀어 준다
  useEffect(() => {
    const onPageShow = (e: PageTransitionEvent) => {
      if (e.persisted) setRedirecting(null)
    }
    window.addEventListener('pageshow', onPageShow)
    return () => window.removeEventListener('pageshow', onPageShow)
  }, [])

  const onSubmit = async ({ email, password }: FormValues) => {
    setError(null)
    setInfo(null)
    try {
      const { data } = await login(email, password)
      loginStore(data.data.accessToken, data.data.refreshToken)
      navigate('/')
    } catch (err) {
      setError(getApiErrorMessage(err, '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.'))
    }
  }

  const onSocialLogin = async (provider: SocialProvider) => {
    setError(null)
    setInfo(null)
    setRedirecting(provider)
    try {
      window.location.assign(await buildAuthorizeUrl(provider))
    } catch (err) {
      setRedirecting(null)
      setError(err instanceof Error ? err.message : `${getProviderLabel(provider)} 로그인을 시작하지 못했습니다.`)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      {/* 보안 안내 팝업이 떠 있는 동안 배경 로그인 폼은 조작·포커스 불가 */}
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow" inert={sessionNotice ? true : undefined}>
        <h1 className="mb-6 text-2xl font-semibold">로그인</h1>

        {info && (
          <p role="status" className="mb-4 rounded bg-green-50 px-3 py-2 text-sm text-green-700">
            {info}
          </p>
        )}

        {error && (
          <p role="alert" className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <form onSubmit={handleSubmit(onSubmit)} aria-label="이메일 로그인">
          <label htmlFor="login-email" className="sr-only">
            이메일
          </label>
          <input
            id="login-email"
            {...register('email', { required: true })}
            required
            type="email"
            autoComplete="email"
            placeholder="이메일"
            className="mb-3 w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
          />
          <label htmlFor="login-password" className="sr-only">
            비밀번호
          </label>
          <input
            id="login-password"
            {...register('password', { required: true })}
            required
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호"
            className="mb-6 w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
          />
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded bg-blue-500 py-2 text-white hover:bg-blue-600 disabled:opacity-60"
          >
            로그인
          </button>
        </form>

        <div className="my-6 flex items-center gap-3 text-xs text-gray-400">
          <span className="h-px flex-1 bg-gray-200" />
          또는
          <span className="h-px flex-1 bg-gray-200" />
        </div>

        <div className="flex flex-col gap-2">
          {SOCIAL_PROVIDERS.map((provider) => {
            const label = getProviderLabel(provider)
            const configured = isProviderConfigured(provider)
            return (
              <button
                key={provider}
                type="button"
                onClick={() => onSocialLogin(provider)}
                disabled={!configured || redirecting !== null}
                title={configured ? undefined : `${label} 로그인 설정(client_id)이 없어 사용할 수 없습니다.`}
                className={`w-full rounded py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50 ${SOCIAL_BUTTON_STYLES[provider]}`}
              >
                {redirecting === provider ? `${label}로 이동 중...` : `${label}로 로그인`}
                {!configured && ' (설정 필요)'}
              </button>
            )
          })}
        </div>

        <p className="mt-6 text-center text-sm text-gray-500">
          계정이 없나요?{' '}
          <Link to="/signup" className="text-blue-500 hover:underline">
            회원가입
          </Link>
        </p>
      </div>

      {sessionNotice && <SessionNoticeDialog message={sessionNotice} onClose={clearSessionNotice} />}
    </div>
  )
}
