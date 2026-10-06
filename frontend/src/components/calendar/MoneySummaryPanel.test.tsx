import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import dayjs from 'dayjs'
import 'dayjs/locale/ko'
import { MemoryRouter } from 'react-router-dom'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import MoneySummaryPanel from './MoneySummaryPanel'
import type { CalendarMoneySummary } from './useCalendarMoneySummary'

const base: CalendarMoneySummary = {
  month: '2026-12',
  monthTotals: { income: 1200000, expense: 345000, net: 855000 },
  monthStatus: 'ready',
  day: '2026-12-31',
  dayTotals: { income: 0, expense: 12000, net: -12000 },
  dayStatus: 'ready',
}

const renderPanel = (over: Partial<CalendarMoneySummary> = {}, onNavigate?: () => void) =>
  render(
    <MemoryRouter>
      <MoneySummaryPanel summary={{ ...base, ...over }} onNavigate={onNavigate} />
    </MemoryRouter>,
  )

const panel = () => screen.getByRole('region', { name: '가계부 요약' })
const totals = (title: string) => within(panel()).getByRole('heading', { name: title }).parentElement!

describe('MoneySummaryPanel', () => {
  // 패널은 CalendarPage 가 설정한 전역 dayjs 로케일(ko)에 기대어 요일을 한글로 쓴다
  beforeAll(() => {
    dayjs.locale('ko')
  })
  afterAll(() => {
    dayjs.locale('en')
  })

  it('render_ready_showsRegionWithMonthAndDayHeadings', () => {
    renderPanel()

    expect(within(panel()).getByRole('heading', { name: '가계부 요약' })).toBeInTheDocument()
    expect(within(panel()).getByRole('heading', { name: '12월 전체' })).toBeInTheDocument()
    expect(within(panel()).getByRole('heading', { name: '12월 31일 (목)' })).toBeInTheDocument()
  })

  it('render_ready_showsIncomeExpenseNetWithTextLabels', () => {
    renderPanel()

    const month = totals('12월 전체')
    const labels = within(month).getAllByRole('term').map((el) => el.textContent)
    expect(labels).toEqual(['수입', '지출', '합계'])
    const values = within(month).getAllByRole('definition').map((el) => el.textContent)
    expect(values).toEqual(['1,200,000원', '345,000원', '855,000원'])
    expect(totals('12월 31일 (목)')).toHaveTextContent('수입0원지출12,000원합계-12,000원')
  })

  it('render_ready_colorsIncomeBlueExpenseRedAndNegativeNetRed', () => {
    renderPanel()

    const day = totals('12월 31일 (목)')
    const [income, expense, net] = within(day).getAllByRole('definition')
    expect(income).toHaveClass('text-blue-600')
    expect(expense).toHaveClass('text-red-500')
    expect(net).toHaveClass('text-red-500')
    // 0 이상 합계는 기본 글자색
    expect(within(totals('12월 전체')).getAllByRole('definition')[2]).toHaveClass('text-gray-800')
  })

  it('render_zeroNet_usesDefaultColor', () => {
    renderPanel({ dayTotals: { income: 0, expense: 0, net: 0 } })

    const net = within(totals('12월 31일 (목)')).getAllByRole('definition')[2]
    expect(net).toHaveTextContent('0원')
    expect(net).toHaveClass('text-gray-800')
  })

  it('render_loading_showsLoadingTextForBoth', () => {
    renderPanel({ monthTotals: null, monthStatus: 'loading', dayTotals: null, dayStatus: 'loading' })

    expect(within(panel()).getAllByText('불러오는 중…')).toHaveLength(2)
    expect(within(panel()).queryByText('수입')).not.toBeInTheDocument()
  })

  it('render_readyWithoutTotals_showsLoadingText', () => {
    renderPanel({ monthTotals: null, monthStatus: 'ready' })

    expect(within(totals('12월 전체')).getByText('불러오는 중…')).toBeInTheDocument()
  })

  it('render_monthErrorDayReady_showsErrorOnlyForMonth', () => {
    renderPanel({ monthTotals: null, monthStatus: 'error' })

    expect(within(totals('12월 전체')).getByText('불러오지 못했습니다.')).toBeInTheDocument()
    expect(totals('12월 31일 (목)')).toHaveTextContent('합계-12,000원')
    expect(within(panel()).getAllByText('불러오지 못했습니다.')).toHaveLength(1)
  })

  it('render_bothError_showsErrorTextForBoth', () => {
    renderPanel({ monthTotals: null, monthStatus: 'error', dayTotals: null, dayStatus: 'error' })

    expect(within(panel()).getAllByText('불러오지 못했습니다.')).toHaveLength(2)
  })

  it('render_link_pointsToExpensesOfViewedMonth', () => {
    renderPanel({ month: '2027-01', day: '2027-01-01' })

    expect(within(panel()).getByRole('link', { name: '가계부 보기' })).toHaveAttribute('href', '/expenses?month=2027-01')
    expect(within(panel()).getByRole('heading', { name: '1월 전체' })).toBeInTheDocument()
    expect(within(panel()).getByRole('heading', { name: '1월 1일 (금)' })).toBeInTheDocument()
  })

  it('onClickLink_callsOnNavigate', async () => {
    const user = userEvent.setup()
    const onNavigate = vi.fn()
    renderPanel({}, onNavigate)

    await user.click(within(panel()).getByRole('link', { name: '가계부 보기' }))

    expect(onNavigate).toHaveBeenCalledTimes(1)
  })
})
