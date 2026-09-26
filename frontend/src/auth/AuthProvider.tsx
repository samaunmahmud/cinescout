import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, createApi } from '../api/endpoints'
import type { RegisterRequest, User } from '../api/types'
import { AuthContext, type AuthState, type Session } from './context'

/**
 * Holds the login. The server keeps the session in an HttpOnly cookie that scripts cannot read, so nothing
 * secret is stored here; on load the app asks the server whose session it is, which is what lets a reload
 * keep the user logged in.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(null)
  const [checking, setChecking] = useState(true)

  // Forgets the login here only: for when the server has already ended the session.
  const forget = useCallback(() => {
    setSession(null)
    // Drop every cached response so the next user never sees the previous one's data.
    queryClient.clear()
  }, [queryClient])

  const start = useCallback(
    (user: User) => {
      queryClient.clear()
      setSession({ user, api: createApi(forget) })
    },
    [queryClient, forget],
  )

  useEffect(() => {
    let current = true
    authApi
      .me()
      .then((user) => current && start(user))
      // No session, or the server is unreachable: either way the login page is where to go.
      .catch(() => {})
      .finally(() => current && setChecking(false))
    return () => {
      current = false
    }
  }, [start])

  const logIn = useCallback(
    async (email: string, password: string) => start(await authApi.logIn({ email: email.trim(), password })),
    [start],
  )

  const register = useCallback(
    async (request: RegisterRequest) => {
      await authApi.register({ ...request, email: request.email.trim() })
      await logIn(request.email, request.password)
    },
    [logIn],
  )

  const logOut = useCallback(async () => {
    // Waited for, so a quick log-in afterwards cannot have its new cookie cleared by this response.
    await authApi.logOut().catch(() => {})
    forget()
  }, [forget])

  const value = useMemo<AuthState>(() => ({ session, checking, logIn, register, logOut }), [session, checking, logIn, register, logOut])
  return <AuthContext value={value}>{children}</AuthContext>
}
