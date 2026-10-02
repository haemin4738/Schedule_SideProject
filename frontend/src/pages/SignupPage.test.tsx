import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SignupPage from './SignupPage'
import { PASSWORD_PATTERN } from '@/auth/password'
import { login, signup } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  signup: vi.fn(),
}))

const mockedLogin = vi.mocked(login)
const mockedSignup = vi.mocked(signup)

function LoginProbe() {
  const location = useLocation()
  return <output data-testid="login-state">{JSON.stringify(location.state)}</output>
}

const renderSignup = () =>
  render(
    <MemoryRouter initialEntries={['/signup']}>
      <Routes>
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/login" element={<LoginProbe />} />
        <Route path="/" element={<div>HOME</div>} />
      </Routes>
    </MemoryRouter>,
  )

const fillForm = async (
  user: ReturnType<typeof userEvent.setup>,
  values: Partial<{ name: string; email: string; password: string; passwordConfirm: string }> = {},
) => {
  const { name = '홍길동', email = 'a@b.com', password = 'Abcd1234!', passwordConfirm = password } = values
  if (name) await user.type(screen.getByLabelText('이름'), name)
  if (email) await user.type(screen.getByLabelText('이메일'), email)
  if (password) await user.type(screen.getByLabelText('비밀번호'), password)
  if (passwordConfirm) await user.type(screen.getByLabelText('비밀번호 확인'), passwordConfirm)
}

