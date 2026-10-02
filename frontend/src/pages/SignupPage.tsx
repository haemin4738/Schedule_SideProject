import { login, signup } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errorMessage'
import { PASSWORD_PATTERN, PASSWORD_RULE_MESSAGE } from '@/auth/password'
import { useAuthStore } from '@/store/authStore'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

interface FormValues {
  name: string
  email: string
  password: string
  passwordConfirm: string
}

const INPUT_CLASS =
  'w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400 aria-[invalid=true]:border-red-400'

export default function SignupPage() {
  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ mode: 'onBlur' })
  const loginStore = useAuthStore((s) => s.login)
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async ({ name, email, password }: FormValues) => {
    setError(null)
    try {
      await signup(email, password, name.trim())
    } catch (err) {
      setError(getApiErrorMessage(err, '회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.'))
      return
    }
    // 가입 API 는 토큰을 주지 않으므로 같은 자격증명으로 바로 로그인한다
    try {
      const { data } = await login(email, password)
      loginStore(data.data.accessToken, data.data.refreshToken)
      navigate('/', { replace: true })
    } catch {
      navigate('/login', { replace: true, state: { notice: '가입이 완료되었습니다. 로그인해 주세요.' } })
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow">
        <h1 className="mb-6 text-2xl font-semibold">회원가입</h1>

        {error && (
          <p role="alert" className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <form onSubmit={handleSubmit(onSubmit)} aria-label="회원가입" noValidate className="flex flex-col gap-3">
          <div>
            <label htmlFor="signup-name" className="mb-1 block text-sm text-gray-600">
              이름
            </label>
            <input
              id="signup-name"
              {...register('name', {
                validate: (v) => v.trim().length > 0 || '이름을 입력해 주세요.',
              })}
              autoComplete="name"
              maxLength={12}
              aria-invalid={errors.name ? true : undefined}
              aria-describedby={errors.name ? 'signup-name-error' : undefined}
              className={INPUT_CLASS}
            />
            {errors.name && (
              <p id="signup-name-error" className="mt-1 text-xs text-red-600">
                {errors.name.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="signup-email" className="mb-1 block text-sm text-gray-600">
              이메일
            </label>
            <input
              id="signup-email"
              type="email"
              {...register('email', {
                required: '이메일을 입력해 주세요.',
                pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: '올바른 이메일 형식이 아닙니다.' },
              })}
              autoComplete="email"
              aria-invalid={errors.email ? true : undefined}
              aria-describedby={errors.email ? 'signup-email-error' : undefined}
              className={INPUT_CLASS}
            />
            {errors.email && (
              <p id="signup-email-error" className="mt-1 text-xs text-red-600">
                {errors.email.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="signup-password" className="mb-1 block text-sm text-gray-600">
              비밀번호
            </label>
            <input
              id="signup-password"
              type="password"
              {...register('password', {
                required: '비밀번호를 입력해 주세요.',
                pattern: { value: PASSWORD_PATTERN, message: PASSWORD_RULE_MESSAGE },
                // 비밀번호를 고치면 이미 입력한 '비밀번호 확인' 도 다시 검사한다
                deps: ['passwordConfirm'],
              })}
              autoComplete="new-password"
              aria-invalid={errors.password ? true : undefined}
              aria-describedby="signup-password-hint"
              className={INPUT_CLASS}
            />
            <p id="signup-password-hint" className={`mt-1 text-xs ${errors.password ? 'text-red-600' : 'text-gray-500'}`}>
              {errors.password?.message ?? '영문, 숫자, 특수문자를 각각 1자 이상 포함한 9~15자'}
            </p>
          </div>

          <div>
            <label htmlFor="signup-password-confirm" className="mb-1 block text-sm text-gray-600">
              비밀번호 확인
            </label>
            <input
              id="signup-password-confirm"
              type="password"
              {...register('passwordConfirm', {
                required: '비밀번호를 한 번 더 입력해 주세요.',
                validate: (v) => v === getValues('password') || '비밀번호가 일치하지 않습니다.',
              })}
              autoComplete="new-password"
              aria-invalid={errors.passwordConfirm ? true : undefined}
              aria-describedby={errors.passwordConfirm ? 'signup-password-confirm-error' : undefined}
              className={INPUT_CLASS}
            />
            {errors.passwordConfirm && (
              <p id="signup-password-confirm-error" className="mt-1 text-xs text-red-600">
                {errors.passwordConfirm.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="mt-3 w-full rounded bg-blue-500 py-2 text-white hover:bg-blue-600 disabled:opacity-60"
          >
            {isSubmitting ? '가입 중...' : '가입하기'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-gray-500">
          이미 계정이 있나요?{' '}
          <Link to="/login" className="text-blue-500 hover:underline">
            로그인
          </Link>
        </p>
      </div>
    </div>
  )
}
