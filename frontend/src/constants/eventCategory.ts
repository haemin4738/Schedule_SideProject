import type { EventCategory } from '@/api/events'

export const EVENT_CATEGORY_LABELS: Record<EventCategory, string> = {
  PERSONAL: '개인',
  WORK: '업무',
  REMINDER: '알림',
  OTHER: '기타',
}

export const EVENT_CATEGORY_OPTIONS = (Object.keys(EVENT_CATEGORY_LABELS) as EventCategory[]).map((value) => ({
  value,
  label: EVENT_CATEGORY_LABELS[value],
}))

/** 일정 색상 팔레트 (구글 캘린더 계열) */
export const EVENT_COLORS = [
  { value: '#D50000', label: '토마토' },
  { value: '#E67C73', label: '플라밍고' },
  { value: '#F4511E', label: '귤' },
  { value: '#F6BF26', label: '바나나' },
  { value: '#33B679', label: '세이지' },
  { value: '#0B8043', label: '바질' },
  { value: '#039BE5', label: '공작' },
  { value: '#3F51B5', label: '블루베리' },
  { value: '#7986CB', label: '라벤더' },
  { value: '#8E24AA', label: '포도' },
  { value: '#616161', label: '흑연' },
] as const

/** 색상을 따로 고르지 않은 일정은 카테고리 기본 색으로 표시한다 */
export const CATEGORY_DEFAULT_COLORS: Record<EventCategory, string> = {
  PERSONAL: '#039BE5',
  WORK: '#3F51B5',
  REMINDER: '#F6BF26',
  OTHER: '#616161',
}

const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/

/** 일정 표시 색: 직접 고른 색(형식이 올바를 때) → 카테고리 기본 색 → 개인 기본 색 */
export const resolveEventColor = (color: string | null | undefined, category: EventCategory | null | undefined): string =>
  color && HEX_COLOR.test(color) ? color : CATEGORY_DEFAULT_COLORS[category ?? 'PERSONAL']

/** 배경색 위 글자색: 밝은 색(바나나 등)에는 검정, 그 외에는 흰색 (YIQ 밝기 기준) */
export const readableTextColor = (hex: string): string => {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return (r * 299 + g * 587 + b * 114) / 1000 >= 160 ? '#202124' : '#FFFFFF'
}
