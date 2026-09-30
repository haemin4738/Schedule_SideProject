import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { PublicOnlyRoute } from './index'
import { useAuthStore } from '@/store/authStore'

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/signup"
          element={
            <PublicOnlyRoute>
              <div>SIGNUP</div>
            </PublicOnlyRoute>
          }
        />
        <Route path="/" element={<div>HOME</div>} />
      </Routes>
    </MemoryRouter>,
  )

describe('PublicOnlyRoute', () => {
  afterEach(() => useAuthStore.setState({ accessToken: null }))

  it('PublicOnlyRoute_whenLoggedOut_rendersChildren', () => {
    useAuthStore.setState({ accessToken: null })
    renderAt('/signup')
    expect(screen.getByText('SIGNUP')).toBeInTheDocument()
  })

  it('PublicOnlyRoute_whenAlreadyLoggedIn_redirectsHome', () => {
    useAuthStore.setState({ accessToken: 'acc' })
    renderAt('/signup')
    expect(screen.getByText('HOME')).toBeInTheDocument()
    expect(screen.queryByText('SIGNUP')).not.toBeInTheDocument()
  })
})
