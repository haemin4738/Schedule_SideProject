import { afterEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore', () => {
  afterEach(() => {
    localStorage.clear()
    useAuthStore.setState({ accessToken: null, sessionNotice: null })
  })

  it('logout_withoutNotice_clearsTokensAndKeepsNoticeEmpty', () => {
    useAuthStore.getState().login('acc', 'ref')
    useAuthStore.getState().logout()

    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().sessionNotice).toBeNull()
  })

  it('logout_withNotice_clearsTokensAndStoresNotice', () => {
    useAuthStore.getState().login('acc', 'ref')
    useAuthStore.getState().logout('보안 안내')

    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().sessionNotice).toBe('보안 안내')
  })

  it('logout_withoutNoticeAfterNotice_keepsExistingNotice', () => {
    useAuthStore.getState().logout('보안 안내')
    // 같은 순간 다른 요청의 일반 로그아웃이 팝업 안내를 지우지 않는다
    useAuthStore.getState().logout()

    expect(useAuthStore.getState().sessionNotice).toBe('보안 안내')
  })

  it('clearSessionNotice_afterNotice_removesNotice', () => {
    useAuthStore.getState().logout('보안 안내')
    useAuthStore.getState().clearSessionNotice()

    expect(useAuthStore.getState().sessionNotice).toBeNull()
  })
})
