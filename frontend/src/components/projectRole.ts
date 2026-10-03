import { useQuery } from '@tanstack/react-query'
import { createContext, useContext } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { ProjectRole } from '../api/types'
import { useSession } from '../auth/context'

/*
 * The user's role on the project a page belongs to, so the page offers only what they may do. The server checks
 * every request on its own; this only keeps a viewer from being offered buttons that would be refused. While the
 * role is unknown (still loading), the page shows its controls as before.
 */

export const RoleContext = createContext<ProjectRole | undefined>(undefined)

/** Whether the user may change the project's work: everyone but a viewer. */
export function useCanEdit(): boolean {
  return useContext(RoleContext) !== 'VIEWER'
}

/** The role of the user on a project, from the project itself (cached with it). */
export function useProjectRole(projectId: string | undefined): ProjectRole | undefined {
  const { api } = useSession()
  const project = useQuery({
    queryKey: queryKeys.project(projectId ?? ''),
    queryFn: () => api.projects.get(projectId!),
    enabled: !!projectId,
  })
  return project.data?.role
}
