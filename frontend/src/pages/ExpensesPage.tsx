import {
  addDefaultExpenseCategories,
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
import CategorySummarySection from '@/components/expenses/CategorySummarySection'
import ExpenseFormModal from '@/components/expenses/ExpenseFormModal'
import ExpenseListTable from '@/components/expenses/ExpenseListTable'
import ExpenseSummaryCards from '@/components/expenses/ExpenseSummaryCards'
import { EXPENSE_TYPE_LABELS, EXPENSE_TYPE_OPTIONS, type ExpenseType } from '@/constants/expenseType'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useRef, useState } from 'react'
import LogoutButton from '@/components/LogoutButton'
import { Link, useSearchParams } from 'react-router-dom'

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

type CategoriesResult = { ok: true; items: ExpenseCategory[] } | { ok: false; error: string }

// 실패해도 reject 하지 않는다. 실패를 빈 목록으로 취급하면 '카테고리를 추가하세요' 안내가 잘못 보이므로 구분한다
const fetchCategories = (): Promise<CategoriesResult> =>
  getExpenseCategories()
    .then(({ data }): CategoriesResult => ({ ok: true, items: data.data }))
    .catch(
      (err): CategoriesResult => ({
        ok: false,
        error: getApiErrorMessage(err, '카테고리를 불러오지 못했습니다.'),
      }),
    )

/** ?month=YYYY-MM (캘린더의 가계부 합계에서 이동) — 형식이 틀리면 이번 달 */
const initialMonth = (param: string | null): Dayjs => {
  if (param && /^\d{4}-(0[1-9]|1[0-2])$/.test(param)) return dayjs(`${param}-01`).startOf('month')
  return dayjs().startOf('month')
}

export default function ExpensesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [month, setMonth] = useState<Dayjs>(() => initialMonth(searchParams.get('month')))
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
  const [categoriesError, setCategoriesError] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>(null)
  const [isCategoryManagerOpen, setIsCategoryManagerOpen] = useState(false)
  // 행을 빠르게 연속 클릭했을 때 마지막 클릭의 상세 조회만 폼에 반영하기 위한 요청 순번
  const editRequestSeq = useRef(0)

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
        if (ignore) return
        // 마지막 페이지의 마지막 항목을 지운 경우 등: 빈 페이지에 머무르지 않고 앞 페이지로 이동해 다시 조회
        if (data.data.length === 0 && page > 0) {
          setPage(Math.max(0, Math.min(page - 1, data.meta.totalPages - 1)))
          return
        }
        setList({ key: listKey, items: data.data, meta: data.meta, error: null })
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

  // 초기 로드 및 카테고리 관리 모달의 onChanged — fetchCategories 가 reject 하지 않으므로 안전하다.
  // 실패 시 기존 목록은 유지하고 오류만 표시한다.
  const applyCategories = useCallback((result: CategoriesResult) => {
    if (result.ok) {
      setCategories(result.items)
      setCategoriesError(null)
    } else {
      setCategoriesError(result.error)
    }
  }, [])

  useEffect(() => {
    fetchCategories().then(applyCategories)
  }, [applyCategories])

  const loadCategories = useCallback(async () => {
    applyCategories(await fetchCategories())
  }, [applyCategories])

  // 필터로 선택된 카테고리가 (카테고리 관리에서) 삭제되면 필터를 해제한다.
  // 렌더 중 state 조정 (React 권장 패턴) — 조정 후에는 조건이 거짓이 되어 반복되지 않는다.
  if (categoryFilter !== '' && !categories.some((c) => c.id === categoryFilter)) {
    setCategoryFilter('')
    setPage(0)
  }

  const reloadAfterChange = () => setReloadKey((k) => k + 1)

  // 진행 중인 상세 조회 응답이 늦게 도착해도 수정 폼을 열지 않도록 무효화한다
  const cancelPendingEdit = () => {
    editRequestSeq.current++
  }

  const moveMonth = (diff: number) => {
    cancelPendingEdit()
    setMonth((m) => m.add(diff, 'month'))
    // 캘린더에서 넘어온 ?month= 가 남아 있으면 새로고침 시 그 달로 돌아가므로 지운다
    if (searchParams.has('month'))
      setSearchParams(
        (params) => {
          params.delete('month')
          return params
        },
        { replace: true },
      )
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
    const seq = ++editRequestSeq.current
    setActionError(null)
    try {
      const { data } = await getExpense(item.id)
      if (seq !== editRequestSeq.current) return
      setForm({ expense: data.data })
    } catch (err) {
      if (seq !== editRequestSeq.current) return
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
    // 조회 실패 상태였다면 다시 시도할 수 있도록 카테고리도 다시 불러온다
    void loadCategories()
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
        <div className="flex items-center gap-3">
          <Link to="/" className="text-sm text-blue-500 hover:underline">
            캘린더로 이동
          </Link>
          <LogoutButton />
        </div>
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

      <ExpenseSummaryCards monthly={monthly} />

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
              {/* 유형 '전체'일 때는 같은 이름의 지출/수입 카테고리를 구분하도록 유형을 붙인다 (Flutter 와 동일) */}
              {typeFilter ? c.name : `${c.name} (${EXPENSE_TYPE_LABELS[c.type]})`}
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
            onClick={() => {
              cancelPendingEdit()
              setForm({ expense: null })
            }}
            className="rounded bg-blue-500 px-3 py-1 text-sm text-white hover:bg-blue-600"
          >
            내역 추가
          </button>
        </div>
      </div>

      {listError && <p className="mb-3 text-sm text-red-500">{listError}</p>}
      {actionError && <p className="mb-3 text-sm text-red-500">{actionError}</p>}
      {categoriesError && (
        <p className="mb-3 text-sm text-red-500">
          <span>{categoriesError}</span>{' '}
          <button type="button" onClick={() => void loadCategories()} className="underline">
            다시 시도
          </button>
        </p>
      )}

      <ExpenseListTable
        items={items}
        meta={meta}
        page={page}
        isLoading={isLoading}
        error={listError}
        onEdit={startEdit}
        onDelete={onDelete}
        onPageChange={setPage}
      />

      <CategorySummarySection byCategory={byCategory} />

      {form && (
        <ExpenseFormModal
          expense={form.expense}
          defaultDate={defaultFormDate}
          categories={categories}
          categoriesError={categoriesError}
          onAddDefaultCategories={async () => {
            await addDefaultExpenseCategories()
            await loadCategories()
          }}
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
          loadError={categoriesError}
          initialType={typeFilter || 'EXPENSE'}
          onChanged={loadCategories}
          onClose={closeCategoryManager}
        />
      )}
    </div>
  )
}
