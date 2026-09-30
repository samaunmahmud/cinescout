import { ApiError, request } from './client'
import type {
  CreateLocationRequest,
  CreateProjectRequest,
  GenerateOutreachRequest,
  LoginRequest,
  Location,
  LocationVideos,
  LogisticsReport,
  OutreachDraft,
  Page,
  Project,
  ProjectStatus,
  RegisterRequest,
  Scene,
  SceneRequest,
  ScoutingResult,
  ScriptImport,
  UpdateCoordinatesRequest,
  UpdateLocationRequest,
  UpdateOutreachRequest,
  UpdateProjectRequest,
  User,
} from './types'

/** Items per page for every list the app shows. */
export const PAGE_SIZE = 24

const pageQuery = (page: number) => `page=${page}&size=${PAGE_SIZE}`

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
  const call = async <T>(path: string, options: Parameters<typeof request>[1] = {}) => {
    try {
      return await request<T>(path, options)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onUnauthorized()
      throw e
    }
  }

  return {
    projects: {
      /** Newest first. */
      list: (status: ProjectStatus, page = 0) => call<Page<Project>>(`/api/projects?status=${status}&${pageQuery(page)}`),
      get: (id: string) => call<Project>(`/api/projects/${encodeURIComponent(id)}`),
      create: (body: CreateProjectRequest) => call<Project>('/api/projects', { method: 'POST', body }),
      update: (id: string, body: UpdateProjectRequest) =>
        call<Project>(`/api/projects/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/projects/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    },
    scenes: {
      /** In script order. */
      list: (projectId: string, page = 0) =>
        call<Page<Scene>>(`/api/projects/${encodeURIComponent(projectId)}/scenes?${pageQuery(page)}`),
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
      remove: (id: string) => call<void>(`/api/scenes/${encodeURIComponent(id)}`, { method: 'DELETE' }),
      /** Extracts the filming requirements with the AI. Takes seconds; answers 503 when scouting is not configured. */
      parse: (id: string) => call<Scene>(`/api/scenes/${encodeURIComponent(id)}/parse`, { method: 'POST' }),
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
      get: (id: string) => call<Location>(`/api/locations/${encodeURIComponent(id)}`),
      /** Adds a venue by hand; 409 when the same source URL is already saved for the scene. */
      create: (sceneId: string, body: CreateLocationRequest) =>
        call<Location>(`/api/scenes/${encodeURIComponent(sceneId)}/locations`, { method: 'POST', body }),
      update: (id: string, body: UpdateLocationRequest) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/locations/${encodeURIComponent(id)}`, { method: 'DELETE' }),
      /** Videos of the venue, searched on first request and cached by the server; 503 when not configured. */
      videos: (id: string) => call<LocationVideos>(`/api/locations/${encodeURIComponent(id)}/videos`),
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
