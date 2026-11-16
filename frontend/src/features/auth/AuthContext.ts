import { createContext } from 'react'
import type { LoginInput, RegisterInput } from '../../api/authApi'
import type { Role, User } from '../../api/types'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  user: User | null
  hasRole: (...roles: Role[]) => boolean
  login: (input: LoginInput) => Promise<User>
  register: (input: RegisterInput) => Promise<User>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
