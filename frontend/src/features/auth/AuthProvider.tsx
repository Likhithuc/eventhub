import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../../api/authApi'
import { onSessionChange, refreshSession } from '../../api/http'
import type { Role, User } from '../../api/types'
import { AuthContext, type AuthContextValue, type AuthStatus } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<User | null>(null)

  useEffect(() => {
    // Keep React state in sync with every session change (login, refresh, expiry, logout).
    const unsubscribe = onSessionChange((session) => {
      setUser(session?.user ?? null)
      setStatus(session ? 'authenticated' : 'anonymous')
    })
    // Restore the session from the refresh cookie on page load.
    refreshSession().then((session) => {
      if (!session) setStatus((s) => (s === 'loading' ? 'anonymous' : s))
    })
    return unsubscribe
  }, [])

  const hasRole = useCallback((...roles: Role[]) => !!user && roles.some((r) => user.roles.includes(r)), [user])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      hasRole,
      login: async (input) => (await authApi.login(input)).user,
      register: async (input) => (await authApi.register(input)).user,
      logout: authApi.logout,
    }),
    [status, user, hasRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
