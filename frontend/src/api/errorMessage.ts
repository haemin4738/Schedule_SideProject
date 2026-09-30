/**
 * 백엔드 오류 envelope({ success: false, data: null, error: '메시지' })에서 메시지를 꺼낸다.
 * 메시지가 없으면(네트워크 오류 등) fallback 을 돌려준다.
 */
/** 백엔드 오류 envelope 의 기계 판독용 code (선택 필드). 없으면 undefined */
export const getApiErrorCode = (err: unknown): string | undefined => {
  const code = (err as { response?: { data?: { code?: unknown } } } | null)?.response?.data?.code
  return typeof code === 'string' ? code : undefined
}

export const getApiErrorMessage = (err: unknown, fallback: string): string => {
  const message = (err as { response?: { data?: { error?: unknown } } } | null)?.response?.data?.error
  return typeof message === 'string' && message.length > 0 ? message : fallback
}
