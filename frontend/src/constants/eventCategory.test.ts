import { describe, expect, it } from 'vitest'
import {
  CATEGORY_DEFAULT_COLORS,
  EVENT_CATEGORY_OPTIONS,
  readableTextColor,
  resolveEventColor,
} from './eventCategory'

describe('eventCategory', () => {
  it('EVENT_CATEGORY_OPTIONS_always_listsAllCategoriesInKorean', () => {
    expect(EVENT_CATEGORY_OPTIONS.map((o) => o.label)).toEqual(['개인', '업무', '알림', '기타'])
  })

  it('resolveEventColor_validHex_returnsIt', () => {
    expect(resolveEventColor('#D50000', 'WORK')).toBe('#D50000')
  })

  it('resolveEventColor_missingOrInvalidColor_fallsBackToCategory', () => {
    expect(resolveEventColor(null, 'WORK')).toBe(CATEGORY_DEFAULT_COLORS.WORK)
    expect(resolveEventColor('red; background:url(x)', 'REMINDER')).toBe(CATEGORY_DEFAULT_COLORS.REMINDER)
    expect(resolveEventColor('#FFF', 'OTHER')).toBe(CATEGORY_DEFAULT_COLORS.OTHER)
  })

  it('resolveEventColor_noColorNoCategory_returnsPersonalColor', () => {
    expect(resolveEventColor(undefined, null)).toBe(CATEGORY_DEFAULT_COLORS.PERSONAL)
  })

  it('readableTextColor_lightBackground_returnsDark', () => {
    expect(readableTextColor('#F6BF26')).toBe('#202124')
  })

  it('readableTextColor_darkBackground_returnsWhite', () => {
    expect(readableTextColor('#3F51B5')).toBe('#FFFFFF')
  })
})
