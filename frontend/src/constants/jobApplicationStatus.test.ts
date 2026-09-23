import { describe, expect, it } from 'vitest'
import {
  JOB_APPLICATION_STATUS_LABELS,
  JOB_APPLICATION_STATUS_OPTIONS,
  type JobApplicationStatus,
} from './jobApplicationStatus'

describe('JOB_APPLICATION_STATUS_LABELS', () => {
  it.each([
    ['APPLIED', '지원완료'],
    ['DOCUMENT_PASS', '서류합격'],
    ['DOCUMENT_FAIL', '서류불합격'],
    ['INTERVIEW_SCHEDULED', '면접예정'],
    ['INTERVIEW_PASS', '면접합격'],
    ['INTERVIEW_FAIL', '면접불합격'],
    ['OFFER', '최종합격(오퍼)'],
    ['ACCEPTED', '입사확정'],
    ['REJECTED', '불합격'],
    ['WITHDRAWN', '지원취소'],
  ] satisfies [JobApplicationStatus, string][])(
    '%s 상태는 "%s" 라벨을 갖는다',
    (status, label) => {
      expect(JOB_APPLICATION_STATUS_LABELS[status]).toBe(label)
    },
  )

  it('OPTIONS 목록은 LABELS 레코드와 동일한 10개 항목을 담는다', () => {
    expect(JOB_APPLICATION_STATUS_OPTIONS).toHaveLength(10)
    expect(Object.fromEntries(JOB_APPLICATION_STATUS_OPTIONS)).toEqual(
      JOB_APPLICATION_STATUS_LABELS,
    )
  })
})
