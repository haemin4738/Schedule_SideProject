import {
  createExpenseCategory,
  deleteExpenseCategory,
  updateExpenseCategory,
  type ExpenseCategory,
} from '@/api/expenses'
import { getApiErrorMessage } from '@/api/errorMessage'
import { EXPENSE_TYPE_OPTIONS, type ExpenseType } from '@/constants/expenseType'
import { useState } from 'react'

const MAX_NAME_LENGTH = 50

interface Props {
  categories: ExpenseCategory[]
  initialType?: ExpenseType
  /** 카테고리 추가/변경/삭제 후 부모가 목록을 다시 불러오도록 호출 */
  onChanged: () => Promise<void>
  onClose: () => void
}

const validateName = (name: string): string | null => {
  const trimmed = name.trim()
  if (!trimmed) return '카테고리 이름을 입력하세요.'
  if (trimmed.length > MAX_NAME_LENGTH) return '카테고리 이름은 50자 이하로 입력하세요.'
  return null
}

export default function CategoryManagerModal({
  categories,
  initialType = 'EXPENSE',
  onChanged,
  onClose,
}: Props) {
  const [tab, setTab] = useState<ExpenseType>(initialType)
  const [newName, setNewName] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isBusy, setIsBusy] = useState(false)

  const tabCategories = categories.filter((c) => c.type === tab)

  const run = async (action: () => Promise<unknown>, fallback: string): Promise<boolean> => {
    setError(null)
    setIsBusy(true)
    try {
      await action()
      await onChanged()
      return true
    } catch (err) {
      setError(getApiErrorMessage(err, fallback))
      return false
    } finally {
      setIsBusy(false)
    }
  }

  const onAdd = async () => {
    const invalid = validateName(newName)
    if (invalid) {
      setError(invalid)
      return
    }
    const ok = await run(
      () => createExpenseCategory({ type: tab, name: newName.trim() }),
      '카테고리 추가에 실패했습니다.',
    )
    if (ok) setNewName('')
  }

  const onRename = async (id: number) => {
    const invalid = validateName(editingName)
    if (invalid) {
      setError(invalid)
      return
    }
    const ok = await run(
      () => updateExpenseCategory(id, { name: editingName.trim() }),
      '이름 변경에 실패했습니다.',
    )
    if (ok) setEditingId(null)
  }

  const onDelete = async (category: ExpenseCategory) => {
    if (!window.confirm(`'${category.name}' 카테고리를 삭제하시겠습니까?`)) return
    await run(() => deleteExpenseCategory(category.id), '카테고리 삭제에 실패했습니다.')
  }

  const changeTab = (type: ExpenseType) => {
    setTab(type)
    setEditingId(null)
    setError(null)
  }

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-black/40 p-4">
      <div
        role="dialog"
        aria-label="카테고리 관리"
        className="max-h-full w-full max-w-md overflow-y-auto rounded-xl bg-white p-6 shadow"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-medium">카테고리 관리</h2>
          <button type="button" onClick={onClose} className="text-sm text-gray-500 hover:underline">
            닫기
          </button>
        </div>

        <div className="mb-3 flex gap-2">
          {EXPENSE_TYPE_OPTIONS.map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={tab === value}
              onClick={() => changeTab(value)}
              className={`flex-1 rounded border px-3 py-2 text-sm ${
                tab === value ? 'border-blue-500 bg-blue-500 text-white' : 'hover:bg-gray-50'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="mb-3 flex gap-2">
          <input
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="새 카테고리 이름"
            maxLength={MAX_NAME_LENGTH}
            className="flex-1 rounded border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-400"
          />
          <button
            type="button"
            onClick={onAdd}
            disabled={isBusy}
            className="rounded bg-blue-500 px-4 py-2 text-sm text-white hover:bg-blue-600 disabled:opacity-50"
          >
            추가
          </button>
        </div>

        {error && <p className="mb-3 text-sm text-red-500">{error}</p>}

        <ul className="divide-y rounded border">
          {tabCategories.map((c) => (
            <li key={c.id} className="flex items-center gap-2 px-3 py-2 text-sm">
              {editingId === c.id ? (
                <>
                  <input
                    value={editingName}
                    onChange={(e) => setEditingName(e.target.value)}
                    aria-label="카테고리 이름"
                    maxLength={MAX_NAME_LENGTH}
                    className="flex-1 rounded border px-2 py-1"
                  />
                  <button
                    type="button"
                    onClick={() => onRename(c.id)}
                    disabled={isBusy}
                    className="text-blue-500 hover:underline disabled:opacity-50"
                  >
                    저장
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditingId(null)}
                    className="text-gray-500 hover:underline"
                  >
                    취소
                  </button>
                </>
              ) : (
                <>
                  <span className="flex-1">{c.name}</span>
                  <button
                    type="button"
                    onClick={() => {
                      setEditingId(c.id)
                      setEditingName(c.name)
                      setError(null)
                    }}
                    className="text-blue-500 hover:underline"
                  >
                    이름 변경
                  </button>
                  <button
                    type="button"
                    onClick={() => onDelete(c)}
                    disabled={isBusy}
                    className="text-red-500 hover:underline disabled:opacity-50"
                  >
                    삭제
                  </button>
                </>
              )}
            </li>
          ))}
          {tabCategories.length === 0 && (
            <li className="px-3 py-4 text-center text-sm text-gray-400">카테고리가 없습니다.</li>
          )}
        </ul>
      </div>
    </div>
  )
}
