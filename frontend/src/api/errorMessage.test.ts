import { describe, expect, it } from 'vitest'
import { getApiErrorMessage } from './errorMessage'

describe('getApiErrorMessage', () => {
  it('응답 envelope 의 error 메시지를 돌려준다', () => {
    const err = { response: { status: 409, data: { success: false, data: null, error: '중복입니다.' } } }
    expect(getApiErrorMessage(err, '기본')).toBe('중복입니다.')
  })

  it.each([
    ['응답이 없는 네트워크 오류', new Error('Network Error')],
    ['error 필드가 비어 있음', { response: { data: { error: '' } } }],
    ['error 필드가 문자열이 아님', { response: { data: { error: { code: 'X' } } } }],
    ['null', null],
  ])('%s 이면 fallback 을 돌려준다', (_, err) => {
    expect(getApiErrorMessage(err, '기본')).toBe('기본')
  })
})
