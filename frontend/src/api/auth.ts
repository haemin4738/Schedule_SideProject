import client from './client'

export const login = (email: string, password: string) =>
  client.post<{ success: boolean; data: { accessToken: string; refreshToken: string } }>(
    '/api/v1/auth/login',
    { email, password },
  )

export interface SignupResponse {
  id: number
  email: string
  name: string
  createdAt: string
}

/** 가입만 하고 토큰은 주지 않는다 — 가입 후 login 을 따로 호출해야 한다 */
export const signup = (email: string, password: string, name: string) =>
  client.post<{ success: boolean; data: SignupResponse }>(
    '/api/v1/auth/signup',
    { email, password, name },
  )

/** 서버의 refresh 세션을 무효화한다. 인증 불필요, 무효·만료 토큰이어도 200 (멱등) */
export const logout = (refreshToken: string) => client.post('/api/v1/auth/logout', { refreshToken })

export type SocialProvider = 'kakao' | 'naver' | 'google'

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
}

export interface SocialLinkInfo {
  linkToken: string
  provider: 'KAKAO' | 'NAVER' | 'GOOGLE'
  maskedEmail: string
  expiresInSeconds: number
}

export type SocialLoginResponse =
  | { status: 'LOGGED_IN'; newUser: boolean; token: TokenResponse; link: null }
  | { status: 'LINK_REQUIRED'; newUser: boolean; token: null; link: SocialLinkInfo }

export interface SocialLoginRequest {
  grantType: 'AUTHORIZATION_CODE'
  code: string
  redirectUri: string
  codeVerifier?: string
  nonce?: string
  state?: string
}

export const socialLogin = (provider: SocialProvider, body: SocialLoginRequest) =>
  client.post<{ success: boolean; data: SocialLoginResponse }>(
    `/api/v1/auth/social/${provider}/login`,
    body,
  )

export const socialLink = (linkToken: string, password: string) =>
  client.post<{ success: boolean; data: TokenResponse }>('/api/v1/auth/social/link', {
    linkToken,
    password,
  })
