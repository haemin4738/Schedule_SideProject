import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import SessionNoticeDialog from './SessionNoticeDialog'

describe('SessionNoticeDialog', () => {
  it('render_withMessage_showsAlertDialogWithConfirmFocused', () => {
    render(<SessionNoticeDialog message="보안 안내" onClose={() => {}} />)

    const dialog = screen.getByRole('alertdialog', { name: '로그아웃 안내' })
    expect(dialog).toHaveAccessibleDescription('보안 안내')
    expect(screen.getByRole('button', { name: '확인' })).toHaveFocus()
  })

  it('onKeyDown_tabAndShiftTab_keepsFocusOnConfirm', async () => {
    render(
      <>
        <button type="button">배경 버튼</button>
        <SessionNoticeDialog message="보안 안내" onClose={() => {}} />
      </>,
    )

    await userEvent.tab()
    expect(screen.getByRole('button', { name: '확인' })).toHaveFocus()
    await userEvent.tab({ shift: true })
    expect(screen.getByRole('button', { name: '확인' })).toHaveFocus()
  })

  it('onClick_confirm_callsOnClose', async () => {
    const onClose = vi.fn()
    render(<SessionNoticeDialog message="보안 안내" onClose={onClose} />)

    await userEvent.click(screen.getByRole('button', { name: '확인' }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('onKeyDown_escape_callsOnClose', async () => {
    const onClose = vi.fn()
    render(<SessionNoticeDialog message="보안 안내" onClose={onClose} />)

    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
