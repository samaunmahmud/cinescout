import type { ReactNode } from 'react'
import type { ProjectRole } from '../api/types'
import { RoleContext } from './projectRole'

/** Gives the pieces of a project's page the user's role on it (see `useCanEdit`). */
export function ProjectRoleProvider({ role, children }: { role: ProjectRole | undefined; children: ReactNode }) {
  return <RoleContext.Provider value={role}>{children}</RoleContext.Provider>
}
