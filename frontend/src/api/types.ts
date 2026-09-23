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

export type ParseStatus = 'PENDING' | 'PARSED' | 'FAILED'

export type AcousticSensitivity = 'LOW' | 'MEDIUM' | 'HIGH'

/** The physical filming requirements the AI extracts from a scene's script. */
export interface SceneRequirements {
  settingType: string
  visualMood: string | null
  lightingNeeds: string | null
  timeOfDay: string | null
  acousticSensitivity: AcousticSensitivity | null
  estimatedCastAndCrewSize: number | null
}

export interface Scene {
  id: string
  projectId: string
  sceneNumber: number | null
  title: string
  sourceText: string
  /** ISO dates (yyyy-mm-dd). */
  shootDateStart: string | null
  shootDateEnd: string | null
  parseStatus: ParseStatus
  /** Null until the scene has been parsed. */
  requirements: SceneRequirements | null
  parsedAt: string | null
  createdAt: string
  updatedAt: string
}

/** Creates a scene, or fully replaces one (PUT): nulls clear the optional fields. */
export interface SceneRequest {
  sceneNumber: number | null
  title: string
  sourceText: string
  shootDateStart: string | null
  shootDateEnd: string | null
}
