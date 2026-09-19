import client from './client'

export const login = (email: string, password: string) =>
  client.post<{ success: boolean; data: { accessToken: string; refreshToken: string } }>(
    '/api/auth/login',
    { email, password },
  )

export const signup = (email: string, password: string, name: string) =>
  client.post<{ success: boolean; data: { accessToken: string; refreshToken: string } }>(
    '/api/auth/signup',
    { email, password, name },
  )

export const logout = () => client.post('/api/auth/logout')
