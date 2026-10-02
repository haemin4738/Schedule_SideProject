import LogoutButton from '@/components/LogoutButton'
import { CATEGORY_DEFAULT_COLORS } from '@/constants/eventCategory'
import { Link } from 'react-router-dom'
import { CALENDAR_LAYER_OPTIONS, type CalendarLayer, type CalendarLayers } from './calendarLayers'
import { JOB_APPLICATION_COLOR } from './calendarUtils'

/** 표시 항목 앞의 색 — 달력에 그려지는 색과 맞춘다 */
const LAYER_COLORS: Record<CalendarLayer, string> = {
  events: CATEGORY_DEFAULT_COLORS.PERSONAL,
  jobApplications: JOB_APPLICATION_COLOR,
  expenses: '#16a34a',
  specialDays: '#ef4444',
}

const NAV_LINK = 'block rounded-r-full px-4 py-2 text-sm text-gray-700 hover:bg-gray-100'

interface Props {
  layers: CalendarLayers
  onToggleLayer: (layer: CalendarLayer) => void
  onCreate: () => void
  /** 메뉴 이동·만들기 후 서랍을 닫는 등 (데스크톱 사이드바에서는 없음) */
  onNavigate?: () => void
}

/** 캘린더 왼쪽 패널 — 만들기, 표시 항목, 메뉴, 로그아웃. 데스크톱은 고정 사이드바, 폰은 서랍 안에 같은 내용을 쓴다 */
export default function CalendarSidebar({ layers, onToggleLayer, onCreate, onNavigate }: Props) {
  return (
    <div className="flex h-full flex-col gap-6 py-4 pr-3">
      <div className="pl-4">
        <button
          type="button"
          onClick={() => {
            onNavigate?.()
            onCreate()
          }}
          className="flex items-center gap-2 rounded-2xl bg-white px-5 py-3 text-sm font-medium text-gray-700 shadow-md ring-1 ring-gray-200 hover:bg-gray-50"
        >
          <span aria-hidden="true" className="text-2xl leading-none text-blue-600">
            +
          </span>
          만들기
        </button>
      </div>

      <section className="pl-4">
        <h2 className="mb-2 text-xs font-semibold text-gray-500">표시할 항목</h2>
        <div role="group" aria-label="캘린더에 표시할 항목" className="flex flex-col">
          {CALENDAR_LAYER_OPTIONS.map(([layer, label]) => (
            <button
              key={layer}
              type="button"
              aria-pressed={layers[layer]}
              onClick={() => onToggleLayer(layer)}
              className="flex items-center gap-3 rounded-md px-1 py-1.5 text-left text-sm text-gray-700 hover:bg-gray-100"
            >
              <span
                aria-hidden="true"
                className="flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border-2 text-[10px] leading-none text-white"
                style={{
                  borderColor: LAYER_COLORS[layer],
                  backgroundColor: layers[layer] ? LAYER_COLORS[layer] : 'transparent',
                }}
              >
                {layers[layer] ? '✓' : ''}
              </span>
              {label}
            </button>
          ))}
        </div>
      </section>

      <nav aria-label="주요 메뉴" className="flex flex-col">
        <Link
          to="/"
          aria-current="page"
          onClick={onNavigate}
          className={`${NAV_LINK} bg-blue-50 font-medium text-blue-700 hover:bg-blue-50`}
        >
          캘린더
        </Link>
        <Link to="/job-applications" onClick={onNavigate} className={NAV_LINK}>
          구직활동
        </Link>
        <Link to="/expenses" onClick={onNavigate} className={NAV_LINK}>
          가계부
        </Link>
      </nav>

      <div className="mt-auto pl-4">
        <LogoutButton />
      </div>
    </div>
  )
}
