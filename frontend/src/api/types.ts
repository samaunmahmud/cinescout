// Mirrors the backend DTOs in com.cinescout.dto. Timestamps are ISO-8601 strings.

export type UserRole = 'USER' | 'ADMIN'

export interface User {
  id: string
  email: string
  displayName: string
  role: UserRole
  createdAt: string
}

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'

export interface Project {
  id: string
  title: string
  description: string | null
  locationArea: string | null
  status: ProjectStatus
  createdAt: string
  updatedAt: string
}

export interface CreateProjectRequest {
  title: string
  description?: string | null
  locationArea?: string | null
}

/** Full replacement: a null description or location area clears it. */
export interface UpdateProjectRequest {
  title: string
  description: string | null
  locationArea: string | null
  status: ProjectStatus
}
