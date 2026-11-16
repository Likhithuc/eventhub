import { http, setSession } from './http'
import type { AuthResponse, User } from './types'

export interface RegisterInput {
  name: string
  email: string
  password: string
}

export interface LoginInput {
  email: string
  password: string
}

export async function register(input: RegisterInput): Promise<AuthResponse> {
  const session = await http<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: input })
  setSession(session)
  return session
}

export async function login(input: LoginInput): Promise<AuthResponse> {
  const session = await http<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: input })
  setSession(session)
  return session
}

export async function logout(): Promise<void> {
  try {
    await http<void>('/api/v1/auth/logout', { method: 'POST' })
  } finally {
    setSession(null)
  }
}

export function getMe(): Promise<User> {
  return http<User>('/api/v1/users/me')
}
