import {
  deleteExpense,
  getCategorySummary,
  getExpense,
  getExpenseCategories,
  getExpenses,
  getMonthlySummary,
  type CategorySummaryResponse,
  type ExpenseCategory,
  type ExpenseResponse,
  type ExpenseSummary,
  type MonthlySummaryResponse,
  type PageMeta,
} from '@/api/expenses'
import { getApiErrorMessage } from '@/api/errorMessage'
import CategoryManagerModal from '@/components/expenses/CategoryManagerModal'
import ExpenseFormModal from '@/components/expenses/ExpenseFormModal'
import {
  EXPENSE_TYPE_OPTIONS,
  formatAmount,
  formatSignedAmount,
  type ExpenseType,
} from '@/constants/expenseType'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

const PAGE_SIZE = 20
const DATE_FORMAT = 'YYYY-MM-DD'

type FormState = { expense: ExpenseResponse | null } | null

interface ListResult {
  key: string
  items: ExpenseSummary[]
  meta: PageMeta | null
  error: string | null
}

interface SummaryResult {
  key: string
  monthly: MonthlySummaryResponse | null
  byCategory: CategorySummaryResponse | null
  error: string | null
}

// 실패해도 reject 하지 않는다 (카테고리가 없으면 폼이 '먼저 카테고리를 추가하세요' 안내를 보여준다)
const fetchCategories = (): Promise<ExpenseCategory[]> =>
  getExpenseCategories()
    .then(({ data }) => data.data)
    .catch(() => [])

