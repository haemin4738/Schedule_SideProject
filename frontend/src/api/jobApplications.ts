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
  page?: number
  size?: number
}) =>
  client.get<{ success: boolean; data: JobApplicationSummary[]; meta: PageMeta }>(
    '/api/v1/job-applications',
    { params },
  )

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