describe('SignupPage', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({ accessToken: null })
  })

  afterEach(() => {
    vi.resetAllMocks()
    localStorage.clear()
    useAuthStore.setState({ accessToken: null })
  })

  it('onSubmit_validInput_signsUpThenLogsInAndNavigatesHome', async () => {
    const user = userEvent.setup()
    mockedSignup.mockResolvedValue({ data: { data: { id: 1 } } } as never)
    mockedLogin.mockResolvedValue({ data: { data: { accessToken: 'acc', refreshToken: 'ref' } } } as never)
    renderSignup()

    await fillForm(user, { name: '  홍길동  ' })
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('HOME')).toBeInTheDocument()
    expect(mockedSignup).toHaveBeenCalledWith('a@b.com', 'Abcd1234!', '홍길동')
    expect(mockedLogin).toHaveBeenCalledWith('a@b.com', 'Abcd1234!')
    expect(localStorage.getItem('accessToken')).toBe('acc')
    expect(localStorage.getItem('refreshToken')).toBe('ref')
  })

  it('onSubmit_emailAlreadyUsed_showsServerMessageAndDoesNotLogin', async () => {
    const user = userEvent.setup()
    mockedSignup.mockRejectedValue({
      response: { status: 409, data: { success: false, data: null, error: '이미 사용중인 이메일입니다.' } },
    })
    renderSignup()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용중인 이메일입니다.')
    expect(mockedLogin).not.toHaveBeenCalled()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('onSubmit_networkError_showsFallbackMessage', async () => {
    const user = userEvent.setup()
    mockedSignup.mockRejectedValue(new Error('Network Error'))
    renderSignup()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.')
  })

  it('onSubmit_loginAfterSignupFails_navigatesToLoginWithNotice', async () => {
    const user = userEvent.setup()
    mockedSignup.mockResolvedValue({ data: { data: { id: 1 } } } as never)
    mockedLogin.mockRejectedValue(new Error('Network Error'))
    renderSignup()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByTestId('login-state')).toHaveTextContent('가입이 완료되었습니다. 로그인해 주세요.')
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('onSubmit_passwordMismatch_showsErrorAndDoesNotCallApi', async () => {
    const user = userEvent.setup()
    renderSignup()

    await fillForm(user, { passwordConfirm: 'Abcd1234@' })
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
    expect(screen.getByLabelText('비밀번호 확인')).toHaveAttribute('aria-invalid', 'true')
    expect(mockedSignup).not.toHaveBeenCalled()
  })

  it('onSubmit_passwordViolatesPolicy_showsPolicyMessageAndDoesNotCallApi', async () => {
    const user = userEvent.setup()
    renderSignup()

    await fillForm(user, { password: 'abcdefghij' })
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함한 9~15자여야 합니다.')).toBeInTheDocument()
    expect(mockedSignup).not.toHaveBeenCalled()
  })

  it('onSubmit_emptyFields_showsRequiredMessages', async () => {
    const user = userEvent.setup()
    renderSignup()

    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('이름을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('이메일을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호를 한 번 더 입력해 주세요.')).toBeInTheDocument()
    expect(mockedSignup).not.toHaveBeenCalled()
  })

  it('onSubmit_whitespaceOnlyName_showsNameErrorAndDoesNotCallApi', async () => {
    const user = userEvent.setup()
    renderSignup()

    await fillForm(user, { name: '   ' })
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('이름을 입력해 주세요.')).toBeInTheDocument()
    expect(mockedSignup).not.toHaveBeenCalled()
  })

  it('onSubmit_invalidEmail_showsFormatError', async () => {
    const user = userEvent.setup()
    renderSignup()

    await fillForm(user, { email: 'not-an-email' })
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('올바른 이메일 형식이 아닙니다.')).toBeInTheDocument()
    expect(mockedSignup).not.toHaveBeenCalled()
  })

  it('onSubmit_whilePending_disablesButtonAndCallsSignupOnce', async () => {
    const user = userEvent.setup()
    mockedSignup.mockReturnValue(new Promise(() => {}))
    renderSignup()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    const pending = await screen.findByRole('button', { name: '가입 중...' })
    expect(pending).toBeDisabled()
    await user.click(pending)
    expect(mockedSignup).toHaveBeenCalledTimes(1)
  })

  it('onChange_passwordEditedAfterConfirmMatched_revalidatesConfirm', async () => {
    const user = userEvent.setup()
    renderSignup()

    await fillForm(user)
    await user.type(screen.getByLabelText('비밀번호'), 'x')
    await user.tab()

    expect(await screen.findByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
  })

  it('render_always_linksToLoginPage', () => {
    renderSignup()
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login')
  })

  it('onType_nameLongerThan12_isCutAt12', async () => {
    const user = userEvent.setup()
    renderSignup()

    // 서버 SignupRequest.name 의 @Size(max = 12) 와 맞춘다
    await user.type(screen.getByLabelText('이름'), '가나다라마바사아자차카타파하')

    expect(screen.getByLabelText('이름')).toHaveValue('가나다라마바사아자차카타')
  })
})

describe('PASSWORD_PATTERN', () => {
  it.each([
    'Abcd1234!',
    'a1!aaaaaa',
    'Z9~zzzzzzzzzzzz',
    // 백엔드 정규식과 경계가 어긋나기 쉬운 문자 클래스 경계 특수문자
    'Abcd1234[',
    'Abcd1234\\',
    'Abcd1234]',
    'Abcd1234`',
    'Abcd1234{',
    'Abcd1234/',
    'Abcd1234:',
    'Abcd1234@',
  ])('PASSWORD_PATTERN_validPassword_matches_%s', (pw) => {
    expect(PASSWORD_PATTERN.test(pw)).toBe(true)
  })

  it.each([
    ['8자', 'Abc1234!'],
    ['16자', 'Abcd1234!Abcd123'],
    ['특수문자 없음', 'Abcd12345'],
    ['숫자 없음', 'Abcdefgh!'],
    ['영문 없음', '12345678!'],
    ['한글 포함', 'Abcd1234!가'],
    ['공백 포함', 'Abcd 1234!'],
    ['전각 특수문자만', 'Abcd1234！'],
  ])('PASSWORD_PATTERN_%s_doesNotMatch', (_, pw) => {
    expect(PASSWORD_PATTERN.test(pw)).toBe(false)
  })
})
