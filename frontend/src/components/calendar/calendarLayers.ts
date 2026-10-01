export type CalendarLayer = 'events' | 'jobApplications' | 'expenses' | 'specialDays'

export type CalendarLayers = Record<CalendarLayer, boolean>

export const CALENDAR_LAYER_OPTIONS: ReadonlyArray<readonly [CalendarLayer, string]> = [
  ['events', '일정'],
  ['jobApplications', '구직활동'],
  ['expenses', '가계부'],
  ['specialDays', '공휴일·기념일'],
]

export const LAYERS_STORAGE_KEY = 'lifelog.calendar.layers.v1'

export const DEFAULT_LAYERS: CalendarLayers = {
  events: true,
  jobApplications: true,
  expenses: true,
  specialDays: true,
}

/** 저장된 토글 상태. 저장소를 못 쓰거나 값이 깨졌으면 기본값(전부 켬)으로 본다 */
export const loadLayers = (): CalendarLayers => {
  try {
    const raw = localStorage.getItem(LAYERS_STORAGE_KEY)
    if (!raw) return DEFAULT_LAYERS
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return DEFAULT_LAYERS
    const saved = parsed as Partial<Record<CalendarLayer, unknown>>
    const layers = { ...DEFAULT_LAYERS }
    for (const [key] of CALENDAR_LAYER_OPTIONS) {
      if (typeof saved[key] === 'boolean') layers[key] = saved[key]
    }
    return layers
  } catch {
    return DEFAULT_LAYERS
  }
}

export const saveLayers = (layers: CalendarLayers): void => {
  try {
    localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify(layers))
  } catch {
    // 사생활 보호 모드 등 저장소를 못 쓰면 이번 화면에서만 유지한다
  }
}
