import client from './client'
import type { ExpenseType } from '@/constants/expenseType'
import type { PaymentMethod } from '@/constants/paymentMethod'
import type { PageMeta } from './jobApplications'

export type { PageMeta }

export interface ExpenseCategory {
  id: number
  type: ExpenseType
  name: string
  createdAt: string
  updatedAt: string
}

export interface ExpenseSummary {
  id: number
  type: ExpenseType
  categoryId: number
  categoryName: string
  amount: number
  /** 지출일 때만 값이 있을 수 있다 (수입은 항상 null) */
  paymentMethod: PaymentMethod | null
  transactionDate: string
  description: string | null
}

export interface ExpenseResponse extends ExpenseSummary {
  memo: string | null
  createdAt: string
  updatedAt: string
}

export interface ExpenseRequest {
  type: ExpenseType
  categoryId: number
  amount: number
  /** INCOME 이면 반드시 null (아니면 400). PUT 은 전체 교체라 생략하면 null 로 저장된다 */
  paymentMethod: PaymentMethod | null
  transactionDate: string
  description: string | null
  memo: string | null
}

export interface MonthlyItem {
  yearMonth: string
  income: number
  expense: number
  net: number
}

export interface MonthlySummaryResponse {
  from: string
  to: string
  totalIncome: number
  totalExpense: number
  net: number
  months: MonthlyItem[]
}

export interface CategoryItem {
  categoryId: number
  categoryName: string
  amount: number
  count: number
}

export interface CategorySummaryResponse {
  type: ExpenseType
  from: string
  to: string
  total: number
  categories: CategoryItem[]
}

export interface DailyItem {
  /** yyyy-MM-dd */
  date: string
  income: number
  expense: number
  net: number
}

/** 일별 요약 — days 는 내역이 있는 날짜만 포함(sparse), 날짜 오름차순 */
export interface DailySummaryResponse {
  from: string
  to: string
  totalIncome: number
  totalExpense: number
  net: number
  days: DailyItem[]
}

// ── 내역 ──────────────────────────────────────────────

export const getExpenses = (params: {
  from: string
  to: string
  type?: ExpenseType
  categoryId?: number
  page?: number
  size?: number
}) =>
  client.get<{ success: boolean; data: ExpenseSummary[]; meta: PageMeta }>('/api/v1/expenses', {
    params,
  })

export const getExpense = (id: number) =>
  client.get<{ success: boolean; data: ExpenseResponse }>(`/api/v1/expenses/${id}`)

export const createExpense = (body: ExpenseRequest) =>
  client.post<{ success: boolean; data: ExpenseResponse }>('/api/v1/expenses', body)

export const updateExpense = (id: number, body: ExpenseRequest) =>
  client.put<{ success: boolean; data: ExpenseResponse }>(`/api/v1/expenses/${id}`, body)

export const deleteExpense = (id: number) => client.delete(`/api/v1/expenses/${id}`)

// ── 카테고리 ──────────────────────────────────────────

export const getExpenseCategories = (type?: ExpenseType) =>
  client.get<{ success: boolean; data: ExpenseCategory[] }>('/api/v1/expense-categories', {
    params: type ? { type } : undefined,
  })

export const createExpenseCategory = (body: { type: ExpenseType; name: string }) =>
  client.post<{ success: boolean; data: ExpenseCategory }>('/api/v1/expense-categories', body)

/** 기본 카테고리 중 아직 없는 것만 추가하고 전체 목록을 돌려받는다 (여러 번 호출해도 중복되지 않음) */
export const addDefaultExpenseCategories = () =>
  client.post<{ success: boolean; data: ExpenseCategory[] }>('/api/v1/expense-categories/defaults')

export const updateExpenseCategory = (id: number, body: { name: string }) =>
  client.put<{ success: boolean; data: ExpenseCategory }>(`/api/v1/expense-categories/${id}`, body)

export const deleteExpenseCategory = (id: number) =>
  client.delete(`/api/v1/expense-categories/${id}`)

// ── 요약 ──────────────────────────────────────────────

/** from/to: yyyy-MM */
export const getMonthlySummary = (params: { from: string; to: string }) =>
  client.get<{ success: boolean; data: MonthlySummaryResponse }>(
    '/api/v1/expenses/summary/monthly',
    { params },
  )

/** from/to: yyyy-MM-dd */
export const getCategorySummary = (params: { type: ExpenseType; from: string; to: string }) =>
  client.get<{ success: boolean; data: CategorySummaryResponse }>(
    '/api/v1/expenses/summary/by-category',
    { params },
  )

/** from/to: yyyy-MM-dd (양끝 포함, 최대 366일) */
export const getDailySummary = (params: { from: string; to: string }) =>
  client.get<{ success: boolean; data: DailySummaryResponse }>('/api/v1/expenses/summary/daily', { params })
