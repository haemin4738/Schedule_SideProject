import {
  createJobApplication,
  deleteJobApplication,
  getJobApplication,
  getJobApplications,
  updateJobApplication,
  type JobApplicationRequest,
  type JobApplicationSummary,
  type PageMeta,
} from '@/api/jobApplications'
import {
  JOB_APPLICATION_STATUS_OPTIONS,
  JOB_APPLICATION_STATUS_LABELS,
  type JobApplicationStatus,
} from '@/constants/jobApplicationStatus'
import { Link } from 'react-router-dom'
import { useCallback, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

const PAGE_SIZE = 20

interface FormValues {
  companyName: string
  position: string
  status: JobApplicationStatus
  appliedAt: string
  jobPostingUrl: string
  memo: string
}

const emptyForm: FormValues = {
  companyName: '',
  position: '',
  status: 'APPLIED',
  appliedAt: '',
  jobPostingUrl: '',
  memo: '',
}

export default function JobApplicationsPage() {
  const [items, setItems] = useState<JobApplicationSummary[]>([])
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<JobApplicationStatus | ''>('')
  const [listError, setListError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ defaultValues: emptyForm })

  const loadItems = useCallback(() => {
    setListError(null)
    setIsLoading(true)
    getJobApplications({
      status: statusFilter || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then(({ data }) => {
        setItems(data.data)
        setMeta(data.meta)
      })
      .catch(() => {
        setListError('구직활동 목록을 불러오지 못했습니다.')
      })
      .finally(() => {
        setIsLoading(false)
      })
  }, [page, statusFilter])

  useEffect(() => {
    loadItems()
  }, [loadItems])

  const startEdit = async (item: JobApplicationSummary) => {
    setFormError(null)
    try {
      const { data } = await getJobApplication(item.id)
      setEditingId(item.id)
      reset({
        companyName: data.data.companyName,
        position: data.data.position,
        status: data.data.status,
        appliedAt: data.data.appliedAt,
        jobPostingUrl: data.data.jobPostingUrl ?? '',
        memo: data.data.memo ?? '',
      })
    } catch {
      setFormError('상세 정보를 불러오지 못했습니다.')
    }
  }

  const cancelEdit = () => {
    setEditingId(null)
    setFormError(null)
    reset(emptyForm)
  }

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
      if (editingId) {
        await updateJobApplication(editingId, body)
      } else {
        await createJobApplication(body)
      }
      setEditingId(null)
      reset(emptyForm)
      loadItems()
    } catch {
      setFormError('저장에 실패했습니다. 입력값을 확인해주세요.')
    }
  }

  const onDelete = async (id: number) => {
    if (!window.confirm('삭제하시겠습니까?')) return
    setListError(null)
    try {
      await deleteJobApplication(id)
      loadItems()
    } catch {
      setListError('삭제에 실패했습니다.')
    }
  }

  return (
    <div className="mx-auto max-w-4xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">구직활동 관리</h1>
        <Link to="/" className="text-sm text-blue-500 hover:underline">
          캘린더로 이동
        </Link>
      </div>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="mb-6 rounded-xl bg-white p-6 shadow"
      >
        <h2 className="mb-4 text-lg font-medium">{editingId ? '지원 내역 수정' : '지원 내역 추가'}</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <input
              {...register('companyName', { required: '회사명은 필수입니다.', maxLength: 200 })}
              placeholder="회사명"
              className="w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
            />
            {errors.companyName && (
              <p className="mt-1 text-xs text-red-500">{errors.companyName.message}</p>
            )}
          </div>
          <div>
            <input
              {...register('position', { required: '지원 직무는 필수입니다.', maxLength: 200 })}
              placeholder="지원 직무"
              className="w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
            />
            {errors.position && <p className="mt-1 text-xs text-red-500">{errors.position.message}</p>}
          </div>
          <div>
            <select
              {...register('status', { required: true })}
              className="w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
            >
              {JOB_APPLICATION_STATUS_OPTIONS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <input
              {...register('appliedAt', { required: '지원일은 필수입니다.' })}
              type="date"
              className="w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
            />
            {errors.appliedAt && <p className="mt-1 text-xs text-red-500">{errors.appliedAt.message}</p>}
          </div>
          <div>
            <input
              {...register('jobPostingUrl', { maxLength: 500 })}
              placeholder="채용공고 URL"
              className="w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
            />
          </div>
          <div>
            <input
              {...register('memo')}
              placeholder="메모"
              className="w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
            />
          </div>
        </div>

        {formError && <p className="mt-3 text-sm text-red-500">{formError}</p>}

        <div className="mt-4 flex gap-2">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded bg-blue-500 px-4 py-2 text-sm text-white hover:bg-blue-600 disabled:opacity-50"
          >
            {editingId ? '수정 저장' : '추가'}
          </button>
          {editingId && (
            <button
              type="button"
              onClick={cancelEdit}
              className="rounded border px-4 py-2 text-sm hover:bg-gray-50"
            >
              취소
            </button>
          )}
        </div>
      </form>

      <div className="mb-3 flex items-center gap-2">
        <label className="text-sm text-gray-600">상태 필터</label>
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value as JobApplicationStatus | '')
            setPage(0)
          }}
          className="rounded border px-2 py-1 text-sm"
        >
          <option value="">전체</option>
          {JOB_APPLICATION_STATUS_OPTIONS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      {listError && <p className="mb-3 text-sm text-red-500">{listError}</p>}

      <div className="overflow-hidden rounded-xl bg-white shadow">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-left text-gray-600">
            <tr>
              <th className="px-4 py-2">회사명</th>
              <th className="px-4 py-2">직무</th>
              <th className="px-4 py-2">상태</th>
              <th className="px-4 py-2">지원일</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id} className="border-t">
                <td className="px-4 py-2">{item.companyName}</td>
                <td className="px-4 py-2">{item.position}</td>
                <td className="px-4 py-2">
                  <span className="rounded-full bg-blue-100 px-2 py-1 text-xs text-blue-700">
                    {JOB_APPLICATION_STATUS_LABELS[item.status]}
                  </span>
                </td>
                <td className="px-4 py-2">{item.appliedAt}</td>
                <td className="px-4 py-2 text-right">
                  <button
                    onClick={() => startEdit(item)}
                    className="mr-2 text-blue-500 hover:underline"
                  >
                    수정
                  </button>
                  <button
                    onClick={() => onDelete(item.id)}
                    className="text-red-500 hover:underline"
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
            {isLoading && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                  불러오는 중...
                </td>
              </tr>
            )}
            {!isLoading && items.length === 0 && !listError && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                  지원 내역이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {meta && meta.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-sm text-gray-600">
            {page + 1} / {meta.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(meta.totalPages - 1, p + 1))}
            disabled={page >= meta.totalPages - 1}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </div>
  )
}
