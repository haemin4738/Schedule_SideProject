import client from './client'

export type SpecialDayKind = 'HOLIDAY' | 'ANNIVERSARY' | 'SOLAR_TERM'

/** 공휴일·기념일·24절기 (백엔드 SpecialDayResponse) */
export interface SpecialDay {
  /** yyyy-MM-dd */
  date: string
  name: string
  kind: SpecialDayKind
  /** 쉬는 날 여부 (공휴일·대체공휴일·임시공휴일) */
  holiday: boolean
}

/** [from, to] (yyyy-MM-dd, 양끝 포함, 최대 366일) 의 특일. 외부 API 장애 시에도 빈 배열로 응답된다 */
export const getSpecialDays = (from: string, to: string) =>
  client.get<{ success: boolean; data: SpecialDay[] }>('/api/v1/special-days', { params: { from, to } })
