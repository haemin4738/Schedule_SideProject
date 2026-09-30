import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { SocialProvider } from '@/api/auth'
import {
  SOCIAL_PROVIDERS,
  buildAuthorizeUrl,
  clearSocialSession,
  createCodeChallenge,
  getProviderLabel,
  getRedirectUri,
  isProviderConfigured,
  isSocialProvider,
  randomBase64url,
  takeCodeVerifierAndNonce,
  verifyAndConsumeState,
} from './social'

const key = (provider: SocialProvider, name: string) => `lifelog.oauth.${provider}.${name}`

const parse = (url: string) => {
  const u = new URL(url)
  return { base: `${u.origin}${u.pathname}`, params: u.searchParams }
}

const stubAllClientIds = () => {
  vi.stubEnv('VITE_KAKAO_CLIENT_ID', 'kakao-client')
  vi.stubEnv('VITE_NAVER_CLIENT_ID', 'naver-client')
  vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'google-client')
}

describe('social 유틸', () => {
  beforeEach(() => {
    sessionStorage.clear()
    stubAllClientIds()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    sessionStorage.clear()
    localStorage.clear()
  })

  describe('createCodeChallenge', () => {
    it('createCodeChallenge_rfc7636AppendixBVector_returnsExpectedChallenge', async () => {
      await expect(createCodeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).resolves.toBe(
        'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
      )
    })

    it('createCodeChallenge_whenNotSecureContext_throwsFriendlyError', async () => {
      // http LAN IP 개발 서버처럼 secure context 가 아니면 crypto.subtle 이 없다
      vi.stubGlobal('crypto', { getRandomValues: crypto.getRandomValues.bind(crypto) })
      try {
        await expect(createCodeChallenge('verifier')).rejects.toThrow(
          '보안 연결(HTTPS 또는 localhost)에서만 소셜 로그인을 사용할 수 있습니다.',
        )
      } finally {
        vi.unstubAllGlobals()
      }
    })
  })

  describe('randomBase64url', () => {
    it('randomBase64url_default_returns43UrlSafeCharsAndDiffersEachCall', () => {
      const a = randomBase64url()
      const b = randomBase64url()
      expect(a).toMatch(/^[A-Za-z0-9_-]{43}$/)
      expect(b).toMatch(/^[A-Za-z0-9_-]{43}$/)
      expect(a).not.toBe(b)
    })
  })

  describe('isSocialProvider / getProviderLabel / getRedirectUri', () => {
    it('isSocialProvider_knownAndUnknownValues_returnsExpected', () => {
      expect(SOCIAL_PROVIDERS).toEqual(['kakao', 'naver', 'google'])
      SOCIAL_PROVIDERS.forEach((p) => expect(isSocialProvider(p)).toBe(true))
      expect(isSocialProvider('apple')).toBe(false)
      expect(isSocialProvider('KAKAO')).toBe(false)
      expect(isSocialProvider(undefined)).toBe(false)
      expect(isSocialProvider(1)).toBe(false)
    })

    it('getProviderLabel_eachProvider_returnsKoreanLabel', () => {
      expect(getProviderLabel('kakao')).toBe('카카오')
      expect(getProviderLabel('naver')).toBe('네이버')
      expect(getProviderLabel('google')).toBe('구글')
    })

    it('getRedirectUri_provider_returnsOriginCallbackPath', () => {
      expect(getRedirectUri('naver')).toBe(`${window.location.origin}/oauth/callback/naver`)
    })
  })

  describe('isProviderConfigured', () => {
    it('isProviderConfigured_clientIdPresent_returnsTrue', () => {
      SOCIAL_PROVIDERS.forEach((p) => expect(isProviderConfigured(p)).toBe(true))
    })

    it('isProviderConfigured_clientIdEmptyOrBlank_returnsFalse', () => {
      vi.stubEnv('VITE_KAKAO_CLIENT_ID', '')
      vi.stubEnv('VITE_GOOGLE_CLIENT_ID', '   ')
      expect(isProviderConfigured('kakao')).toBe(false)
      expect(isProviderConfigured('google')).toBe(false)
      expect(isProviderConfigured('naver')).toBe(true)
    })
  })

  describe('buildAuthorizeUrl', () => {
    it('buildAuthorizeUrl_kakao_includesPkceS256AndStateWithoutNonceOrScope', async () => {
      const { base, params } = parse(await buildAuthorizeUrl('kakao'))

      expect(base).toBe('https://kauth.kakao.com/oauth/authorize')
      expect(params.get('response_type')).toBe('code')
      expect(params.get('client_id')).toBe('kakao-client')
      expect(params.get('redirect_uri')).toBe(`${window.location.origin}/oauth/callback/kakao`)
      expect(params.get('state')).toBe(sessionStorage.getItem(key('kakao', 'state')))
      expect(params.get('code_challenge_method')).toBe('S256')
      const verifier = sessionStorage.getItem(key('kakao', 'codeVerifier'))
      expect(verifier).toMatch(/^[A-Za-z0-9_-]{43}$/)
      expect(params.get('code_challenge')).toBe(await createCodeChallenge(verifier!))
      expect(params.has('nonce')).toBe(false)
      expect(params.has('scope')).toBe(false)
      expect(sessionStorage.getItem(key('kakao', 'nonce'))).toBeNull()
    })

    it('buildAuthorizeUrl_naver_includesStateWithoutPkceOrNonce', async () => {
      const { base, params } = parse(await buildAuthorizeUrl('naver'))

      expect(base).toBe('https://nid.naver.com/oauth2.0/authorize')
      expect(params.get('response_type')).toBe('code')
      expect(params.get('client_id')).toBe('naver-client')
      expect(params.get('redirect_uri')).toBe(`${window.location.origin}/oauth/callback/naver`)
      expect(params.get('state')).toBeTruthy()
      expect(params.get('state')).toBe(sessionStorage.getItem(key('naver', 'state')))
      expect(params.has('code_challenge')).toBe(false)
      expect(params.has('code_challenge_method')).toBe(false)
      expect(params.has('nonce')).toBe(false)
      expect(sessionStorage.getItem(key('naver', 'codeVerifier'))).toBeNull()
      expect(sessionStorage.getItem(key('naver', 'nonce'))).toBeNull()
    })

    it('buildAuthorizeUrl_google_includesPkceNonceAndOpenIdScope', async () => {
      const { base, params } = parse(await buildAuthorizeUrl('google'))

      expect(base).toBe('https://accounts.google.com/o/oauth2/v2/auth')
      expect(params.get('response_type')).toBe('code')
      expect(params.get('client_id')).toBe('google-client')
      expect(params.get('redirect_uri')).toBe(`${window.location.origin}/oauth/callback/google`)
      expect(params.get('scope')).toBe('openid email profile')
      expect(params.get('state')).toBe(sessionStorage.getItem(key('google', 'state')))
      expect(params.get('nonce')).toBe(sessionStorage.getItem(key('google', 'nonce')))
      expect(params.get('nonce')).toBeTruthy()
      expect(params.get('code_challenge_method')).toBe('S256')
      const verifier = sessionStorage.getItem(key('google', 'codeVerifier'))
      expect(params.get('code_challenge')).toBe(await createCodeChallenge(verifier!))
    })

    it('buildAuthorizeUrl_calledTwice_generatesDifferentRandomValues', async () => {
      const first = parse(await buildAuthorizeUrl('google')).params
      const firstVerifier = sessionStorage.getItem(key('google', 'codeVerifier'))
      const second = parse(await buildAuthorizeUrl('google')).params
      const secondVerifier = sessionStorage.getItem(key('google', 'codeVerifier'))

      expect(first.get('state')).not.toBe(second.get('state'))
      expect(first.get('nonce')).not.toBe(second.get('nonce'))
      expect(first.get('code_challenge')).not.toBe(second.get('code_challenge'))
      expect(firstVerifier).not.toBe(secondVerifier)
      // 두 번째 호출 값으로 덮어쓴다
      expect(sessionStorage.getItem(key('google', 'state'))).toBe(second.get('state'))
    })

    it('buildAuthorizeUrl_naverAfterStaleVerifier_clearsPreviousSessionValues', async () => {
      sessionStorage.setItem(key('naver', 'codeVerifier'), 'stale')
      sessionStorage.setItem(key('naver', 'nonce'), 'stale')

      await buildAuthorizeUrl('naver')

      expect(sessionStorage.getItem(key('naver', 'codeVerifier'))).toBeNull()
      expect(sessionStorage.getItem(key('naver', 'nonce'))).toBeNull()
    })

    it('buildAuthorizeUrl_clientIdMissing_throwsAndStoresNothing', async () => {
      vi.stubEnv('VITE_KAKAO_CLIENT_ID', '')

      await expect(buildAuthorizeUrl('kakao')).rejects.toThrow('카카오 로그인 설정(client_id)이 없습니다.')
      expect(sessionStorage.length).toBe(0)
    })

    it('buildAuthorizeUrl_clientIdWithWhitespace_usesTrimmedValue', async () => {
      vi.stubEnv('VITE_NAVER_CLIENT_ID', '  naver-client  ')
      expect(parse(await buildAuthorizeUrl('naver')).params.get('client_id')).toBe('naver-client')
    })

    it('buildAuthorizeUrl_anyProvider_storesNothingInLocalStorage', async () => {
      for (const p of SOCIAL_PROVIDERS) await buildAuthorizeUrl(p)
      expect(localStorage.length).toBe(0)
    })
  })

  describe('verifyAndConsumeState', () => {
    const seed = (provider: SocialProvider) => {
      sessionStorage.setItem(key(provider, 'state'), 'saved-state')
      sessionStorage.setItem(key(provider, 'codeVerifier'), 'verifier')
      sessionStorage.setItem(key(provider, 'nonce'), 'nonce')
    }

    it('verifyAndConsumeState_matchingState_returnsTrueAndRemovesOnlyState', () => {
      seed('google')

      expect(verifyAndConsumeState('google', 'saved-state')).toBe(true)
      expect(sessionStorage.getItem(key('google', 'state'))).toBeNull()
      expect(sessionStorage.getItem(key('google', 'codeVerifier'))).toBe('verifier')
      expect(sessionStorage.getItem(key('google', 'nonce'))).toBe('nonce')
    })

    it('verifyAndConsumeState_mismatchedState_returnsFalseAndClearsAll', () => {
      seed('google')

      expect(verifyAndConsumeState('google', 'other-state')).toBe(false)
      expect(sessionStorage.length).toBe(0)
    })

    it('verifyAndConsumeState_returnedStateNull_returnsFalseAndClearsAll', () => {
      seed('kakao')

      expect(verifyAndConsumeState('kakao', null)).toBe(false)
      expect(sessionStorage.length).toBe(0)
    })

    it('verifyAndConsumeState_noSavedState_returnsFalseAndClearsAll', () => {
      sessionStorage.setItem(key('kakao', 'codeVerifier'), 'verifier')

      expect(verifyAndConsumeState('kakao', 'any')).toBe(false)
      expect(sessionStorage.length).toBe(0)
    })

    it('verifyAndConsumeState_emptyStrings_returnsFalse', () => {
      sessionStorage.setItem(key('naver', 'state'), '')
      expect(verifyAndConsumeState('naver', '')).toBe(false)
    })

    it('verifyAndConsumeState_otherProviderState_isNotAffected', () => {
      seed('kakao')
      seed('google')

      expect(verifyAndConsumeState('google', 'wrong')).toBe(false)
      expect(sessionStorage.getItem(key('kakao', 'state'))).toBe('saved-state')
    })

    it('verifyAndConsumeState_calledTwiceWithSameState_secondCallFails', () => {
      seed('naver')
      expect(verifyAndConsumeState('naver', 'saved-state')).toBe(true)
      expect(verifyAndConsumeState('naver', 'saved-state')).toBe(false)
    })
  })

  describe('takeCodeVerifierAndNonce', () => {
    it('takeCodeVerifierAndNonce_valuesStored_returnsAndRemovesThem', () => {
      sessionStorage.setItem(key('google', 'codeVerifier'), 'v')
      sessionStorage.setItem(key('google', 'nonce'), 'n')

      expect(takeCodeVerifierAndNonce('google')).toEqual({ codeVerifier: 'v', nonce: 'n' })
      expect(sessionStorage.length).toBe(0)
      expect(takeCodeVerifierAndNonce('google')).toEqual({ codeVerifier: undefined, nonce: undefined })
    })

    it('takeCodeVerifierAndNonce_naverWithoutValues_returnsUndefined', () => {
      expect(takeCodeVerifierAndNonce('naver')).toEqual({ codeVerifier: undefined, nonce: undefined })
    })
  })

  describe('clearSocialSession', () => {
    it('clearSocialSession_provider_removesOnlyThatProvidersKeys', () => {
      for (const p of ['kakao', 'google'] as const) {
        sessionStorage.setItem(key(p, 'state'), 's')
        sessionStorage.setItem(key(p, 'codeVerifier'), 'v')
        sessionStorage.setItem(key(p, 'nonce'), 'n')
      }
      sessionStorage.setItem('unrelated', 'keep')

      clearSocialSession('google')

      expect(sessionStorage.getItem(key('google', 'state'))).toBeNull()
      expect(sessionStorage.getItem(key('google', 'codeVerifier'))).toBeNull()
      expect(sessionStorage.getItem(key('google', 'nonce'))).toBeNull()
      expect(sessionStorage.getItem(key('kakao', 'state'))).toBe('s')
      expect(sessionStorage.getItem('unrelated')).toBe('keep')
    })
  })
})
