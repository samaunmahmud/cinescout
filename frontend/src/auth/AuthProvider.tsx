import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { authApi, createApi } from '../api/endpoints'
import type { RegisterRequest } from '../api/types'
import { AuthContext, type AuthState, type Session } from './context'

/**
 * Holds the login for the life of the page. The API uses HTTP Basic, so the "session" is the email and
 * password themselves: they are kept in memory only, never in localStorage or sessionStorage, which means a
 * reload asks for them again. Token authentication on the backend is what would lift that.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(null)

  const logOut = useCallback(() => {
    setSession(null)
    // Drop every cached response so the next user never sees the previous one's data.
    queryClient.clear()
  }, [queryClient])

  const logIn = useCallback(
    async (email: string, password: string) => {
      const credentials = { email: email.trim(), password }
      const user = await authApi.me(credentials)
      queryClient.clear()
      setSession({ user, api: createApi(credentials, logOut) })
    },
    [queryClient, logOut],
  )

  const register = useCallback(
    async (request: RegisterRequest) => {
      await authApi.register({ ...request, email: request.email.trim() })
      await logIn(request.email, request.password)
    },
    [logIn],
  )

  const value = useMemo<AuthState>(() => ({ session, logIn, register, logOut }), [session, logIn, register, logOut])
  return <AuthContext value={value}>{children}</AuthContext>
}
