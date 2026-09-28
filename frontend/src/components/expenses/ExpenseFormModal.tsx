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
import { useState } from 'react'
import { useDialog } from './useDialog'
import { useForm, useWatch } from 'react-hook-form'

interface FormValues {
  type: ExpenseType
  categoryId: string
  amount: string
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
  onClose: () => void
  onSaved: () => void
  onManageCategories: () => void
}

const inputClass =
  'w-full rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400'

export default function ExpenseFormModal({
  expense,
  defaultDate,
  categories,
  categoriesError = null,
  onClose,
  onSaved,
  onManageCategories,
}: Props) {
  const [formError, setFormError] = useState<string | null>(null)

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
          transactionDate: expense.transactionDate,
          description: expense.description ?? '',
          memo: expense.memo ?? '',
        }
      : {
          type: 'EXPENSE',
          categoryId: '',
          amount: '',
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
  const noCategories = !categoriesError && typeCategories.length === 0

  const changeType = (type: ExpenseType) => {
    if (type === selectedType) return
    setValue('type', type)
    setValue('categoryId', '')
  }

  const onSubmit = async (values: FormValues) => {
    setFormError(null)
    const body: ExpenseRequest = {
      type: values.type,
      categoryId: Number(values.categoryId),
      amount: Number(values.amount),
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
    <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/40 p-4">
      <form
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        aria-label={expense ? '내역 수정' : '내역 추가'}
        onSubmit={handleSubmit(onSubmit)}
        className="max-h-full w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow"
      >
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
            {categoriesError ? (
              <p role="alert" className="rounded border border-red-200 p-3 text-sm text-red-500">
                {categoriesError}
              </p>
            ) : noCategories ? (
              <div className="rounded border border-dashed p-3 text-sm text-gray-600">
                <p>먼저 카테고리를 추가하세요.</p>
                <button
                  type="button"
                  onClick={onManageCategories}
                  className="mt-1 text-blue-500 hover:underline"
                >
                  카테고리 관리로 이동
                </button>
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
            disabled={isSubmitting || noCategories || !!categoriesError}
            className="rounded bg-blue-500 px-4 py-2 text-sm text-white hover:bg-blue-600 disabled:opacity-50"
          >
            저장
          </button>
        </div>
      </form>
    </div>
  )
}
