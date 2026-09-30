import { ApiError, download, request } from './client'
import type {
  BatchLogisticsResult,
  BatchParseResult,
  ChangePasswordRequest,
  CreateLocationRequest,
  CreateProjectRequest,
  GenerateOutreachRequest,
  LoginRequest,
  Location,
  LocationStatus,
  LocationVideos,
  LogisticsReport,
  OutreachDraft,
  OutreachStatus,
  Page,
  Project,
  ProjectLocation,
  ProjectOutreach,
  ProjectProgress,
  ProjectStatus,
  PublicCallSheet,
  RegisterRequest,
  Scene,
  Schedule,
  SceneRequest,
  ScoutingResult,
  ScriptImport,
  UpdateContactRequest,
  UpdateCoordinatesRequest,
  UpdateLocationRequest,
  UpdateOutreachRequest,
  UpdateProjectRequest,
  User,
} from './types'

/** Items per page for every list the app shows. */
export const PAGE_SIZE = 24

const pageQuery = (page: number) => `page=${page}&size=${PAGE_SIZE}`

/** What needs no login: a shared call sheet, whose token is the permission. */
export const publicApi = {
  callSheet: (token: string) => request<PublicCallSheet>(`/api/public/call-sheets/${encodeURIComponent(token)}`),
}

export const authApi = {
  register: (body: RegisterRequest) => request<User>('/api/auth/register', { method: 'POST', body }),
  /** Starts a session: the server sets an HttpOnly cookie the browser then sends by itself. */
  logIn: (body: LoginRequest) => request<User>('/api/auth/login', { method: 'POST', body }),
  logOut: () => request<void>('/api/auth/logout', { method: 'POST' }),
  /** The session's account; a 401 means there is no session (or it expired). */
  me: () => request<User>('/api/auth/me'),
}

/**
 * The API calls that need a login. `onUnauthorized` runs when the server rejects them (the session expired or
 * was ended elsewhere, say), so the app can send the user back to the login page.
 */
