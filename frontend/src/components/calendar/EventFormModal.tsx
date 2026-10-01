import { getApiErrorMessage } from '@/api/errorMessage'
import {
  createEvent,
  deleteEvent,
  updateEvent,
  type EventDetail,
} from '@/api/events'
import { useDialog } from '@/components/expenses/useDialog'
import {
  CATEGORY_DEFAULT_COLORS,
  EVENT_CATEGORY_OPTIONS,
  EVENT_COLORS,
  HEX_COLOR,
} from '@/constants/eventCategory'
import dayjs from 'dayjs'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toEventRequest, type EventFormValues as FormValues } from './calendarUtils'

interface Props {
  /** 수정 대상 (단건 조회 결과). null 이면 생성 */
  event: EventDetail | null
  /** 생성 시 기본 시작/종료 (캘린더에서 고른 날짜·시간) */
  defaultStart: Date
  defaultEnd: Date
  defaultAllDay?: boolean
  onClose: () => void
  onSaved: () => void
}

const TITLE_MAX = 200

const inputClass =
  'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 aria-[invalid=true]:border-red-400'

const toFormValues = (event: EventDetail | null, start: Date, end: Date, allDay: boolean): FormValues => {
  if (event) {
    const s = dayjs(event.startAt)
    const e = event.endAt ? dayjs(event.endAt) : s.add(1, 'hour')
    return {
      title: event.title,
      allDay: event.allDay,
      startDate: s.format('YYYY-MM-DD'),
      startTime: s.format('HH:mm'),
      endDate: e.format('YYYY-MM-DD'),
      endTime: e.format('HH:mm'),
      eventCategory: event.eventCategory ?? 'PERSONAL',
      // 형식이 잘못된 색은 '카테고리 기본 색'으로 정규화한다 (그대로 다시 저장하지 않게)
      color: event.color && HEX_COLOR.test(event.color) ? event.color : '',
      location: event.location ?? '',
      description: event.description ?? '',
    }
  }
  return {
    title: '',
    allDay,
    startDate: dayjs(start).format('YYYY-MM-DD'),
    startTime: dayjs(start).format('HH:mm'),
    endDate: dayjs(end).format('YYYY-MM-DD'),
    endTime: dayjs(end).format('HH:mm'),
    eventCategory: 'PERSONAL',
    color: '',
    location: '',
    description: '',
  }
}

