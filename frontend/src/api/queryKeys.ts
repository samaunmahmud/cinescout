import type { ProjectStatus } from './types'

/** React Query cache keys, one place so invalidation matches what was cached. */
export const queryKeys = {
  projects: ['projects'] as const,
  projectList: (status: ProjectStatus) => ['projects', 'list', status] as const,
  project: (id: string) => ['projects', 'detail', id] as const,
}
