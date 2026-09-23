import { useAuthStore } from '@/store/authStore'
import { Navigate, Route, BrowserRouter as Router, Routes } from 'react-router-dom'
import CalendarPage from '@/pages/CalendarPage'
import JobApplicationsPage from '@/pages/JobApplicationsPage'
import LoginPage from '@/pages/LoginPage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  return accessToken ? <>{children}</> : <Navigate to="/login" replace />
}

export default function AppRouter() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
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
      </Routes>
    </Router>
  )
}
