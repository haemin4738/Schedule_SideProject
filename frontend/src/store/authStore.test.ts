import { afterEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'

/** 다른 탭이 localStorage 를 바꾼 것처럼 storage 이벤트를 보낸다 (같은 탭의 setItem 은 이벤트를 만들지 않는다) */
const otherTabSets = (key: string | null, value: string | null) => {
  if (key === null) localStorage.clear()
  else if (value === null) localStorage.removeItem(key)
  else localStorage.setItem(key, value)
  window.dispatchEvent(new StorageEvent('storage', { key, newValue: value, storageArea: localStorage }))
}

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

  it('storage_otherTabLogsOut_clearsAccessTokenInThisTab', () => {
    useAuthStore.getState().login('acc', 'ref')

    otherTabSets('accessToken', null)

    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('storage_otherTabLogsIn_setsAccessTokenInThisTab', () => {
    otherTabSets('accessToken', 'other-acc')

    expect(useAuthStore.getState().accessToken).toBe('other-acc')
  })

  it('storage_otherTabRefreshes_usesNewAccessToken', () => {
    useAuthStore.getState().login('acc', 'ref')

    otherTabSets('accessToken', 'rotated-acc')

    expect(useAuthStore.getState().accessToken).toBe('rotated-acc')
  })

  it('storage_otherTabClearsStorage_clearsAccessToken', () => {
    useAuthStore.getState().login('acc', 'ref')

    otherTabSets(null, null)

    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('storage_unrelatedKeyChanged_keepsState', () => {
    useAuthStore.getState().login('acc', 'ref')
    localStorage.removeItem('accessToken') // 상태는 그대로 두고 저장소만 어긋나게 해서, 다른 키 이벤트로는 다시 읽지 않음을 확인

    otherTabSets('lifelog.calendar.layers.v1', '{}')

    expect(useAuthStore.getState().accessToken).toBe('acc')
  })

  it('storage_sessionStorageEvent_isIgnored', () => {
    useAuthStore.getState().login('acc', 'ref')
    localStorage.removeItem('accessToken')

    window.dispatchEvent(new StorageEvent('storage', { key: 'accessToken', storageArea: sessionStorage }))

    expect(useAuthStore.getState().accessToken).toBe('acc')
  })
})
