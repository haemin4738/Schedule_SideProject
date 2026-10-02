import { createJobApplication, type JobApplicationRequest } from '@/api/jobApplications'
import { getApiErrorMessage } from '@/api/errorMessage'
import { useDialog } from '@/components/expenses/useDialog'
import { JOB_APPLICATION_STATUS_OPTIONS, type JobApplicationStatus } from '@/constants/jobApplicationStatus'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

interface FormValues {
  companyName: string
  position: string
  status: JobApplicationStatus
  appliedAt: string
  jobPostingUrl: string
  memo: string
}

interface Props {
  /** 지원일 기본값 (yyyy-MM-dd) — 캘린더에서 고른 날짜 */
  defaultAppliedAt: string
  /** 제목 위에 붙일 내용 (캘린더의 입력 종류 탭) */
  header?: ReactNode
  onClose: () => void
  onSaved: () => void
}

const inputClass = 'w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400'

/**
 * 캘린더에서 구직활동을 바로 추가하는 모달. 검증 규칙은 구직활동 화면의 폼과 같다
 * (백엔드 JobApplicationRequest — 회사명·직무 200, URL 500, 메모 10,000).
 * 수정·삭제는 구직활동 화면에서 한다.
 */
export default function JobApplicationFormModal({ defaultAppliedAt, header, onClose, onSaved }: Props) {
  const [formError, setFormError] = useState<string | null>(null)
  const dialogRef = useDialog<HTMLFormElement>(onClose)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      companyName: '',
      position: '',
      status: 'APPLIED',
      appliedAt: defaultAppliedAt,
      jobPostingUrl: '',
      memo: '',
    },
  })

  const onSubmit = async (values: FormValues) => {
    setFormError(null)
    const body: JobApplicationRequest = {
      companyName: values.companyName,
      position: values.position,
      status: values.status,
      appliedAt: values.appliedAt,
      jobPostingUrl: values.jobPostingUrl || undefined,
      memo: values.memo || undefined,
    }
    try {
      await createJobApplication(body)
      onSaved()
    } catch (err) {
      setFormError(getApiErrorMessage(err, '저장에 실패했습니다. 입력값을 확인해주세요.'))
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <form
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        aria-label="구직활동 추가"
        onSubmit={handleSubmit(onSubmit)}
        noValidate
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-xl bg-white p-6 shadow-xl outline-none"
      >
        {header}
        <h2 className="mb-4 text-lg font-semibold">구직활동 추가</h2>

        <div className="flex flex-col gap-3">
          <div>
            <input
              {...register('companyName', {
                validate: (v) => v.trim().length > 0 || '회사명은 필수입니다.',
              })}
              maxLength={200}
              aria-label="회사명"
              placeholder="회사명"
              aria-invalid={errors.companyName ? true : undefined}
              className={inputClass}
            />
            {errors.companyName && <p className="mt-1 text-xs text-red-500">{errors.companyName.message}</p>}
          </div>
          <div>
            <input
              {...register('position', {
                validate: (v) => v.trim().length > 0 || '지원 직무는 필수입니다.',
              })}
              maxLength={200}
              aria-label="지원 직무"
              placeholder="지원 직무"
              aria-invalid={errors.position ? true : undefined}
              className={inputClass}
            />
            {errors.position && <p className="mt-1 text-xs text-red-500">{errors.position.message}</p>}
          </div>
          <select {...register('status')} aria-label="상태" className={inputClass}>
            {JOB_APPLICATION_STATUS_OPTIONS.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <div>
            <input
              {...register('appliedAt', { required: '지원일은 필수입니다.' })}
              type="date"
              aria-label="지원일"
              className={inputClass}
            />
            {errors.appliedAt && <p className="mt-1 text-xs text-red-500">{errors.appliedAt.message}</p>}
          </div>
          <input
            {...register('jobPostingUrl')}
            maxLength={500}
            aria-label="채용공고 URL"
            placeholder="채용공고 URL"
            className={inputClass}
          />
          <textarea
            {...register('memo')}
            maxLength={10_000}
            aria-label="메모"
            placeholder="메모"
            rows={3}
            className={inputClass}
          />
        </div>

        {formError && (
          <p role="alert" className="mt-3 text-sm text-red-500">
            {formError}
          </p>
        )}

        <div className="mt-4 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded border px-4 py-2 text-sm hover:bg-gray-50">
            취소
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded bg-blue-500 px-4 py-2 text-sm text-white hover:bg-blue-600 disabled:opacity-50"
          >
            저장
          </button>
        </div>
      </form>
    </div>
  )
}
