import { JOB_APPLICATION_STATUS_LABELS, type JobApplicationStatus } from '@/constants/jobApplicationStatus'

/** 표와 카드가 같은 모양의 상태 뱃지를 쓰도록 한 곳에 둔다 */
export default function JobApplicationStatusBadge({ status }: { status: JobApplicationStatus }) {
  return (
    <span className="inline-block shrink-0 rounded-full bg-blue-100 px-2 py-1 text-xs text-blue-700">
      {JOB_APPLICATION_STATUS_LABELS[status]}
    </span>
  )
}
