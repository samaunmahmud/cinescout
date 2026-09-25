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

export type LocationStatus = 'SUGGESTED' | 'SHORTLISTED' | 'REJECTED' | 'CONTACTED' | 'CONFIRMED'

/** How hard the venue is likely to be to book: a public space, a business, or a private home or property. */
export type BookingFriction = 'PUBLIC' | 'COMMERCIAL' | 'PRIVATE'

/** A candidate venue for a scene, found by scouting (with an AI assessment) or added by hand (without one). */
export interface Location {
  id: string
  sceneId: string
  name: string
  address: string | null
  latitude: number | null
  longitude: number | null
  sourceUrl: string | null
  sourceProvider: string | null
  sourceExcerpt: string | null
  fitReason: string | null
  /** 0 (unusable) to 100 (ideal); null for venues added by hand. */
  fitScore: number | null
  bookingFriction: BookingFriction | null
  frictionNote: string | null
  footprintWarnings: string[]
  /** The cached logistics report, null until it has been worked out. */
  logistics: unknown
  logisticsFetchedAt: string | null
  status: LocationStatus
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** The user's own workflow fields (PUT, full replacement): a null note clears it. */
export interface UpdateLocationRequest {
  status: LocationStatus
  notes: string | null
}

/** What one scouting run saved for a scene. */
export interface ScoutingResult {
  /** The new candidate locations, best fit first. */
  added: Location[]
  /** Venues found again that were already saved; left untouched. */
  alreadySaved: number
  /** Venues found but dropped because the AI could not assess them. */
  unassessed: number
}
