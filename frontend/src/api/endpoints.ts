import { ApiError, request, type Credentials } from './client'
import type {
  CreateLocationRequest,
  CreateProjectRequest,
  GenerateOutreachRequest,
  Location,
  LogisticsReport,
  OutreachDraft,
  Project,
  ProjectStatus,
  RegisterRequest,
  Scene,
  SceneRequest,
  ScoutingResult,
  UpdateCoordinatesRequest,
  UpdateLocationRequest,
  UpdateOutreachRequest,
  UpdateProjectRequest,
  User,
} from './types'

export const authApi = {
  register: (body: RegisterRequest) => request<User>('/api/auth/register', { method: 'POST', body }),
  me: (credentials: Credentials) => request<User>('/api/auth/me', { credentials }),
}

/**
 * The API calls that need a login, bound to one user's credentials. `onUnauthorized` runs when the server
 * rejects them (the password was changed elsewhere, say), so the app can send the user back to the login page.
 */
export function createApi(credentials: Credentials, onUnauthorized: () => void = () => {}) {
  const call = async <T>(path: string, options: Parameters<typeof request>[1] = {}) => {
    try {
      return await request<T>(path, { ...options, credentials })
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onUnauthorized()
      throw e
    }
  }

  return {
    projects: {
      list: (status?: ProjectStatus) => call<Project[]>(`/api/projects${status ? `?status=${status}` : ''}`),
      get: (id: string) => call<Project>(`/api/projects/${encodeURIComponent(id)}`),
      create: (body: CreateProjectRequest) => call<Project>('/api/projects', { method: 'POST', body }),
      update: (id: string, body: UpdateProjectRequest) =>
        call<Project>(`/api/projects/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/projects/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    },
    scenes: {
      list: (projectId: string) => call<Scene[]>(`/api/projects/${encodeURIComponent(projectId)}/scenes`),
      get: (id: string) => call<Scene>(`/api/scenes/${encodeURIComponent(id)}`),
      create: (projectId: string, body: SceneRequest) =>
        call<Scene>(`/api/projects/${encodeURIComponent(projectId)}/scenes`, { method: 'POST', body }),
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
      list: (sceneId: string) => call<Location[]>(`/api/scenes/${encodeURIComponent(sceneId)}/locations`),
      get: (id: string) => call<Location>(`/api/locations/${encodeURIComponent(id)}`),
      /** Adds a venue by hand; 409 when the same source URL is already saved for the scene. */
      create: (sceneId: string, body: CreateLocationRequest) =>
        call<Location>(`/api/scenes/${encodeURIComponent(sceneId)}/locations`, { method: 'POST', body }),
      update: (id: string, body: UpdateLocationRequest) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/locations/${encodeURIComponent(id)}`, { method: 'DELETE' }),
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
      list: (locationId: string) => call<OutreachDraft[]>(`/api/locations/${encodeURIComponent(locationId)}/outreach-drafts`),
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
