import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      const refreshToken = localStorage.getItem('refreshToken')
      if (!refreshToken) return Promise.reject(error)

      const { data } = await axios.post(
        `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api/auth/refresh`,
        { refreshToken },
      )
      localStorage.setItem('accessToken', data.data.accessToken)
      original.headers.Authorization = `Bearer ${data.data.accessToken}`
      return client(original)
    }
    return Promise.reject(error)
  },
)

export default client
