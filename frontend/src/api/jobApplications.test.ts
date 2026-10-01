import { afterEach, describe, expect, it, vi } from 'vitest'
import client from './client'
import { getJobApplications, getJobApplicationsInRange } from './jobApplications'

const page = (data: unknown[], p: number, totalPages: number) => ({
  data: { success: true, data, meta: { page: p, size: 100, total: 0, totalPages } },
})

describe('jobApplications api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('getJobApplications_withRange_passesFromToParams', async () => {
    const get = vi.spyOn(client, 'get').mockResolvedValue(page([], 0, 0))

    await getJobApplications({ from: '2026-09-01', to: '2026-09-30', page: 0, size: 20 })

    expect(get).toHaveBeenCalledWith('/api/v1/job-applications', {
      params: { from: '2026-09-01', to: '2026-09-30', page: 0, size: 20 },
    })
  })

  it('getJobApplicationsInRange_multiplePages_fetchesAllPagesWithRangeParams', async () => {
    const get = vi
      .spyOn(client, 'get')
      .mockResolvedValueOnce(page([{ id: 1 }], 0, 2))
      .mockResolvedValueOnce(page([{ id: 2 }], 1, 2))

    const items = await getJobApplicationsInRange('2026-08-30', '2026-10-03')

    expect(items.map((a) => a.id)).toEqual([1, 2])
    expect(get).toHaveBeenCalledTimes(2)
    expect(get).toHaveBeenNthCalledWith(1, '/api/v1/job-applications', {
      params: { from: '2026-08-30', to: '2026-10-03', page: 0, size: 100 },
    })
    expect(get.mock.calls[1][1]).toMatchObject({ params: { page: 1, size: 100 } })
  })

  it('getJobApplicationsInRange_emptyResult_stopsAfterFirstPage', async () => {
    const get = vi.spyOn(client, 'get').mockResolvedValue(page([], 0, 0))

    expect(await getJobApplicationsInRange('2026-09-01', '2026-09-30')).toEqual([])
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('getJobApplicationsInRange_serverKeepsReportingMorePages_stopsAtPageLimit', async () => {
    const get = vi.spyOn(client, 'get').mockImplementation(async () => page([{ id: 1 }], 0, 999))

    const items = await getJobApplicationsInRange('2026-09-01', '2026-09-30')

    expect(get).toHaveBeenCalledTimes(20)
    expect(items).toHaveLength(20)
  })

  it('getJobApplicationsInRange_requestFails_rejects', async () => {
    vi.spyOn(client, 'get').mockResolvedValueOnce(page([{ id: 1 }], 0, 2)).mockRejectedValueOnce(new Error('boom'))

    await expect(getJobApplicationsInRange('2026-09-01', '2026-09-30')).rejects.toThrow('boom')
  })
})
