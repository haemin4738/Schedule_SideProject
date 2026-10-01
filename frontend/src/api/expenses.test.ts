import { afterEach, describe, expect, it, vi } from 'vitest'
import client from './client'
import { getDailySummary } from './expenses'

describe('expenses api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('getDailySummary_range_requestsDailySummaryEndpoint', async () => {
    const summary = {
      from: '2026-09-01',
      to: '2026-09-30',
      totalIncome: 0,
      totalExpense: 12000,
      net: -12000,
      days: [{ date: '2026-09-05', income: 0, expense: 12000, net: -12000 }],
    }
    const get = vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: summary } })

    const { data } = await getDailySummary({ from: '2026-09-01', to: '2026-09-30' })

    expect(get).toHaveBeenCalledWith('/api/v1/expenses/summary/daily', {
      params: { from: '2026-09-01', to: '2026-09-30' },
    })
    expect(data.data.days).toEqual(summary.days)
  })
})
