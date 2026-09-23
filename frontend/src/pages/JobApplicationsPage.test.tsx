import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import JobApplicationsPage from './JobApplicationsPage'
import {
  createJobApplication,
  getJobApplication,
  getJobApplications,
  updateJobApplication,
} from '@/api/jobApplications'

vi.mock('@/api/jobApplications', () => ({
  getJobApplications: vi.fn(),
  getJobApplication: vi.fn(),
  createJobApplication: vi.fn(),
  updateJobApplication: vi.fn(),
  deleteJobApplication: vi.fn(),
}))

const mockedGetJobApplications = vi.mocked(getJobApplications)
const mockedGetJobApplication = vi.mocked(getJobApplication)
const mockedCreateJobApplication = vi.mocked(createJobApplication)
const mockedUpdateJobApplication = vi.mocked(updateJobApplication)

const sampleItem = {
  id: 1,
  companyName: '테스트회사',
  position: '백엔드 개발자',
  status: 'APPLIED' as const,
  appliedAt: '2026-09-01',
}

function renderPage() {
  return render(
    <MemoryRouter>
      <JobApplicationsPage />
    </MemoryRouter>,
  )
}

function mockListResponse(items: typeof sampleItem[], meta = { page: 0, size: 20, total: items.length, totalPages: 1 }) {
  mockedGetJobApplications.mockResolvedValue({
    data: { success: true, data: items, meta },
  } as never)
}

describe('JobApplicationsPage', () => {
  beforeEach(() => {
    mockedGetJobApplications.mockReset()
    mockedGetJobApplication.mockReset()
    mockedCreateJobApplication.mockReset()
    mockedUpdateJobApplication.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('목록 로딩 후 데이터를 렌더링한다', async () => {
    mockListResponse([sampleItem])
    renderPage()

    expect(await screen.findByText('테스트회사')).toBeInTheDocument()
    const row = screen.getByText('테스트회사').closest('tr') as HTMLElement
    expect(within(row).getByText('백엔드 개발자')).toBeInTheDocument()
    expect(within(row).getByText('지원완료')).toBeInTheDocument()
  })

  it('로딩 중에는 로딩 문구를 보여주고 빈 상태 문구는 보여주지 않는다', () => {
    mockedGetJobApplications.mockReturnValue(new Promise(() => {}) as never)
    renderPage()

    expect(screen.getByText('불러오는 중...')).toBeInTheDocument()
    expect(screen.queryByText('지원 내역이 없습니다.')).not.toBeInTheDocument()
  })

  it('로딩 후 데이터가 없으면 빈 상태 문구를 보여준다', async () => {
    mockListResponse([])
    renderPage()

    expect(await screen.findByText('지원 내역이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText('불러오는 중...')).not.toBeInTheDocument()
  })

  it('목록 조회 실패 시 에러 메시지를 렌더링한다', async () => {
    mockedGetJobApplications.mockRejectedValue(new Error('network error'))
    renderPage()

    expect(
      await screen.findByText('구직활동 목록을 불러오지 못했습니다.'),
    ).toBeInTheDocument()
  })

  it('생성 폼 제출 시 createJobApplication을 올바른 값으로 호출한다', async () => {
    const user = userEvent.setup()
    mockListResponse([])
    mockedCreateJobApplication.mockResolvedValue({
      data: { success: true, data: { ...sampleItem, createdAt: '', updatedAt: '' } },
    } as never)

    const { container } = renderPage()
    await screen.findByText('지원 내역이 없습니다.')

    await user.type(screen.getByPlaceholderText('회사명'), '새회사')
    await user.type(screen.getByPlaceholderText('지원 직무'), '프론트엔드 개발자')
    const dateInput = container.querySelector('input[type="date"]') as HTMLInputElement
    await user.type(dateInput, '2026-09-20')

    await user.click(screen.getByRole('button', { name: '추가' }))

    await waitFor(() => {
      expect(mockedCreateJobApplication).toHaveBeenCalledWith({
        companyName: '새회사',
        position: '프론트엔드 개발자',
        status: 'APPLIED',
        appliedAt: '2026-09-20',
        jobPostingUrl: undefined,
        memo: undefined,
      })
    })
  })

  it('수정 클릭 시 상세 조회로 jobPostingUrl/memo를 포함해 폼을 채우고, 저장 시 기존 값을 보존해서 전송한다', async () => {
    const user = userEvent.setup()
    mockListResponse([sampleItem])
    mockedGetJobApplication.mockResolvedValue({
      data: {
        success: true,
        data: {
          ...sampleItem,
          jobPostingUrl: 'https://example.com/posting',
          memo: '기존 메모',
          createdAt: '2026-09-01T00:00:00Z',
          updatedAt: '2026-09-01T00:00:00Z',
        },
      },
    } as never)
    mockedUpdateJobApplication.mockResolvedValue({
      data: { success: true, data: { ...sampleItem, createdAt: '', updatedAt: '' } },
    } as never)

    renderPage()
    await screen.findByText('테스트회사')

    await user.click(screen.getByRole('button', { name: '수정' }))

    expect(mockedGetJobApplication).toHaveBeenCalledWith(1)
    await waitFor(() => {
      expect(screen.getByPlaceholderText('채용공고 URL')).toHaveValue(
        'https://example.com/posting',
      )
    })
    expect(screen.getByPlaceholderText('메모')).toHaveValue('기존 메모')

    await user.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => {
      expect(mockedUpdateJobApplication).toHaveBeenCalledWith(1, {
        companyName: '테스트회사',
        position: '백엔드 개발자',
        status: 'APPLIED',
        appliedAt: '2026-09-01',
        jobPostingUrl: 'https://example.com/posting',
        memo: '기존 메모',
      })
    })
  })

  it('페이지네이션 다음 버튼 클릭 시 다음 페이지를 조회한다', async () => {
    const user = userEvent.setup()
    mockListResponse([sampleItem], { page: 0, size: 20, total: 40, totalPages: 2 })
    renderPage()

    await screen.findByText('테스트회사')
    expect(screen.getByText('1 / 2')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '다음' }))

    await waitFor(() => {
      expect(mockedGetJobApplications).toHaveBeenLastCalledWith({
        status: undefined,
        page: 1,
        size: 20,
      })
    })
  })
})
