import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import JobApplicationsPage from './JobApplicationsPage'
import {
  createJobApplication,
  deleteJobApplication,
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

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

function renderPage(initialEntry = '/job-applications') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <JobApplicationsPage />
      <LocationProbe />
    </MemoryRouter>,
  )
}

const detailResponse = (over: Record<string, unknown> = {}) =>
  ({
    data: {
      success: true,
      data: {
        ...sampleItem,
        jobPostingUrl: 'https://example.com/posting',
        memo: '캘린더에서 열림',
        createdAt: '',
        updatedAt: '',
        ...over,
      },
    },
  }) as never

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
    // 표의 수정 버튼은 포커스를 옮기지 않는다
    expect(screen.getByRole('heading', { name: '지원 내역 수정' })).not.toHaveFocus()

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
  describe('?id 로 진입', () => {
    it('startEdit_previousResponseArrivesLate_keepsLatestForm', async () => {
      const user = userEvent.setup()
      mockListResponse([
        { ...sampleItem, id: 1, companyName: '먼저회사' },
        { ...sampleItem, id: 2, companyName: '나중회사' },
      ])
      let resolveFirst!: (v: unknown) => void
      mockedGetJobApplication
        .mockImplementationOnce(() => new Promise((r) => (resolveFirst = r)) as never)
        .mockResolvedValueOnce(detailResponse({ id: 2, companyName: '나중회사' }))
      renderPage()
      const editButtons = await screen.findAllByRole('button', { name: '수정' })

      await user.click(editButtons[0])
      await user.click(editButtons[1])
      await waitFor(() => expect(screen.getByPlaceholderText('회사명')).toHaveValue('나중회사'))
      resolveFirst(detailResponse({ id: 1, companyName: '먼저회사' }))

      // 늦게 온 첫 응답이 마지막으로 누른 내역의 폼을 덮어쓰지 않는다
      await new Promise((r) => setTimeout(r, 0))
      expect(screen.getByPlaceholderText('회사명')).toHaveValue('나중회사')
    })

    it('cancelEdit_whileDetailLoading_ignoresResponse', async () => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      let resolveDetail!: (v: unknown) => void
      mockedGetJobApplication
        .mockResolvedValueOnce(detailResponse({ id: 1, companyName: '열린회사' }))
        .mockImplementationOnce(() => new Promise((r) => (resolveDetail = r)) as never)
      renderPage()
      await user.click(await screen.findByRole('button', { name: '수정' }))
      await waitFor(() => expect(screen.getByPlaceholderText('회사명')).toHaveValue('열린회사'))

      // 다시 수정을 눌러 조회가 진행 중일 때 취소하면, 늦게 온 응답으로 폼이 다시 열리지 않는다
      await user.click(screen.getAllByRole('button', { name: '수정' })[0])
      await user.click(screen.getByRole('button', { name: '취소' }))
      resolveDetail(detailResponse({ id: 1, companyName: '늦은회사' }))

      await new Promise((r) => setTimeout(r, 0))
      expect(screen.getByPlaceholderText('회사명')).toHaveValue('')
      expect(screen.getByRole('heading', { name: '지원 내역 추가' })).toBeInTheDocument()
    })

    it('render_withIdParam_loadsDetailFillsEditFormAndClearsParam', async () => {
      mockListResponse([])
      mockedGetJobApplication.mockResolvedValue(detailResponse({ id: 7, companyName: '캘린더회사' }))
      renderPage('/job-applications?id=7')

      await waitFor(() => expect(screen.getByPlaceholderText('회사명')).toHaveValue('캘린더회사'))
      expect(mockedGetJobApplication).toHaveBeenCalledTimes(1)
      expect(mockedGetJobApplication).toHaveBeenCalledWith(7)
      expect(screen.getByPlaceholderText('메모')).toHaveValue('캘린더에서 열림')
      expect(screen.getByRole('button', { name: '수정 저장' })).toBeInTheDocument()
      expect(screen.getByTestId('location')).toHaveTextContent(/^\/job-applications$/)
      // 입력칸이 아니라 폼 제목에 포커스한다 (폰에서 키보드가 바로 올라오지 않게)
      await waitFor(() => expect(screen.getByRole('heading', { name: '지원 내역 수정' })).toHaveFocus())
      expect(screen.getByPlaceholderText('회사명')).not.toHaveFocus()
    })

    it('render_withIdParam_scrollsFormIntoView', async () => {
      mockListResponse([])
      mockedGetJobApplication.mockResolvedValue(detailResponse({ id: 7 }))
      const scrollIntoView = vi.fn()
      Element.prototype.scrollIntoView = scrollIntoView
      try {
        renderPage('/job-applications?id=7')

        await waitFor(() => expect(screen.getByRole('heading', { name: '지원 내역 수정' })).toHaveFocus())
        expect(scrollIntoView).toHaveBeenCalledWith({ block: 'start' })
        expect(scrollIntoView.mock.contexts[0]).toBe(document.querySelector('form'))
      } finally {
        delete (Element.prototype as Partial<Element>).scrollIntoView
      }
    })

    it('render_withIdParam_savesToThatId', async () => {
      const user = userEvent.setup()
      mockListResponse([])
      mockedGetJobApplication.mockResolvedValue(detailResponse({ id: 7 }))
      mockedUpdateJobApplication.mockResolvedValue({
        data: { success: true, data: { ...sampleItem, id: 7, createdAt: '', updatedAt: '' } },
      } as never)
      renderPage('/job-applications?id=7')
      await waitFor(() => expect(screen.getByPlaceholderText('회사명')).toHaveValue('테스트회사'))

      await user.click(screen.getByRole('button', { name: '수정 저장' }))

      await waitFor(() => expect(mockedUpdateJobApplication).toHaveBeenCalledWith(7, expect.any(Object)))
    })

    it.each(['abc', '0', '-3', '1.5', '99999999999999999999'])(
      'render_withInvalidIdParam_%s_ignoresAndClearsParam',
      async (param) => {
        mockListResponse([])
        renderPage(`/job-applications?id=${param}`)

        await screen.findByText('지원 내역이 없습니다.')
        expect(mockedGetJobApplication).not.toHaveBeenCalled()
        expect(screen.getByTestId('location')).toHaveTextContent(/^\/job-applications$/)
        expect(screen.getByRole('button', { name: '추가' })).toBeInTheDocument()
      },
    )

    it('render_withIdParamDetailFails_showsFormError', async () => {
      mockListResponse([])
      mockedGetJobApplication.mockRejectedValue(new Error('404'))
      renderPage('/job-applications?id=7')

      expect(await screen.findByText('상세 정보를 불러오지 못했습니다.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '추가' })).toBeInTheDocument()
    })
  })

  it('render_memoInput_hasBackendLengthLimit', async () => {
    mockListResponse([])
    renderPage()

    expect(await screen.findByPlaceholderText('메모')).toHaveAttribute('maxLength', '10000')
  })

  describe('작은 화면(카드 목록)', () => {
    const mockedDeleteJobApplication = vi.mocked(deleteJobApplication)
    // 데스크톱 쿼리(min-width: 768px) 일치 여부 — change 로 화면 폭 전환을 흉내 낸다
    let desktopMatches: boolean
    let changeListeners: Set<() => void>

    const setViewport = (desktop: boolean) => {
      desktopMatches = desktop
      act(() => changeListeners.forEach((cb) => cb()))
    }

    beforeEach(() => {
      desktopMatches = false
      changeListeners = new Set()
      mockedDeleteJobApplication.mockReset()
      vi.stubGlobal(
        'matchMedia',
        vi.fn((query: string) => ({
          get matches() {
            return desktopMatches
          },
          media: query,
          addEventListener: (_type: string, cb: () => void) => changeListeners.add(cb),
          removeEventListener: (_type: string, cb: () => void) => changeListeners.delete(cb),
        })),
      )
    })

    afterEach(() => {
      vi.unstubAllGlobals()
    })

    const findCard = (companyName = '테스트회사') =>
      screen.findByRole('button', { name: new RegExp(`^${companyName}.*지원일`) })

    it('render_onMobile_showsCardsInsteadOfTable', async () => {
      mockListResponse([sampleItem])
      renderPage()

      const list = await screen.findByRole('list', { name: '지원 내역 목록' })
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      const card = within(list).getByRole('listitem')
      expect(within(card).getByText('테스트회사')).toBeInTheDocument()
      expect(within(card).getByText('백엔드 개발자')).toBeInTheDocument()
      expect(within(card).getByText('지원완료')).toBeInTheDocument()
      expect(within(card).getByText('지원일 2026-09-01')).toBeInTheDocument()
      expect(within(card).queryByRole('button', { name: '수정' })).not.toBeInTheDocument()
    })

    it('resize_mobileToDesktopAndBack_swapsCardsAndTable', async () => {
      mockListResponse([sampleItem])
      renderPage()
      await screen.findByRole('list', { name: '지원 내역 목록' })

      setViewport(true)
      expect(screen.getByRole('table')).toBeInTheDocument()
      expect(screen.queryByRole('list', { name: '지원 내역 목록' })).not.toBeInTheDocument()
      expect(within(screen.getByRole('table')).getByText('테스트회사')).toBeInTheDocument()

      setViewport(false)
      expect(screen.getByRole('list', { name: '지원 내역 목록' })).toBeInTheDocument()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
      // 폭 전환은 목록을 다시 조회하지 않는다
      expect(mockedGetJobApplications).toHaveBeenCalledTimes(1)
    })

    it('resize_whileEditing_keepsEditForm', async () => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      mockedGetJobApplication.mockResolvedValue(detailResponse({ memo: '기존 메모' }))
      renderPage()
      await user.click(await findCard())
      await waitFor(() => expect(screen.getByPlaceholderText('회사명')).toHaveValue('테스트회사'))

      setViewport(true)

      expect(screen.getByPlaceholderText('메모')).toHaveValue('기존 메모')
      expect(screen.getByRole('button', { name: '수정 저장' })).toBeInTheDocument()
    })

    it('clickCard_sameCardWhileEditing_keepsInputWithoutRefetch', async () => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      mockedGetJobApplication.mockResolvedValue(detailResponse())
      renderPage()
      await user.click(await findCard())
      const company = screen.getByPlaceholderText('회사명')
      await waitFor(() => expect(company).toHaveValue('테스트회사'))
      await user.clear(company)
      await user.type(company, '바꾼회사')

      await user.click(await findCard())

      expect(company).toHaveValue('바꾼회사')
      expect(mockedGetJobApplication).toHaveBeenCalledTimes(1)
      expect(screen.getByRole('heading', { name: '지원 내역 수정' })).toHaveFocus()
    })

    it('clickCard_otherCardWithUnsavedInput_asksBeforeReplacing', async () => {
      const user = userEvent.setup()
      const other = { ...sampleItem, id: 2, companyName: '다른회사' }
      mockListResponse([sampleItem, other])
      mockedGetJobApplication.mockImplementation(async (id: number) =>
        detailResponse(id === 2 ? { id: 2, companyName: '다른회사' } : {}),
      )
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
      renderPage()
      await user.click(await findCard())
      const company = screen.getByPlaceholderText('회사명')
      await waitFor(() => expect(company).toHaveValue('테스트회사'))
      await user.type(company, ' 수정중')

      await user.click(await findCard('다른회사'))
      expect(confirmSpy).toHaveBeenCalledTimes(1)
      expect(company).toHaveValue('테스트회사 수정중')
      expect(mockedGetJobApplication).toHaveBeenCalledTimes(1)

      confirmSpy.mockReturnValue(true)
      await user.click(await findCard('다른회사'))
      await waitFor(() => expect(company).toHaveValue('다른회사'))
      expect(mockedGetJobApplication).toHaveBeenLastCalledWith(2)
    })

    it('clickCard_otherCardWithoutChanges_switchesWithoutConfirm', async () => {
      const user = userEvent.setup()
      const other = { ...sampleItem, id: 2, companyName: '다른회사' }
      mockListResponse([sampleItem, other])
      mockedGetJobApplication.mockImplementation(async (id: number) =>
        detailResponse(id === 2 ? { id: 2, companyName: '다른회사' } : {}),
      )
      const confirmSpy = vi.spyOn(window, 'confirm')
      renderPage()
      await user.click(await findCard())
      const company = screen.getByPlaceholderText('회사명')
      await waitFor(() => expect(company).toHaveValue('테스트회사'))

      await user.click(await findCard('다른회사'))

      await waitFor(() => expect(company).toHaveValue('다른회사'))
      expect(confirmSpy).not.toHaveBeenCalled()
    })

    it('clickCard_onMobile_fillsEditFormAndFocusesHeading', async () => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      mockedGetJobApplication.mockResolvedValue(detailResponse({ memo: '기존 메모' }))
      renderPage()

      await user.click(await findCard())

      expect(mockedGetJobApplication).toHaveBeenCalledWith(1)
      await waitFor(() => expect(screen.getByPlaceholderText('회사명')).toHaveValue('테스트회사'))
      expect(screen.getByPlaceholderText('채용공고 URL')).toHaveValue('https://example.com/posting')
      expect(screen.getByPlaceholderText('메모')).toHaveValue('기존 메모')
      expect(screen.getByRole('button', { name: '수정 저장' })).toBeInTheDocument()
      const heading = screen.getByRole('heading', { name: '지원 내역 수정' })
      await waitFor(() => expect(heading).toHaveFocus())
      expect(heading).toHaveAttribute('tabIndex', '-1')
      // 입력칸에 포커스하지 않아 폰 키보드가 올라오지 않는다
      expect(screen.getByPlaceholderText('회사명')).not.toHaveFocus()
      expect(await findCard()).toHaveAttribute('aria-current', 'true')
    })

    it('clickCard_detailFails_showsErrorAndKeepsFocusOnCard', async () => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      mockedGetJobApplication.mockRejectedValue(new Error('404'))
      renderPage()
      const card = await findCard()

      await user.click(card)

      expect(await screen.findByText('상세 정보를 불러오지 못했습니다.')).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: '지원 내역 추가' })).not.toHaveFocus()
      expect(card).toHaveFocus()
    })

    it.each(['{Enter}', ' '])('pressKeyOnCard_%s_onMobile_startsEdit', async (key) => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      mockedGetJobApplication.mockResolvedValue(detailResponse())
      renderPage()

      ;(await findCard()).focus()
      await user.keyboard(key)

      await waitFor(() => expect(mockedGetJobApplication).toHaveBeenCalledWith(1))
      await waitFor(() => expect(screen.getByRole('heading', { name: '지원 내역 수정' })).toHaveFocus())
    })

    it('render_withIdParamOnMobile_fillsFormAndFocusesHeading', async () => {
      mockListResponse([sampleItem])
      mockedGetJobApplication.mockResolvedValue(detailResponse({ id: 7, companyName: '캘린더회사' }))
      renderPage('/job-applications?id=7')

      await waitFor(() => expect(screen.getByPlaceholderText('회사명')).toHaveValue('캘린더회사'))
      expect(mockedGetJobApplication).toHaveBeenCalledWith(7)
      expect(screen.getByTestId('location')).toHaveTextContent(/^\/job-applications$/)
      await waitFor(() => expect(screen.getByRole('heading', { name: '지원 내역 수정' })).toHaveFocus())
      expect(await screen.findByRole('list', { name: '지원 내역 목록' })).toBeInTheDocument()
    })

    it('clickNextAndPrev_onMobile_loadsPagesAsCards', async () => {
      const user = userEvent.setup()
      mockedGetJobApplications
        .mockResolvedValueOnce({
          data: { success: true, data: [sampleItem], meta: { page: 0, size: 20, total: 21, totalPages: 2 } },
        } as never)
        .mockResolvedValueOnce({
          data: {
            success: true,
            data: [{ ...sampleItem, id: 21, companyName: '둘째페이지회사' }],
            meta: { page: 1, size: 20, total: 21, totalPages: 2 },
          },
        } as never)
        .mockResolvedValueOnce({
          data: { success: true, data: [sampleItem], meta: { page: 0, size: 20, total: 21, totalPages: 2 } },
        } as never)
      renderPage()
      await findCard()
      expect(screen.getByText('1 / 2')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '이전' })).toBeDisabled()

      await user.click(screen.getByRole('button', { name: '다음' }))

      expect(await findCard('둘째페이지회사')).toBeInTheDocument()
      expect(mockedGetJobApplications).toHaveBeenLastCalledWith({ status: undefined, page: 1, size: 20 })
      expect(screen.getByText('2 / 2')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: '이전' }))

      expect(await findCard()).toBeInTheDocument()
      expect(mockedGetJobApplications).toHaveBeenLastCalledWith({ status: undefined, page: 0, size: 20 })
    })

    it('render_onMobileSinglePage_hidesPagination', async () => {
      mockListResponse([sampleItem])
      renderPage()
      await findCard()

      expect(screen.queryByRole('button', { name: '다음' })).not.toBeInTheDocument()
    })

    it('clickDeleteOnCard_onMobile_confirmsDeletesAndReloadsList', async () => {
      const user = userEvent.setup()
      mockedGetJobApplications
        .mockResolvedValueOnce({
          data: {
            success: true,
            data: [sampleItem, { ...sampleItem, id: 2, companyName: '남는회사' }],
            meta: { page: 0, size: 20, total: 2, totalPages: 1 },
          },
        } as never)
        .mockResolvedValueOnce({
          data: {
            success: true,
            data: [{ ...sampleItem, id: 2, companyName: '남는회사' }],
            meta: { page: 0, size: 20, total: 1, totalPages: 1 },
          },
        } as never)
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
      mockedDeleteJobApplication.mockResolvedValue({} as never)
      renderPage()

      await user.click(await screen.findByRole('button', { name: '테스트회사 삭제' }))

      expect(confirm).toHaveBeenCalledWith('삭제하시겠습니까?')
      await waitFor(() => expect(mockedDeleteJobApplication).toHaveBeenCalledWith(1))
      await waitFor(() => expect(screen.queryByText('테스트회사')).not.toBeInTheDocument())
      expect(within(screen.getByRole('list', { name: '지원 내역 목록' })).getAllByRole('listitem')).toHaveLength(1)
      expect(screen.getByText('남는회사')).toBeInTheDocument()
      expect(mockedGetJobApplications).toHaveBeenCalledTimes(2)
      expect(mockedGetJobApplication).not.toHaveBeenCalled()
    })

    it('clickDeleteOnCard_whenConfirmCancelled_doesNotDelete', async () => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      vi.spyOn(window, 'confirm').mockReturnValue(false)
      renderPage()

      await user.click(await screen.findByRole('button', { name: '테스트회사 삭제' }))

      expect(mockedDeleteJobApplication).not.toHaveBeenCalled()
      expect(mockedGetJobApplications).toHaveBeenCalledTimes(1)
    })

    it('clickDeleteOnCard_whenDeleteFails_showsError', async () => {
      const user = userEvent.setup()
      mockListResponse([sampleItem])
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      mockedDeleteJobApplication.mockRejectedValue(new Error('500'))
      renderPage()

      await user.click(await screen.findByRole('button', { name: '테스트회사 삭제' }))

      expect(await screen.findByText('삭제에 실패했습니다.')).toBeInTheDocument()
      expect(await findCard()).toBeInTheDocument()
    })

    it('render_onMobileWhileLoading_showsLoadingMessage', () => {
      mockedGetJobApplications.mockReturnValue(new Promise(() => {}) as never)
      renderPage()

      expect(screen.getByText('불러오는 중...')).toBeInTheDocument()
      expect(screen.queryByText('지원 내역이 없습니다.')).not.toBeInTheDocument()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
    })

    it('render_onMobileEmptyList_showsEmptyMessage', async () => {
      mockListResponse([])
      renderPage()

      expect(await screen.findByText('지원 내역이 없습니다.')).toBeInTheDocument()
      expect(screen.queryByRole('list', { name: '지원 내역 목록' })).not.toBeInTheDocument()
    })

    it('render_onMobileListFails_showsErrorWithoutEmptyMessage', async () => {
      mockedGetJobApplications.mockRejectedValue(new Error('network error'))
      renderPage()

      expect(await screen.findByText('구직활동 목록을 불러오지 못했습니다.')).toBeInTheDocument()
      expect(screen.queryByText('지원 내역이 없습니다.')).not.toBeInTheDocument()
    })
  })
})
