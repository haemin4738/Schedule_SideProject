import { login } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'

interface FormValues {
  email: string
  password: string
}

export default function LoginPage() {
  const { register, handleSubmit } = useForm<FormValues>()
  const loginStore = useAuthStore((s) => s.login)
  const navigate = useNavigate()

  const onSubmit = async ({ email, password }: FormValues) => {
    const { data } = await login(email, password)
    loginStore(data.data.accessToken, data.data.refreshToken)
    navigate('/')
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="w-full max-w-sm rounded-xl bg-white p-8 shadow"
      >
        <h1 className="mb-6 text-2xl font-semibold">로그인</h1>
        <input
          {...register('email')}
          type="email"
          placeholder="이메일"
          className="mb-3 w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
        />
        <input
          {...register('password')}
          type="password"
          placeholder="비밀번호"
          className="mb-6 w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
        />
        <button
          type="submit"
          className="w-full rounded bg-blue-500 py-2 text-white hover:bg-blue-600"
        >
          로그인
        </button>
      </form>
    </div>
  )
}
