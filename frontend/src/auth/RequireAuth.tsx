import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './context'
import { SessionCheck } from './SessionCheck'

/** Sends a logged-out visitor to the login page, remembering where they were going. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { session, checking } = useAuth()
  const location = useLocation()
  if (checking) return <SessionCheck />
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return children
}
