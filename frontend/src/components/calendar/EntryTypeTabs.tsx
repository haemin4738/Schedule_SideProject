export type EntryKind = 'event' | 'jobApplication' | 'expense'

export const ENTRY_KIND_OPTIONS: ReadonlyArray<readonly [EntryKind, string]> = [
  ['event', '일정'],
  ['jobApplication', '구직활동'],
  ['expense', '가계부'],
]

interface Props {
  value: EntryKind
  onChange: (kind: EntryKind) => void
}

/** 캘린더에서 날짜를 눌러 새로 입력할 때 일정·구직활동·가계부 중 고르는 탭 (입력 모달 맨 위에 붙는다) */
export default function EntryTypeTabs({ value, onChange }: Props) {
  return (
    <div role="group" aria-label="입력 종류" className="mb-4 flex gap-1 rounded-lg bg-gray-100 p-1">
      {ENTRY_KIND_OPTIONS.map(([kind, label]) => (
        <button
          key={kind}
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
