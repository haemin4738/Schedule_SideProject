import {
  createExpense,
  updateExpense,
  type ExpenseCategory,
  type ExpenseRequest,
  type ExpenseResponse,
} from '@/api/expenses'
import { getApiErrorMessage } from '@/api/errorMessage'
import {
  EXPENSE_TYPE_OPTIONS,
  MAX_AMOUNT,
  MIN_AMOUNT,
  type ExpenseType,
} from '@/constants/expenseType'
import { PAYMENT_METHOD_OPTIONS, type PaymentMethod } from '@/constants/paymentMethod'
import { useState, type ReactNode } from 'react'
import { useDialog } from './useDialog'
import { useForm, useWatch } from 'react-hook-form'

interface FormValues {
  type: ExpenseType
  categoryId: string
  amount: string
  /** '' 은 '선택 안 함' (null 로 보냄) */
  paymentMethod: PaymentMethod | ''
  transactionDate: string
  description: string
  memo: string
}

interface Props {
  /** 수정 대상 (반드시 단건 조회 결과 — 목록 항목에는 memo 가 없다). null 이면 생성 */
  expense: ExpenseResponse | null
  defaultDate: string
  categories: ExpenseCategory[]
  /** 카테고리 조회 실패 메시지. 있으면 저장을 막고 '카테고리 추가' 안내 대신 오류를 보여준다 */
  categoriesError?: string | null
  /** 카테고리를 불러오는 중이면 true — 그동안 '카테고리 추가' 안내 대신 불러오는 중으로 보여주고 저장을 막는다 */
  categoriesLoading?: boolean
  onClose: () => void
  onSaved: () => void
  onManageCategories: () => void
  /** 제목 위에 붙일 내용 (캘린더의 입력 종류 탭) */
  header?: ReactNode
  /** 카테고리가 하나도 없을 때 '기본 카테고리 추가' 버튼으로 호출 (부모가 추가 후 목록을 갱신) */
  onAddDefaultCategories?: () => Promise<void>
}

const inputClass =
  'w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400'

