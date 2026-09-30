import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import LogoutButton from './LogoutButton'
import { logoutSession } from '@/auth/logout'

vi.mock('@/auth/logout', () => ({ logoutSession: vi.fn() }))

describe('LogoutButton', () => {
  it('onClick_always_callsLogoutSession', async () => {
    vi.mocked(logoutSession).mockResolvedValue()
    render(<LogoutButton />)

    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(logoutSession).toHaveBeenCalledTimes(1)
  })
})
