import { useAuthStore } from '@/store/authStore'
import { Navigate, Route, BrowserRouter as Router, Routes } from 'react-router-dom'
import CalendarPage from '@/pages/CalendarPage'
import ExpensesPage from '@/pages/ExpensesPage'
import JobApplicationsPage from '@/pages/JobApplicationsPage'
import LoginPage from '@/pages/LoginPage'
import OAuthCallbackPage from '@/pages/OAuthCallbackPage'
import SignupPage from '@/pages/SignupPage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  return accessToken ? <>{children}</> : <Navigate to="/login" replace />
}

/** 로그인 상태에서 로그인/가입 화면에 들어오면 홈으로 보낸다 (가입 후 자동 로그인이 기존 세션을 덮어쓰지 않게) */
export function PublicOnlyRoute({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  return accessToken ? <Navigate to="/" replace /> : <>{children}</>
}

export default function AppRouter() {
  return (
    <Router>
      <Routes>
        <Route
          path="/login"
          element={
            <PublicOnlyRoute>
              <LoginPage />
            </PublicOnlyRoute>
          }
        />
        <Route
          path="/signup"
          element={
            <PublicOnlyRoute>
              <SignupPage />
            </PublicOnlyRoute>
          }
        />
        <Route path="/oauth/callback/:provider" element={<OAuthCallbackPage />} />
        <Route
          path="/"
          element={
            <PrivateRoute>
              <CalendarPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/job-applications"
          element={
            <PrivateRoute>
              <JobApplicationsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/expenses"
          element={
            <PrivateRoute>
              <ExpensesPage />
            </PrivateRoute>
          }
        />
      </Routes>
    </Router>
  )
}
