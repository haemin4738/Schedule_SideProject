import client from './client'
import type { JobApplicationStatus } from '@/constants/jobApplicationStatus'

export interface JobApplicationSummary {
  id: number
  companyName: string
  position: string
  status: JobApplicationStatus
  appliedAt: string
}

export interface JobApplicationRequest {
  companyName: string
  position: string
  status: JobApplicationStatus
  appliedAt: string
  jobPostingUrl?: string
  memo?: string
}

export interface JobApplicationResponse {
  id: number
  companyName: string
  position: string
  status: JobApplicationStatus
  appliedAt: string
  jobPostingUrl?: string
  memo?: string
  createdAt: string
  updatedAt: string
}

export interface PageMeta {
  page: number
  size: number
  total: number
  totalPages: number
}

export const getJobApplications = (params?: {
  status?: JobApplicationStatus
  /** yyyy-MM-dd, 지원일 기준 양끝 포함 */
  from?: string
  to?: string
  page?: number
  size?: number
}) =>
  client.get<{ success: boolean; data: JobApplicationSummary[]; meta: PageMeta }>(
    '/api/v1/job-applications',
    { params },
  )

// 백엔드 목록 size 상한
const RANGE_PAGE_SIZE = 100
// 비정상 응답으로 무한 반복하지 않도록 페이지 수 상한을 둔다
const RANGE_MAX_PAGES = 20

/** 지원일이 [from, to] (yyyy-MM-dd, 양끝 포함) 인 지원 내역을 모든 페이지에 걸쳐 가져온다 */
export const getJobApplicationsInRange = async (from: string, to: string): Promise<JobApplicationSummary[]> => {
  const items: JobApplicationSummary[] = []
  for (let page = 0; page < RANGE_MAX_PAGES; page++) {
    const { data } = await getJobApplications({ from, to, page, size: RANGE_PAGE_SIZE })
    items.push(...data.data)
    if (page + 1 >= data.meta.totalPages) break
  }
  return items
}

export const getJobApplication = (id: number) =>
  client.get<{ success: boolean; data: JobApplicationResponse }>(`/api/v1/job-applications/${id}`)

export const createJobApplication = (body: JobApplicationRequest) =>
  client.post<{ success: boolean; data: JobApplicationResponse }>('/api/v1/job-applications', body)

export const updateJobApplication = (id: number, body: JobApplicationRequest) =>
  client.put<{ success: boolean; data: JobApplicationResponse }>(
    `/api/v1/job-applications/${id}`,
    body,
  )

export const deleteJobApplication = (id: number) => client.delete(`/api/v1/job-applications/${id}`)
