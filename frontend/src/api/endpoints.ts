import { ApiError, request, type Credentials } from './client'
import type {
  CreateProjectRequest,
  Project,
  ProjectStatus,
  RegisterRequest,
  Scene,
  SceneRequest,
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
    },
  }
}

export type Api = ReturnType<typeof createApi>
