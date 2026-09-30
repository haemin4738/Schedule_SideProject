import { logoutSession } from '@/auth/logout'

export default function LogoutButton() {
  return (
    <button
      type="button"
      onClick={() => void logoutSession()}
      className="text-sm text-gray-500 hover:text-gray-800 hover:underline"
    >
      로그아웃
    </button>
  )
}