export default function ExpenseFormModal({
  expense,
  defaultDate,
  categories,
  categoriesError = null,
  categoriesLoading = false,
  onClose,
  onSaved,
  onManageCategories,
  header,
  onAddDefaultCategories,
}: Props) {
  const [formError, setFormError] = useState<string | null>(null)
  const [addingDefaults, setAddingDefaults] = useState(false)

  const addDefaults = async () => {
    if (!onAddDefaultCategories) return
    setFormError(null)
    setAddingDefaults(true)
    try {
      await onAddDefaultCategories()
    } catch (err) {
      setFormError(getApiErrorMessage(err, '기본 카테고리 추가에 실패했습니다.'))
    } finally {
      setAddingDefaults(false)
    }
  }

  const {
    register,
    handleSubmit,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: expense
      ? {
          type: expense.type,
          categoryId: String(expense.categoryId),
          amount: String(expense.amount),
          paymentMethod: expense.paymentMethod ?? '',
          transactionDate: expense.transactionDate,
          description: expense.description ?? '',
          memo: expense.memo ?? '',
        }
      : {
          type: 'EXPENSE',
          categoryId: '',
          amount: '',
          paymentMethod: '',
          transactionDate: defaultDate,
          description: '',
          memo: '',
        },
  })

  const dialogRef = useDialog<HTMLFormElement>(onClose)
  // type 은 토글 버튼으로만 바꾸지만, setValue 전에 필드를 등록해 둔다 (react-hook-form 권장)
  register('type')
  const selectedType = useWatch({ control, name: 'type' })
  const typeCategories = categories.filter((c) => c.type === selectedType)
  const noCategories = !categoriesLoading && !categoriesError && typeCategories.length === 0

  const changeType = (type: ExpenseType) => {
    if (type === selectedType) return
    setValue('type', type)
    setValue('categoryId', '')
    // 수입에는 결제수단을 지정할 수 없다 (서버 400) — 숨기면서 값도 비운다
    if (type === 'INCOME') setValue('paymentMethod', '')
  }

  const onSubmit = async (values: FormValues) => {
    setFormError(null)
    const body: ExpenseRequest = {
      type: values.type,
      categoryId: Number(values.categoryId),
      amount: Number(values.amount),
      paymentMethod: values.type === 'INCOME' ? null : values.paymentMethod || null,
      transactionDate: values.transactionDate,
      // description 은 앞뒤 공백 제거, memo 는 줄바꿈/들여쓰기를 보존해 원문 그대로 (공백만이면 null) — Flutter 와 동일
      description: values.description.trim() || null,
      memo: values.memo.trim() ? values.memo : null,
    }
    try {
      if (expense) {
        await updateExpense(expense.id, body)
      } else {
        await createExpense(body)
      }
      onSaved()
    } catch (err) {
      setFormError(getApiErrorMessage(err, '저장에 실패했습니다. 입력값을 확인해주세요.'))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <form
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        aria-label={expense ? '내역 수정' : '내역 추가'}
        onSubmit={handleSubmit(onSubmit)}
        className="max-h-full w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow"
      >
        {header}
        <h2 className="mb-4 text-lg font-medium">{expense ? '내역 수정' : '내역 추가'}</h2>

        <div className="mb-3 flex gap-2">
          {EXPENSE_TYPE_OPTIONS.map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={selectedType === value}
              onClick={() => changeType(value)}
              className={`flex-1 rounded border px-3 py-2 text-sm ${
                selectedType === value ? 'border-blue-500 bg-blue-500 text-white' : 'hover:bg-gray-50'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-1 gap-3">
          <div>
            {categoriesLoading ? (
              <p role="status" className="rounded border p-3 text-sm text-gray-500">
                카테고리를 불러오는 중…
              </p>
            ) : categoriesError ? (
              <p role="alert" className="rounded border border-red-200 p-3 text-sm text-red-500">
                {categoriesError}
              </p>
            ) : noCategories ? (
              <div className="rounded border border-dashed p-3 text-sm text-gray-600">
                <p>먼저 카테고리를 추가하세요.</p>
                <div className="mt-1 flex flex-wrap gap-3">
                  {onAddDefaultCategories && (
                    <button
                      type="button"
                      onClick={() => void addDefaults()}
                      disabled={addingDefaults}
                      className="text-blue-500 hover:underline disabled:opacity-50"
                    >
                      기본 카테고리 추가
                    </button>
                  )}
                  <button type="button" onClick={onManageCategories} className="text-blue-500 hover:underline">
                    카테고리 관리로 이동
                  </button>
                </div>
              </div>
            ) : (
              <select
                aria-label="카테고리"
                {...register('categoryId', { required: '카테고리를 선택하세요.' })}
                className={inputClass}
              >
                <option value="">카테고리 선택</option>
                {typeCategories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            )}
            {errors.categoryId && (
              <p className="mt-1 text-xs text-red-500">{errors.categoryId.message}</p>
            )}
          </div>
          <div>
            <input
              {...register('amount', {
                required: '금액은 필수입니다.',
                pattern: { value: /^\d+$/, message: '금액은 숫자만 입력하세요.' },
                validate: (v) =>
                  (Number(v) >= MIN_AMOUNT && Number(v) <= MAX_AMOUNT) ||
                  '금액은 1원 이상 99,999,999,999원 이하여야 합니다.',
              })}
              inputMode="numeric"
              placeholder="금액 (원)"
              className={inputClass}
            />
            {errors.amount && <p className="mt-1 text-xs text-red-500">{errors.amount.message}</p>}
          </div>
          {selectedType === 'EXPENSE' && (
            <div>
              <select aria-label="결제수단" {...register('paymentMethod')} className={inputClass}>
                <option value="">결제수단 선택 안 함</option>
                {PAYMENT_METHOD_OPTIONS.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          )}
          <div>
            <input
              {...register('transactionDate', { required: '날짜는 필수입니다.' })}
              type="date"
              aria-label="날짜"
              className={inputClass}
            />
            {errors.transactionDate && (
              <p className="mt-1 text-xs text-red-500">{errors.transactionDate.message}</p>
            )}
          </div>
          <div>
            <input
              {...register('description', {
                maxLength: { value: 200, message: '설명은 200자 이하로 입력하세요.' },
              })}
              placeholder="설명"
              className={inputClass}
            />
            {errors.description && (
              <p className="mt-1 text-xs text-red-500">{errors.description.message}</p>
            )}
          </div>
          <div>
            <textarea
              {...register('memo', {
                maxLength: { value: 10000, message: '메모는 10000자 이하로 입력하세요.' },
              })}
              placeholder="메모"
              rows={3}
              className={inputClass}
            />
            {errors.memo && <p className="mt-1 text-xs text-red-500">{errors.memo.message}</p>}
          </div>
        </div>

        {formError && <p className="mt-3 text-sm text-red-500">{formError}</p>}

        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded border px-4 py-2 text-sm hover:bg-gray-50"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={isSubmitting || categoriesLoading || noCategories || !!categoriesError}
            className="rounded bg-blue-500 px-4 py-2 text-sm text-white hover:bg-blue-600 disabled:opacity-50"
          >
            저장
          </button>
        </div>
      </form>
    </div>
  )
}
