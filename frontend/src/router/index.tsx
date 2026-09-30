import { useAuthStore } from '@/store/authStore'
import { Navigate, Route, BrowserRouter as Router, Routes } from 'react-router-dom'
import CalendarPage from '@/pages/CalendarPage'
import ExpensesPage from '@/pages/ExpensesPage'
import JobApplicationsPage from '@/pages/JobApplicationsPage'
import LoginPage from '@/pages/LoginPage'
import OAuthCallbackPage from '@/pages/OAuthCallbackPage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  return accessToken ? <>{children}</> : <Navigate to="/login" replace />
}

export default function AppRouter() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
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
