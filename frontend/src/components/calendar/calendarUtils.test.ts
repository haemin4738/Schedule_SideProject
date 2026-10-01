import { describe, expect, it } from 'vitest'
import {
  JOB_APPLICATION_COLOR,
  dateKey,
  jobApplicationToCalendarEvent,
  toCalendarEvent,
  visibleRange,
  weekdayTextClass,
} from './calendarUtils'
import type { EventSummary } from '@/api/events'
import type { JobApplicationSummary } from '@/api/jobApplications'

const summary = (over: Partial<EventSummary> = {}): EventSummary => ({
  id: 1,
  title: '팀 회의',
  startAt: '2026-09-30T14:00:00',
  endAt: '2026-09-30T15:00:00',
  allDay: false,
  color: null,
  eventCategory: 'WORK',
  ...over,
})

describe('weekdayTextClass', () => {
  it('weekdayTextClass_sundaySaturdayWeekday_returnsRedBlueGray', () => {
    expect(weekdayTextClass(new Date(2026, 8, 27))).toBe('text-red-500')
    expect(weekdayTextClass(new Date(2026, 9, 3))).toBe('text-blue-500')
    expect(weekdayTextClass(new Date(2026, 8, 30))).toBe('text-gray-700')
  })

  it('weekdayTextClass_holidayOnWeekdayOrSaturday_returnsRed', () => {
    expect(weekdayTextClass(new Date(2026, 8, 25), true)).toBe('text-red-500')
    expect(weekdayTextClass(new Date(2026, 9, 3), true)).toBe('text-red-500')
  })

  it('weekdayTextClass_notHoliday_keepsWeekdayColor', () => {
    expect(weekdayTextClass(new Date(2026, 8, 25), false)).toBe('text-gray-700')
  })
})

describe('visibleRange', () => {
  it('visibleRange_month_includesLeadingAndTrailingWeeks', () => {
    const { from, to } = visibleRange(new Date(2026, 8, 15), 'month')
    expect(from).toEqual(new Date(2026, 7, 30, 0, 0, 0, 0))
    expect(to).toEqual(new Date(2026, 9, 3, 23, 59, 59, 999))
  })

  it('visibleRange_day_returnsWholeDay', () => {
    const { from, to } = visibleRange(new Date(2026, 8, 30, 15), 'day')
    expect(from).toEqual(new Date(2026, 8, 30, 0, 0, 0, 0))
    expect(to).toEqual(new Date(2026, 8, 30, 23, 59, 59, 999))
  })
})

describe('toCalendarEvent', () => {
  it('toCalendarEvent_timedWithoutEnd_assumesOneHour', () => {
    const e = toCalendarEvent(summary({ endAt: null }))
    expect(e.end).toEqual(new Date(2026, 8, 30, 15, 0))
  })

  it('toCalendarEvent_allDayWithoutEnd_endsSameDay', () => {
    const e = toCalendarEvent(summary({ allDay: true, startAt: '2026-09-30T00:00:00', endAt: null }))
    expect(e.end).toEqual(new Date(2026, 8, 30, 23, 59, 59, 999))
  })

  it('toCalendarEvent_customColor_usesIt', () => {
    expect(toCalendarEvent(summary({ color: '#0B8043' })).color).toBe('#0B8043')
  })

  it('toCalendarEvent_summary_marksKindEvent', () => {
    expect(toCalendarEvent(summary()).kind).toBe('event')
  })
})

describe('jobApplicationToCalendarEvent', () => {
  const application = (over: Partial<JobApplicationSummary> = {}): JobApplicationSummary => ({
    id: 7,
    companyName: '라이프로그',
    position: '백엔드 개발자',
    status: 'INTERVIEW_SCHEDULED',
    appliedAt: '2026-09-10',
    ...over,
  })

  it('jobApplicationToCalendarEvent_summary_returnsAllDayItemOnAppliedDate', () => {
    expect(jobApplicationToCalendarEvent(application())).toEqual({
      kind: 'jobApplication',
      id: 7,
      title: '라이프로그 · 백엔드 개발자 (면접예정)',
      start: new Date(2026, 8, 10, 0, 0, 0, 0),
      end: new Date(2026, 8, 10, 23, 59, 59, 999),
      allDay: true,
      color: JOB_APPLICATION_COLOR,
    })
  })

  it('jobApplicationToCalendarEvent_unknownStatus_fallsBackToRawStatus', () => {
    const e = jobApplicationToCalendarEvent(application({ status: 'NEW_STATUS' as never }))
    expect(e.title).toBe('라이프로그 · 백엔드 개발자 (NEW_STATUS)')
  })
})

describe('dateKey', () => {
  it('dateKey_date_formatsAsLocalYyyyMmDd', () => {
    expect(dateKey(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
  })
})
