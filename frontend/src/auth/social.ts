import type { SocialProvider } from '@/api/auth'

/**
 * 웹 소셜 로그인(authorization code) 준비/검증 유틸.
 * state·code_verifier·nonce 는 탭 단위인 sessionStorage 에 제공자별 키로 저장하고, 콜백에서 한 번 꺼내면 지운다.
 */

interface ProviderConfig {
  label: string
  authorizeUrl: string
  /** 테스트에서 vi.stubEnv 로 바꿀 수 있도록 호출 시점에 읽는다 */
  clientId: () => string | undefined
  /** PKCE(S256) 사용 여부 — 네이버 /oauth2.0 엔드포인트는 미지원이라 보내지 않는다 (OIDC /oauth2 엔드포인트만 지원) */
  pkce: boolean
  /** OIDC nonce 사용 여부 (구글) */
  nonce: boolean
  scope?: string
}

const PROVIDERS: Record<SocialProvider, ProviderConfig> = {
  kakao: {
    label: '카카오',
    authorizeUrl: 'https://kauth.kakao.com/oauth/authorize',
    clientId: () => import.meta.env.VITE_KAKAO_CLIENT_ID,
    pkce: true,
    nonce: false,
  },
  naver: {
    label: '네이버',
    authorizeUrl: 'https://nid.naver.com/oauth2.0/authorize',
    clientId: () => import.meta.env.VITE_NAVER_CLIENT_ID,
    pkce: false,
    nonce: false,
  },
  google: {
    label: '구글',
    authorizeUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
    clientId: () => import.meta.env.VITE_GOOGLE_CLIENT_ID,
    pkce: true,
    nonce: true,
    scope: 'openid email profile',
  },
}

export const SOCIAL_PROVIDERS: SocialProvider[] = ['kakao', 'naver', 'google']

export const isSocialProvider = (value: unknown): value is SocialProvider =>
  typeof value === 'string' && (SOCIAL_PROVIDERS as string[]).includes(value)

export const getProviderLabel = (provider: SocialProvider): string => PROVIDERS[provider].label

/** client_id 환경변수가 설정되어 있는지 — 없으면 로그인 버튼을 비활성화한다 */
const getClientId = (provider: SocialProvider): string => {
  const value = PROVIDERS[provider].clientId()
  return typeof value === 'string' ? value.trim() : ''
}

export const isProviderConfigured = (provider: SocialProvider): boolean => getClientId(provider).length > 0

export const getRedirectUri = (provider: SocialProvider): string =>
  `${window.location.origin}/oauth/callback/${provider}`

const storageKey = (provider: SocialProvider, name: 'state' | 'codeVerifier' | 'nonce') =>
  `lifelog.oauth.${provider}.${name}`

const base64url = (bytes: Uint8Array): string => {
  let binary = ''
  bytes.forEach((b) => {
    binary += String.fromCharCode(b)
  })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** 암호학적 난수 byteLength 바이트를 base64url 로 인코딩한다 (32바이트 → 43자, PKCE verifier 길이 조건 충족) */
export const randomBase64url = (byteLength = 32): string =>
  base64url(crypto.getRandomValues(new Uint8Array(byteLength)))

/** PKCE S256 code_challenge = BASE64URL(SHA256(ASCII(code_verifier))) */
export const createCodeChallenge = async (codeVerifier: string): Promise<string> => {
  // crypto.subtle 은 secure context(HTTPS, localhost) 에서만 존재한다 — LAN IP 의 http 개발 서버 등
  if (!globalThis.crypto?.subtle) {
    throw new Error('보안 연결(HTTPS 또는 localhost)에서만 소셜 로그인을 사용할 수 있습니다.')
  }
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier))
  return base64url(new Uint8Array(digest))
}

/**
 * 제공자 authorize URL 을 만들고, 콜백에서 쓸 state·code_verifier·nonce 를 sessionStorage 에 저장한다.
 * client_id 가 없으면 예외를 던진다 (조용히 실패하지 않는다).
 */
export const buildAuthorizeUrl = async (provider: SocialProvider): Promise<string> => {
  const config = PROVIDERS[provider]
  if (!isProviderConfigured(provider)) {
    throw new Error(`${config.label} 로그인 설정(client_id)이 없습니다.`)
  }

  const state = randomBase64url()
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: getClientId(provider),
    redirect_uri: getRedirectUri(provider),
    state,
  })
  if (config.scope) params.set('scope', config.scope)

  clearSocialSession(provider)
  sessionStorage.setItem(storageKey(provider, 'state'), state)

  if (config.pkce) {
    const codeVerifier = randomBase64url()
    params.set('code_challenge', await createCodeChallenge(codeVerifier))
    params.set('code_challenge_method', 'S256')
    sessionStorage.setItem(storageKey(provider, 'codeVerifier'), codeVerifier)
  }
  if (config.nonce) {
    const nonce = randomBase64url()
    params.set('nonce', nonce)
    sessionStorage.setItem(storageKey(provider, 'nonce'), nonce)
  }

  return `${config.authorizeUrl}?${params.toString()}`
}

/**
 * 콜백으로 돌아온 state 를 저장값과 대조한다. 결과와 무관하게 저장된 state 는 즉시 삭제한다.
 * 불일치/없음이면 verifier·nonce 도 함께 지운다.
 */
export const verifyAndConsumeState = (provider: SocialProvider, returnedState: string | null): boolean => {
  const key = storageKey(provider, 'state')
  const saved = sessionStorage.getItem(key)
  sessionStorage.removeItem(key)
  const ok = !!saved && !!returnedState && saved === returnedState
  if (!ok) clearSocialSession(provider)
  return ok
}

/** API 호출 직전에 code_verifier·nonce 를 꺼내고 저장소에서 삭제한다 */
export const takeCodeVerifierAndNonce = (
  provider: SocialProvider,
): { codeVerifier?: string; nonce?: string } => {
  const take = (name: 'codeVerifier' | 'nonce') => {
    const key = storageKey(provider, name)
    const value = sessionStorage.getItem(key) ?? undefined
    sessionStorage.removeItem(key)
    return value
  }
  return { codeVerifier: take('codeVerifier'), nonce: take('nonce') }
}

export const clearSocialSession = (provider: SocialProvider): void => {
  sessionStorage.removeItem(storageKey(provider, 'state'))
  sessionStorage.removeItem(storageKey(provider, 'codeVerifier'))
  sessionStorage.removeItem(storageKey(provider, 'nonce'))
}
