import { useEffect, useRef } from 'react'

export type EntryKind = 'event' | 'jobApplication' | 'expense'

export const ENTRY_KIND_OPTIONS: ReadonlyArray<readonly [EntryKind, string]> = [
  ['event', '일정'],
  ['jobApplication', '구직활동'],
  ['expense', '가계부'],
]

interface Props {
  value: EntryKind
  onChange: (kind: EntryKind) => void
  /** 탭을 바꿔 입력 창이 새로 열렸을 때 고른 탭에 포커스를 돌려준다 (처음 열 때는 대화상자에 포커스) */
  focusSelected?: boolean
}

/** 캘린더에서 날짜를 눌러 새로 입력할 때 일정·구직활동·가계부 중 고르는 탭 (입력 모달 맨 위에 붙는다) */
export default function EntryTypeTabs({ value, onChange, focusSelected = false }: Props) {
  const selectedRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (!focusSelected) return
    // 대화상자가 열리며 자신에게 주는 포커스(부모 effect) 다음에 실행되도록 한 틱 미룬다
    const id = setTimeout(() => selectedRef.current?.focus())
    return () => clearTimeout(id)
    // 입력 창이 열릴 때 한 번만
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div role="group" aria-label="입력 종류" className="mb-4 flex gap-1 rounded-lg bg-gray-100 p-1">
      {ENTRY_KIND_OPTIONS.map(([kind, label]) => (
        <button
          key={kind}
          ref={value === kind ? selectedRef : undefined}
          type="button"
          aria-pressed={value === kind}
          onClick={() => onChange(kind)}
          className={`flex-1 rounded-md px-3 py-1.5 text-sm ${
            value === kind ? 'bg-white font-medium text-blue-700 shadow-sm' : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  )
}
