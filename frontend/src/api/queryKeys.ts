import type { LocationStatus, OutreachFilter, ProjectStatus } from './types'

/**
 * React Query cache keys, one place so invalidation matches what was cached. A `...List` key covers every cached
 * page of that list (invalidate or update through it); a `...Page` key is one page.
 */
export const queryKeys = {
  projects: ['projects'] as const,
  projectPage: (status: ProjectStatus, page: number) => ['projects', 'list', status, page] as const,
  project: (id: string) => ['projects', 'detail', id] as const,
  sceneList: (projectId: string) => ['scenes', 'list', projectId] as const,
  scenePage: (projectId: string, page: number, search = '') => ['scenes', 'list', projectId, page, search] as const,
  scene: (id: string) => ['scenes', 'detail', id] as const,
  locationList: (sceneId: string) => ['locations', 'list', sceneId] as const,
  locationPage: (sceneId: string, page: number) => ['locations', 'list', sceneId, page] as const,
  locationTop: (sceneId: string) => ['locations', 'top', sceneId] as const,
  location: (id: string) => ['locations', 'detail', id] as const,
  projectLocationList: (projectId: string) => ['locations', 'project', projectId] as const,
  projectLocationPage: (projectId: string, status: LocationStatus | null, page: number) =>
    ['locations', 'project', projectId, status, page] as const,
  projectProgress: (projectId: string) => ['projects', 'progress', projectId] as const,
  projectSchedule: (projectId: string) => ['projects', 'schedule', projectId] as const,
  projectMoves: (projectId: string) => ['projects', 'moves', projectId] as const,
  callSheetLink: (projectId: string) => ['projects', 'call-sheet-link', projectId] as const,
  activity: (projectId: string, kind: string | null, page: number) => ['projects', 'activity', projectId, kind, page] as const,
  scoutFilters: (projectId: string) => ['projects', 'scout-filters', projectId] as const,
  projectSettings: (projectId: string) => ['projects', 'settings', projectId] as const,
  crew: (projectId: string) => ['projects', 'crew', projectId] as const,
  invite: (token: string) => ['invites', token] as const,
  publicCallSheet: (token: string) => ['public', 'call-sheet', token] as const,
  publicShortlist: (token: string, page: number) => ['public', 'shortlist', token, page] as const,
  photos: (locationId: string) => ['photos', locationId] as const,
  availability: (locationId: string) => ['availability', locationId] as const,
  covers: (sceneId: string) => ['covers', sceneId] as const,
  alertList: ['alerts', 'list'] as const,
  calendarLink: ['account', 'calendar-link'] as const,
  alertCount: ['alerts', 'count'] as const,
  agreements: (locationId: string) => ['agreements', locationId] as const,
  permit: (locationId: string, latitude: number | null, longitude: number | null) => ['permit', locationId, latitude, longitude] as const,
  libraryList: ['library', 'list'] as const,
  libraryPage: (search: string, tag: string | null, page: number) => ['library', 'list', search, tag, page] as const,
  libraryTags: ['library', 'tags'] as const,
  commentList: (locationId: string) => ['comments', locationId] as const,
  commentPage: (locationId: string, page: number) => ['comments', locationId, page] as const,
  directorLink: (kind: 'project' | 'scene', id: string) => ['director-link', kind, id] as const,
  directorCalls: (kind: 'location' | 'scene', id: string) => ['director-calls', kind, id] as const,
  outreachList: (locationId: string) => ['outreach', 'list', locationId] as const,
  outreachPage: (locationId: string, page: number) => ['outreach', 'list', locationId, page] as const,
  projectOutreachList: (projectId: string) => ['outreach', 'project', projectId] as const,
  projectOutreachPage: (projectId: string, status: OutreachFilter | null, page: number) =>
    ['outreach', 'project', projectId, status, page] as const,
  replies: (draftId: string) => ['outreach', 'replies', draftId] as const,
  videos: (locationId: string) => ['videos', locationId] as const,
}
