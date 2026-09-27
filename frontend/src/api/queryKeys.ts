import type { ProjectStatus } from './types'

/**
 * React Query cache keys, one place so invalidation matches what was cached. A `...List` key covers every cached
 * page of that list (invalidate or update through it); a `...Page` key is one page.
 */
export const queryKeys = {
  projects: ['projects'] as const,
  projectPage: (status: ProjectStatus, page: number) => ['projects', 'list', status, page] as const,
  project: (id: string) => ['projects', 'detail', id] as const,
  sceneList: (projectId: string) => ['scenes', 'list', projectId] as const,
  scenePage: (projectId: string, page: number) => ['scenes', 'list', projectId, page] as const,
  scene: (id: string) => ['scenes', 'detail', id] as const,
  locationList: (sceneId: string) => ['locations', 'list', sceneId] as const,
  locationPage: (sceneId: string, page: number) => ['locations', 'list', sceneId, page] as const,
  location: (id: string) => ['locations', 'detail', id] as const,
  outreachList: (locationId: string) => ['outreach', 'list', locationId] as const,
  outreachPage: (locationId: string, page: number) => ['outreach', 'list', locationId, page] as const,
  videos: (locationId: string) => ['videos', locationId] as const,
}
