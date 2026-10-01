import { afterEach, describe, expect, it, vi } from 'vitest'
import client from './client'
import { getEventsInRange, toLocalDateTime } from './events'

const page = (data: unknown[], p: number, totalPages: number) => ({
  data: { success: true, data, meta: { page: p, size: 100, total: 0, totalPages } },
})

describe('events api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('toLocalDateTime_date_formatsWithoutTimezone', () => {
    expect(toLocalDateTime(new Date(2026, 8, 30, 7, 5, 9))).toBe('2026-09-30T07:05:09')
  })

  it('getEventsInRange_multiplePages_fetchesAllPagesWithRangeParams', async () => {
    const get = vi
      .spyOn(client, 'get')
      .mockResolvedValueOnce(page([{ id: 1 }], 0, 2))
      .mockResolvedValueOnce(page([{ id: 2 }], 1, 2))

    const events = await getEventsInRange(new Date(2026, 8, 1), new Date(2026, 8, 30, 23, 59, 59))

    expect(events.map((e) => e.id)).toEqual([1, 2])
    expect(get).toHaveBeenCalledTimes(2)
    expect(get).toHaveBeenNthCalledWith(1, '/api/v1/events', {
      params: { from: '2026-09-01T00:00:00', to: '2026-09-30T23:59:59', size: 100, page: 0 },
    })
    expect(get.mock.calls[1][1]).toMatchObject({ params: { page: 1 } })
  })

  it('getEventsInRange_emptyResult_stopsAfterFirstPage', async () => {
    const get = vi.spyOn(client, 'get').mockResolvedValue(page([], 0, 0))

    expect(await getEventsInRange(new Date(), new Date())).toEqual([])
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('getEventsInRange_serverKeepsReportingMorePages_stopsAtPageLimit', async () => {
    const get = vi.spyOn(client, 'get').mockImplementation(async () => page([{ id: 1 }], 0, 999))

    const onTruncated = vi.fn()
    await getEventsInRange(new Date(), new Date(), onTruncated)

    expect(get).toHaveBeenCalledTimes(20)
    expect(onTruncated).toHaveBeenCalledTimes(1)
  })

  it('getEventsInRange_exactlyAtPageLimit_doesNotReportTruncated', async () => {
    vi.spyOn(client, 'get').mockImplementation(async (_url, config) =>
      page([{ id: 1 }], (config?.params as { page: number } | undefined)?.page ?? 0, 20),
    )
    const onTruncated = vi.fn()

    await getEventsInRange(new Date(), new Date(), onTruncated)

    expect(onTruncated).not.toHaveBeenCalled()
  })
})