export default function EventFormModal({ event, defaultStart, defaultEnd, defaultAllDay = false, onClose, onSaved }: Props) {
  const isEdit = event !== null
  const [error, setError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const {
    register,
    handleSubmit,
    control,
    setValue,
    getValues,
    getFieldState,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormValues>({ defaultValues: toFormValues(event, defaultStart, defaultEnd, defaultAllDay) })
  const allDay = useWatch({ control, name: 'allDay' })
  const category = useWatch({ control, name: 'eventCategory' })
  const color = useWatch({ control, name: 'color' })

  // 종료 없이 저장된 일정은 폼에 임시 종료(1시간 뒤)를 채워 보여줄 뿐이다.
  // 종료·종일을 건드리지 않았으면 종료 없음을 유지하고, 임시 종료로 검증하지도 않는다
  const keepsNoEnd = () =>
    isEdit &&
    event.endAt === null &&
    !getFieldState('endDate').isDirty &&
    !getFieldState('endTime').isDirty &&
    !getFieldState('allDay').isDirty

  const validateEnd = () => {
    if (keepsNoEnd()) return true
    const v = getValues()
    const start = v.allDay ? v.startDate : `${v.startDate}T${v.startTime}`
    const end = v.allDay ? v.endDate : `${v.endDate}T${v.endTime}`
    // 같은 형식(YYYY-MM-DD[THH:mm])끼리는 문자열 비교가 시간순과 같다
    return end >= start || '종료는 시작보다 빠를 수 없습니다.'
  }

  const onSubmit = async (values: FormValues) => {
    setError(null)
    try {
      const body = toEventRequest(values)
      if (keepsNoEnd()) body.endAt = null
      if (isEdit) await updateEvent(event.id, body)
      else await createEvent(body)
      onSaved()
    } catch (err) {
      setError(getApiErrorMessage(err, '일정을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.'))
    }
  }

  const onDelete = async () => {
    if (!isEdit) return
    setError(null)
    setDeleting(true)
    try {
      await deleteEvent(event.id)
      onSaved()
    } catch (err) {
      setDeleting(false)
      setConfirmingDelete(false)
      setError(getApiErrorMessage(err, '일정을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.'))
    }
  }

  const busy = isSubmitting || deleting
  const [confirmingDiscard, setConfirmingDiscard] = useState(false)

  // 저장·삭제 중에는 닫지 않고(결과 오류를 놓치지 않게), 입력한 내용이 있으면 버릴지 먼저 묻는다
  const requestClose = () => {
    if (busy) return
    if (isDirty) setConfirmingDiscard(true)
    else onClose()
  }
  const dialogRef = useDialog<HTMLDivElement>(requestClose)

  // 색상 라디오: Tab 으로는 선택된 항목 하나에만 들어오고 방향키로 고르며 이동한다 (WAI-ARIA radio group 패턴)
  const colorValues = ['', ...EVENT_COLORS.map((c) => c.value)]
  // 목록에 없는 색(다른 클라이언트가 저장한 색)이면 첫 항목을 Tab 진입점으로 둔다
  const colorTabStop = colorValues.includes(color) ? color : ''
  const onColorKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const current = colorValues.indexOf(colorTabStop)
    const last = colorValues.length - 1
    const target = {
      ArrowRight: (current + 1) % colorValues.length,
      ArrowDown: (current + 1) % colorValues.length,
      ArrowLeft: (current - 1 + colorValues.length) % colorValues.length,
      ArrowUp: (current - 1 + colorValues.length) % colorValues.length,
      Home: 0,
      End: last,
    }[e.key]
    if (target === undefined) return
    e.preventDefault()
    const next = colorValues[target]
    setValue('color', next, { shouldDirty: true })
    e.currentTarget.querySelector<HTMLElement>(`[data-color="${next}"]`)?.focus()
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) requestClose()
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="event-form-title"
        tabIndex={-1}
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-xl bg-white p-6 shadow-xl outline-none"
      >
        <h2 id="event-form-title" className="mb-4 text-lg font-semibold">
          {isEdit ? '일정 수정' : '새 일정'}
        </h2>

        {error && (
          <p role="alert" className="mb-4 rounded bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4" aria-label="일정 입력">
          <div>
            <label htmlFor="event-title" className="sr-only">
              제목
            </label>
            <input
              id="event-title"
              {...register('title', {
                validate: (v) => v.trim().length > 0 || '제목을 입력해 주세요.',
                maxLength: { value: TITLE_MAX, message: `제목은 ${TITLE_MAX}자 이하여야 합니다.` },
              })}
              placeholder="제목 추가"
              maxLength={TITLE_MAX}
              aria-invalid={errors.title ? true : undefined}
              aria-describedby={errors.title ? 'event-title-error' : undefined}
              className="w-full border-b-2 border-gray-200 px-1 py-2 text-xl outline-none focus:border-blue-500 aria-[invalid=true]:border-red-400"
            />
            {errors.title && (
              <p id="event-title-error" className="mt-1 text-xs text-red-600">
                {errors.title.message}
              </p>
            )}
          </div>

          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" {...register('allDay')} className="h-4 w-4" />
            종일
          </label>

          <fieldset className="grid grid-cols-[auto_1fr_auto] items-center gap-x-2 gap-y-2">
            <legend className="sr-only">일시</legend>
            <label htmlFor="event-start-date" className="text-sm text-gray-600">
              시작
            </label>
            <input id="event-start-date" type="date" {...register('startDate', { required: '시작 날짜를 입력해 주세요.', deps: ['endDate'] })} className={inputClass} />
            {allDay ? (
              <span />
            ) : (
              <>
                <label htmlFor="event-start-time" className="sr-only">
                  시작 시간
                </label>
                <input id="event-start-time" type="time" {...register('startTime', { required: true, deps: ['endDate'] })} className={inputClass} />
              </>
            )}
            <label htmlFor="event-end-date" className="text-sm text-gray-600">
              종료
            </label>
            <input
              id="event-end-date"
              type="date"
              {...register('endDate', { required: '종료 날짜를 입력해 주세요.', validate: validateEnd })}
              aria-invalid={errors.endDate ? true : undefined}
              aria-describedby={errors.endDate ? 'event-end-error' : undefined}
              className={inputClass}
            />
            {allDay ? (
              <span />
            ) : (
              <>
                <label htmlFor="event-end-time" className="sr-only">
                  종료 시간
                </label>
                <input
                  id="event-end-time"
                  type="time"
                  {...register('endTime', { required: true, deps: ['endDate'] })}
                  className={inputClass}
                />
              </>
            )}
          </fieldset>
          {errors.endDate && (
            <p id="event-end-error" className="-mt-2 text-xs text-red-600">
              {errors.endDate.message}
            </p>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="event-category" className="mb-1 block text-sm text-gray-600">
                카테고리
              </label>
              <select id="event-category" {...register('eventCategory')} className={inputClass}>
                {EVENT_CATEGORY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="event-location" className="mb-1 block text-sm text-gray-600">
                장소
              </label>
              <input id="event-location" {...register('location')} maxLength={255} className={inputClass} />
            </div>
          </div>

          <div
            role="radiogroup"
            aria-label="색상"
            onKeyDown={onColorKeyDown}
            className="flex flex-wrap items-center gap-2"
          >
            <button
              type="button"
              role="radio"
              aria-checked={color === ''}
              tabIndex={colorTabStop === '' ? 0 : -1}
              data-color=""
              aria-label="카테고리 기본 색"
              title="카테고리 기본 색"
              onClick={() => setValue('color', '', { shouldDirty: true })}
              style={{ backgroundColor: CATEGORY_DEFAULT_COLORS[category] }}
              className={`h-6 w-6 rounded-full ring-offset-2 ${color === '' ? 'ring-2 ring-gray-800' : ''}`}
            >
              <span aria-hidden="true" className="text-[10px] text-white">
                자동
              </span>
            </button>
            {EVENT_COLORS.map((c) => (
              <button
                key={c.value}
                type="button"
                role="radio"
                aria-checked={color === c.value}
                tabIndex={colorTabStop === c.value ? 0 : -1}
                data-color={c.value}
                aria-label={c.label}
                title={c.label}
                onClick={() => setValue('color', c.value, { shouldDirty: true })}
                style={{ backgroundColor: c.value }}
                className={`h-6 w-6 rounded-full ring-offset-2 ${color === c.value ? 'ring-2 ring-gray-800' : ''}`}
              />
            ))}
          </div>

          <div>
            <label htmlFor="event-description" className="mb-1 block text-sm text-gray-600">
              설명
            </label>
            <textarea id="event-description" {...register('description')} rows={3} className={inputClass} />
          </div>

          <div className="mt-2 flex items-center justify-between gap-2">
            {isEdit ? (
              confirmingDelete ? (
                <div className="flex items-center gap-2">
                  <span className="text-sm text-gray-700">삭제할까요?</span>
                  <button
                    type="button"
                    onClick={onDelete}
                    disabled={busy}
                    className="rounded-md bg-red-600 px-3 py-1.5 text-sm text-white hover:bg-red-700 disabled:opacity-60"
                  >
                    {deleting ? '삭제 중...' : '삭제 확인'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmingDelete(false)}
                    disabled={busy}
                    className="rounded-md px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100"
                  >
                    아니요
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(true)}
                  disabled={busy}
                  className="rounded-md px-3 py-1.5 text-sm text-red-600 hover:bg-red-50"
                >
                  삭제
                </button>
              )
            ) : (
              <span />
            )}
            <div className="flex gap-2">
              <button
                type="button"
                onClick={requestClose}
                disabled={busy}
                className="rounded-md px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={busy}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
              >
                {isSubmitting ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </form>

        {confirmingDiscard && (
          <div role="alert" className="mt-4 flex items-center justify-between gap-2 rounded-md bg-amber-50 px-3 py-2">
            <span className="text-sm text-amber-900">작성 중인 내용을 버릴까요?</span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={onClose}
                className="rounded-md bg-amber-600 px-3 py-1.5 text-sm text-white hover:bg-amber-700"
              >
                버리기
              </button>
              <button
                type="button"
                // 확인 창이 뜨면 포커스를 안전한 쪽(계속 작성)으로 옮긴다
                autoFocus
                onClick={() => setConfirmingDiscard(false)}
                className="rounded-md px-3 py-1.5 text-sm text-amber-900 hover:bg-amber-100"
              >
                계속 작성
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
