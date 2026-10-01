import { afterEach, describe, expect, it, vi } from 'vitest'
import client from './client'
import { getSpecialDays } from './specialDays'

describe('specialDays api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('getSpecialDays_range_requestsWithFromToParams', async () => {
    const days = [{ date: '2026-10-03', name: '개천절', kind: 'HOLIDAY', holiday: true }]
    const get = vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: days } })

    const { data } = await getSpecialDays('2026-09-27', '2026-10-31')

    expect(get).toHaveBeenCalledWith('/api/v1/special-days', { params: { from: '2026-09-27', to: '2026-10-31' } })
    expect(data.data).toEqual(days)
  })
})