export default function ExpensesPage() {
  const [month, setMonth] = useState<Dayjs>(() => dayjs().startOf('month'))
  const [typeFilter, setTypeFilter] = useState<ExpenseType | ''>('')
  const [categoryFilter, setCategoryFilter] = useState<number | ''>('')
  const [page, setPage] = useState(0)

  // 조회 결과는 어떤 조건(key)으로 불러온 것인지 함께 저장한다. key 가 현재 조건과 다르면 로딩 중이다.
  const [list, setList] = useState<ListResult | null>(null)
  const [summary, setSummary] = useState<SummaryResult | null>(null)
  // 저장/삭제/카테고리 변경 후 같은 조건으로 다시 조회하기 위한 카운터
  const [reloadKey, setReloadKey] = useState(0)
  const [actionError, setActionError] = useState<string | null>(null)

  const [categories, setCategories] = useState<ExpenseCategory[]>([])
  const [form, setForm] = useState<FormState>(null)
  const [isCategoryManagerOpen, setIsCategoryManagerOpen] = useState(false)

  const from = month.format(DATE_FORMAT)
  const to = month.endOf('month').format(DATE_FORMAT)
  const yearMonth = month.format('YYYY-MM')
  const listKey = `${from}|${typeFilter}|${categoryFilter}|${page}|${reloadKey}`
  const summaryKey = `${from}|${reloadKey}`

  useEffect(() => {
    let ignore = false
    getExpenses({
      from,
      to,
      type: typeFilter || undefined,
      categoryId: categoryFilter === '' ? undefined : categoryFilter,
      page,
      size: PAGE_SIZE,
    })
      .then(({ data }) => {
        if (!ignore) setList({ key: listKey, items: data.data, meta: data.meta, error: null })
      })
      .catch((err) => {
        if (ignore) return
        setList({
          key: listKey,
          items: [],
          meta: null,
          error: getApiErrorMessage(err, '가계부 내역을 불러오지 못했습니다.'),
        })
      })
    // 빠르게 월/필터를 바꿀 때 늦게 도착한 이전 응답이 화면을 덮어쓰지 않도록 한다
    return () => {
      ignore = true
    }
  }, [listKey, from, to, typeFilter, categoryFilter, page])

  useEffect(() => {
    let ignore = false
    Promise.all([
      getMonthlySummary({ from: yearMonth, to: yearMonth }),
      getCategorySummary({ type: 'EXPENSE', from, to }),
    ])
      .then(([monthlyRes, categoryRes]) => {
        if (ignore) return
        setSummary({
          key: summaryKey,
          monthly: monthlyRes.data.data,
          byCategory: categoryRes.data.data,
          error: null,
        })
      })
      .catch(() => {
        if (ignore) return
        setSummary({
          key: summaryKey,
          monthly: null,
          byCategory: null,
          error: '요약 정보를 불러오지 못했습니다.',
        })
      })
    return () => {
      ignore = true
    }
  }, [summaryKey, yearMonth, from, to])

  useEffect(() => {
    fetchCategories().then(setCategories)
  }, [])

  // 카테고리 관리 모달의 onChanged — fetchCategories 가 reject 하지 않으므로 안전하다
  const reloadCategories = useCallback(async () => {
    setCategories(await fetchCategories())
  }, [])

  const reloadAfterChange = () => setReloadKey((k) => k + 1)

  const moveMonth = (diff: number) => {
    setMonth((m) => m.add(diff, 'month'))
    setPage(0)
    setActionError(null)
  }

  const changeType = (type: ExpenseType | '') => {
    setTypeFilter(type)
    setCategoryFilter('')
    setPage(0)
  }

  // 목록 항목에는 memo 가 없으므로 반드시 단건 조회 후 폼을 연다
  const startEdit = async (item: ExpenseSummary) => {
    setActionError(null)
    try {
      const { data } = await getExpense(item.id)
      setForm({ expense: data.data })
    } catch (err) {
      setActionError(getApiErrorMessage(err, '상세 정보를 불러오지 못했습니다.'))
    }
  }

  const onDelete = async (item: ExpenseSummary) => {
    if (!window.confirm('삭제하시겠습니까?')) return
    setActionError(null)
    try {
      await deleteExpense(item.id)
      reloadAfterChange()
    } catch (err) {
      setActionError(getApiErrorMessage(err, '삭제에 실패했습니다.'))
    }
  }

  const closeCategoryManager = () => {
    setIsCategoryManagerOpen(false)
    // 이름 변경이 목록/요약의 카테고리명에 반영되도록 재조회
    reloadAfterChange()
  }

  const items = list?.items ?? []
  const meta = list?.meta ?? null
  const isLoading = list?.key !== listKey
  const listError = isLoading ? null : (list?.error ?? null)
  // 월을 바꾼 직후 이전 달 요약이 보이지 않도록, 현재 조건으로 불러온 결과만 쓴다
  const isSummaryCurrent = summary?.key === summaryKey
  const monthly = isSummaryCurrent ? summary.monthly : null
  const byCategory = isSummaryCurrent ? summary.byCategory : null
  const summaryError = summary?.key === summaryKey ? summary.error : null

  const isCurrentMonth = month.isSame(dayjs(), 'month')
  const defaultFormDate = isCurrentMonth ? dayjs().format(DATE_FORMAT) : from
  const filterCategories = typeFilter ? categories.filter((c) => c.type === typeFilter) : categories

  return (
    <div className="mx-auto max-w-4xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">가계부</h1>
        <Link to="/" className="text-sm text-blue-500 hover:underline">
          캘린더로 이동
        </Link>
      </div>

      <div className="mb-4 flex items-center justify-center gap-4">
        <button
          type="button"
          aria-label="이전 달"
          onClick={() => moveMonth(-1)}
          className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
        >
          ◀
        </button>
        <span className="text-lg font-medium">{month.format('YYYY년 M월')}</span>
        <button
          type="button"
          aria-label="다음 달"
          onClick={() => moveMonth(1)}
          className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
        >
          ▶
        </button>
      </div>

      {summaryError && <p className="mb-3 text-sm text-red-500">{summaryError}</p>}

      <div className="mb-6 grid grid-cols-3 gap-3">
        <div className="rounded-xl bg-white p-4 shadow">
          <p className="text-xs text-gray-500">수입</p>
          <p data-testid="summary-income" className="text-lg font-semibold text-blue-600">
            {monthly ? formatAmount(monthly.totalIncome) : '-'}
          </p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow">
          <p className="text-xs text-gray-500">지출</p>
          <p data-testid="summary-expense" className="text-lg font-semibold text-red-500">
            {monthly ? formatAmount(monthly.totalExpense) : '-'}
          </p>
        </div>
        <div className="rounded-xl bg-white p-4 shadow">
          <p className="text-xs text-gray-500">합계</p>
          <p
            data-testid="summary-net"
            className={`text-lg font-semibold ${monthly && monthly.net < 0 ? 'text-red-500' : ''}`}
          >
            {monthly ? formatAmount(monthly.net) : '-'}
          </p>
        </div>
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <div className="flex overflow-hidden rounded border">
          {([['', '전체'], ...EXPENSE_TYPE_OPTIONS] as [ExpenseType | '', string][]).map(
            ([value, label]) => (
              <button
                key={value || 'ALL'}
                type="button"
                aria-pressed={typeFilter === value}
                onClick={() => changeType(value)}
                className={`px-3 py-1 text-sm ${
                  typeFilter === value ? 'bg-blue-500 text-white' : 'hover:bg-gray-50'
                }`}
              >
                {label}
              </button>
            ),
          )}
        </div>
        <select
          aria-label="카테고리 필터"
          value={categoryFilter}
          onChange={(e) => {
            setCategoryFilter(e.target.value === '' ? '' : Number(e.target.value))
            setPage(0)
          }}
          className="rounded border px-2 py-1 text-sm"
        >
          <option value="">전체</option>
          {filterCategories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <div className="ml-auto flex gap-2">
          <button
            type="button"
            onClick={() => setIsCategoryManagerOpen(true)}
            className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
          >
            카테고리 관리
          </button>
          <button
            type="button"
            onClick={() => setForm({ expense: null })}
            className="rounded bg-blue-500 px-3 py-1 text-sm text-white hover:bg-blue-600"
          >
            내역 추가
          </button>
        </div>
      </div>

      {listError && <p className="mb-3 text-sm text-red-500">{listError}</p>}
      {actionError && <p className="mb-3 text-sm text-red-500">{actionError}</p>}

      <div className="overflow-hidden rounded-xl bg-white shadow">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-left text-gray-600">
            <tr>
              <th className="px-4 py-2">날짜</th>
              <th className="px-4 py-2">카테고리</th>
              <th className="px-4 py-2">설명</th>
              <th className="px-4 py-2 text-right">금액</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr
                key={item.id}
                onClick={() => startEdit(item)}
                onKeyDown={(e) => {
                  if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) {
                    e.preventDefault()
                    startEdit(item)
                  }
                }}
                tabIndex={0}
                aria-label={`${item.categoryName} ${item.description ?? ''} 수정`}
                className="cursor-pointer border-t hover:bg-gray-50 focus:bg-blue-50 focus:outline-none"
              >
                <td className="px-4 py-2">{dayjs(item.transactionDate).format('M/D')}</td>
                <td className="px-4 py-2">{item.categoryName}</td>
                <td className="px-4 py-2">{item.description || '-'}</td>
                <td
                  className={`px-4 py-2 text-right ${
                    item.type === 'INCOME' ? 'text-blue-600' : 'text-red-500'
                  }`}
                >
                  {formatSignedAmount(item.type, item.amount)}
                </td>
                <td className="px-4 py-2 text-right">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onDelete(item)
                    }}
                    className="text-red-500 hover:underline"
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
            {isLoading && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                  불러오는 중...
                </td>
              </tr>
            )}
            {!isLoading && items.length === 0 && !listError && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                  내역이 없습니다
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {meta && meta.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-sm text-gray-600">
            {page + 1} / {meta.totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(meta.totalPages - 1, p + 1))}
            disabled={page >= meta.totalPages - 1}
            className="rounded border px-3 py-1 text-sm disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}

      <section className="mt-6 rounded-xl bg-white p-4 shadow">
        <h2 className="mb-3 text-lg font-medium">카테고리별 지출</h2>
        {byCategory && byCategory.categories.length > 0 ? (
          <ul className="space-y-2">
            {byCategory.categories.map((c) => {
              const ratio = byCategory.total > 0 ? (c.amount / byCategory.total) * 100 : 0
              return (
                <li key={c.categoryId} className="text-sm">
                  <div className="mb-1 flex justify-between">
                    <span>
                      {c.categoryName} <span className="text-gray-400">({c.count}건)</span>
                    </span>
                    <span>
                      {formatAmount(c.amount)}{' '}
                      <span className="text-gray-400">{ratio.toFixed(1)}%</span>
                    </span>
                  </div>
                  <div className="h-2 rounded bg-gray-100">
                    <div className="h-2 rounded bg-red-400" style={{ width: `${ratio}%` }} />
                  </div>
                </li>
              )
            })}
          </ul>
        ) : (
          <p className="text-sm text-gray-400">지출 내역이 없습니다</p>
        )}
      </section>

      {form && (
        <ExpenseFormModal
          expense={form.expense}
          defaultDate={defaultFormDate}
          categories={categories}
          onClose={() => setForm(null)}
          onSaved={() => {
            setForm(null)
            reloadAfterChange()
          }}
          onManageCategories={() => {
            setForm(null)
            setIsCategoryManagerOpen(true)
          }}
        />
      )}

      {isCategoryManagerOpen && (
        <CategoryManagerModal
          categories={categories}
          initialType={typeFilter || 'EXPENSE'}
          onChanged={reloadCategories}
          onClose={closeCategoryManager}
        />
      )}
    </div>
  )
}