export function createApi(onUnauthorized: () => void = () => {}) {
  const guarded = async <T>(work: () => Promise<T>) => {
    try {
      return await work()
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onUnauthorized()
      throw e
    }
  }
  const call = <T>(path: string, options: Parameters<typeof request>[1] = {}) => guarded(() => request<T>(path, options))

  return {
    account: {
      /** The name outreach emails are signed with. */
      update: (displayName: string) => call<User>('/api/account', { method: 'PUT', body: { displayName } }),
      /** 400 on `currentPassword` when it is wrong. The account's other sessions are ended; this one stays. */
      changePassword: (body: ChangePasswordRequest) => call<void>('/api/account/password', { method: 'PUT', body }),
      /** Deletes the account and everything it owns; 400 on `password` when it is wrong. */
      remove: (password: string) => call<void>('/api/account/delete', { method: 'POST', body: { password } }),
    },
    projects: {
      /** Newest first. */
      list: (status: ProjectStatus, page = 0) => call<Page<Project>>(`/api/projects?status=${status}&${pageQuery(page)}`),
      get: (id: string) => call<Project>(`/api/projects/${encodeURIComponent(id)}`),
      create: (body: CreateProjectRequest) => call<Project>('/api/projects', { method: 'POST', body }),
      update: (id: string, body: UpdateProjectRequest) =>
        call<Project>(`/api/projects/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/projects/${encodeURIComponent(id)}`, { method: 'DELETE' }),
      /** Counts of the project's scenes and candidate locations, by how far each has come. */
      progress: (id: string) => call<ProjectProgress>(`/api/projects/${encodeURIComponent(id)}/progress`),
      /** Works out the logistics of the confirmed venues that have none, up to ten a call; the answer says how many are left. */
      refreshLogistics: (id: string) => call<BatchLogisticsResult>(`/api/projects/${encodeURIComponent(id)}/logistics`, { method: 'POST' }),
      /** The call sheet's shared link; 404 while it is not shared. */
      callSheetLink: (id: string) => call<{ token: string }>(`/api/projects/${encodeURIComponent(id)}/call-sheet-link`),
      /** A new shared link, replacing any earlier one. */
      shareCallSheet: (id: string) => call<{ token: string }>(`/api/projects/${encodeURIComponent(id)}/call-sheet-link`, { method: 'POST' }),
      stopSharingCallSheet: (id: string) => call<void>(`/api/projects/${encodeURIComponent(id)}/call-sheet-link`, { method: 'DELETE' }),
      /** The scenes by the day their shoot starts, each with its confirmed locations. */
      schedule: (id: string) => call<Schedule>(`/api/projects/${encodeURIComponent(id)}/schedule`),
    },
    scenes: {
      /** In script order; `search` keeps the scenes whose title, script or setting contains it. */
      list: (projectId: string, page = 0, search = '') =>
        call<Page<Scene>>(
          `/api/projects/${encodeURIComponent(projectId)}/scenes?${search ? `q=${encodeURIComponent(search)}&` : ''}${pageQuery(page)}`,
        ),
      get: (id: string) => call<Scene>(`/api/scenes/${encodeURIComponent(id)}`),
      create: (projectId: string, body: SceneRequest) =>
        call<Scene>(`/api/projects/${encodeURIComponent(projectId)}/scenes`, { method: 'POST', body }),
      /** The scenes a pasted script would be cut into, at its INT./EXT. headings; nothing is saved. */
      previewImport: (projectId: string, script: string) =>
        call<ScriptImport>(`/api/projects/${encodeURIComponent(projectId)}/scenes/import/preview`, { method: 'POST', body: { script } }),
      /** Adds one scene per heading, all or none; 400 (on `script`) when the script has no scene headings. */
      importScript: (projectId: string, script: string) =>
        call<ScriptImport>(`/api/projects/${encodeURIComponent(projectId)}/scenes/import`, { method: 'POST', body: { script } }),
      update: (id: string, body: SceneRequest) => call<Scene>(`/api/scenes/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      /** Sets the shoot window and nothing else; a null date clears it. */
      reschedule: (id: string, body: { shootDateStart: string | null; shootDateEnd: string | null }) =>
        call<Scene>(`/api/scenes/${encodeURIComponent(id)}/shoot-dates`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/scenes/${encodeURIComponent(id)}`, { method: 'DELETE' }),
      /** Extracts the filming requirements with the AI. Takes seconds; answers 503 when scouting is not configured. */
      parse: (id: string) => call<Scene>(`/api/scenes/${encodeURIComponent(id)}/parse`, { method: 'POST' }),
      /**
       * Analyses the project's scenes that are still waiting for it, a limited number a run; the answer says how
       * many are left. Takes up to a minute; 429 or 503 only when no scene could be analysed at all.
       */
      parseAll: (projectId: string) =>
        call<BatchParseResult>(`/api/projects/${encodeURIComponent(projectId)}/scenes/parse`, { method: 'POST' }),
      /**
       * Finds and assesses venues in the project's location area and saves the new ones; parses the scene first
       * if needed. Takes up to minutes; 409 when the project has no location area, 503 when not configured.
       */
      scout: (id: string) => call<ScoutingResult>(`/api/scenes/${encodeURIComponent(id)}/scout`, { method: 'POST' }),
    },
    locations: {
      /** Best fit first; venues added by hand (no score) last. */
      list: (sceneId: string, page = 0) =>
        call<Page<Location>>(`/api/scenes/${encodeURIComponent(sceneId)}/locations?${pageQuery(page)}`),
      /** The scene's best 100 candidates in one go, best fit first, for comparing them side by side. */
      listTop: (sceneId: string) => call<Page<Location>>(`/api/scenes/${encodeURIComponent(sceneId)}/locations?page=0&size=100`),
      /** Across the project's scenes, in script order and best fit first within a scene; `status` null means all. */
      listForProject: (projectId: string, status: LocationStatus | null, page = 0) =>
        call<Page<ProjectLocation>>(
          `/api/projects/${encodeURIComponent(projectId)}/locations?${status ? `status=${status}&` : ''}${pageQuery(page)}`,
        ),
      /** The whole project-wide list (or one status of it) as a CSV file. */
      exportForProject: (projectId: string, status: LocationStatus | null) =>
        guarded(() => download(`/api/projects/${encodeURIComponent(projectId)}/locations/export${status ? `?status=${status}` : ''}`, 'text/csv')),
      get: (id: string) => call<Location>(`/api/locations/${encodeURIComponent(id)}`),
      /** Adds a venue by hand; 409 when the same source URL is already saved for the scene. */
      create: (sceneId: string, body: CreateLocationRequest) =>
        call<Location>(`/api/scenes/${encodeURIComponent(sceneId)}/locations`, { method: 'POST', body }),
      update: (id: string, body: UpdateLocationRequest) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/locations/${encodeURIComponent(id)}`, { method: 'DELETE' }),
      /** Videos of the venue, searched on first request and cached by the server; 503 when not configured. */
      videos: (id: string) => call<LocationVideos>(`/api/locations/${encodeURIComponent(id)}/videos`),
      /** Who to talk to at the venue: a full replacement of name, email and phone. */
      updateContact: (id: string, body: UpdateContactRequest) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}/contact`, { method: 'PUT', body }),
      /** Looks up the picture the venue's page offers, once; later calls return the location as it is. */
      lookUpImage: (id: string) => call<Location>(`/api/locations/${encodeURIComponent(id)}/image`, { method: 'POST' }),
      /** Moves the pin; the server drops the cached logistics, which were for the old spot. */
      updateCoordinates: (id: string, body: UpdateCoordinatesRequest) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}/coordinates`, { method: 'PUT', body }),
      /**
       * Works the logistics out afresh and caches them on the location, geocoding it first if it has no
       * coordinates. Takes seconds (up to half a minute); 409 when the venue cannot be found on the map.
       */
      refreshLogistics: (id: string) =>
        call<LogisticsReport>(`/api/locations/${encodeURIComponent(id)}/logistics`, { method: 'POST' }),
    },
    outreach: {
      /** Every draft of the project, newest first, each with its venue and scene; `status` null means all. */
      listForProject: (projectId: string, status: OutreachStatus | null, page = 0) =>
        call<Page<ProjectOutreach>>(
          `/api/projects/${encodeURIComponent(projectId)}/outreach-drafts?${status ? `status=${status}&` : ''}${pageQuery(page)}`,
        ),
      /** Newest first. */
      list: (locationId: string, page = 0) =>
        call<Page<OutreachDraft>>(`/api/locations/${encodeURIComponent(locationId)}/outreach-drafts?${pageQuery(page)}`),
      /**
       * Has the AI write a new draft (each call adds one). Takes seconds; 503 when generation is not configured.
       * The AI sees the venue, the scene's requirements, the sender's name and the shoot dates, never the script.
       */
      generate: (locationId: string, body: GenerateOutreachRequest) =>
        call<OutreachDraft>(`/api/locations/${encodeURIComponent(locationId)}/outreach-drafts/generate`, { method: 'POST', body }),
      update: (id: string, body: UpdateOutreachRequest) =>
        call<OutreachDraft>(`/api/outreach-drafts/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/outreach-drafts/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    },
  }
}

export type Api = ReturnType<typeof createApi>
