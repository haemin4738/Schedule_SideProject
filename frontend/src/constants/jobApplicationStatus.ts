// 백엔드 com.lifelog.domain.jobapplication.JobApplicationStatus 와 반드시 동기화되어야 함.
// docs/api-spec.yaml (OpenAPI 스펙)에는 job-applications 도메인이 아직 반영되어 있지 않음.
export type JobApplicationStatus =
  | 'APPLIED'
  | 'DOCUMENT_PASS'
  | 'DOCUMENT_FAIL'
  | 'INTERVIEW_SCHEDULED'
  | 'INTERVIEW_PASS'
  | 'INTERVIEW_FAIL'
  | 'OFFER'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'WITHDRAWN'

export const JOB_APPLICATION_STATUS_LABELS: Record<JobApplicationStatus, string> = {
  APPLIED: '지원완료',
  DOCUMENT_PASS: '서류합격',
  DOCUMENT_FAIL: '서류불합격',
  INTERVIEW_SCHEDULED: '면접예정',
  INTERVIEW_PASS: '면접합격',
  INTERVIEW_FAIL: '면접불합격',
  OFFER: '최종합격(오퍼)',
  ACCEPTED: '입사확정',
  REJECTED: '불합격',
  WITHDRAWN: '지원취소',
}

export const JOB_APPLICATION_STATUS_OPTIONS = Object.entries(JOB_APPLICATION_STATUS_LABELS) as [
  JobApplicationStatus,
  string,
][]
