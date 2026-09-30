import { createContext, useContext } from 'react'
import type { Api } from '../api/endpoints'
import type { RegisterRequest, User } from '../api/types'

export interface Session {
  user: User
  api: Api
}

export interface AuthState {
  session: Session | null
  /** True until the server has said whether there is a session already. */
  checking: boolean
  logIn: (email: string, password: string) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logOut: () => Promise<void>
  /** Puts a changed account (a new display name, say) into the session. */
  updateUser: (user: User) => void
  /** Forgets the login here without asking the server: for when the account or session is already gone. */
  forget: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}

/** The logged-in session. Only for pages behind <RequireAuth>, which guarantees there is one. */
export function useSession(): Session {
  const { session } = useAuth()
  if (!session) throw new Error('useSession needs a logged-in user')
  return session
}
