// Mirrors the backend DTOs in com.cinescout.dto. Timestamps are ISO-8601 strings.

export type UserRole = 'USER' | 'ADMIN'

/** One page of a list. `page` is zero-based; a page past the end is empty but still has the totals. */
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  /** 0 when the list is empty. */
  totalPages: number
}

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

export interface LoginRequest {
  email: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
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

/** What one run over a project's unanalysed scenes did. */
export interface BatchParseResult {
  parsed: number
  /** Scenes the AI could not make sense of; they are marked FAILED. */
  failed: number
  /** Scenes still waiting: a run takes a limited number. Run again to go on. */
  remaining: number
}

/** The scenes found in a pasted script: what an import would add (a preview) or has added. */
export interface ScriptImport {
  /** In script order. */
  scenes: ImportedScene[]
  /** False when the scenes were numbered on from the project's last scene instead of as the script numbers them. */
  scriptNumbersKept: boolean
}

export interface ImportedScene {
  /** Null in a preview. */
  id: string | null
  sceneNumber: number | null
  title: string
  characters: number
  /** The scene was longer than a scene may be and its end was cut off. */
  truncated: boolean
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
  /** The cached logistics report, null until it has been worked out (and again after the coordinates change). */
  logistics: LogisticsReport | null
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

/** A venue the user found themselves. Coordinates are given together or not at all. */
/** A candidate location as a row of a project-wide list: the essentials, and the scene it is for. */
export interface ProjectLocation {
  id: string
  sceneId: string
  sceneNumber: number | null
  sceneTitle: string
  name: string
  address: string | null
  latitude: number | null
  longitude: number | null
  sourceUrl: string | null
  fitScore: number | null
  fitReason: string | null
  bookingFriction: BookingFriction | null
  status: LocationStatus
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** How far a project's scouting has come. */
export interface ProjectProgress {
  scenes: number
  /** Scenes with at least one candidate location. */
  scenesWithLocations: number
  /** Scenes with a confirmed location. */
  scenesConfirmed: number
  locations: number
  locationsByStatus: Record<LocationStatus, number>
}

export interface CreateLocationRequest {
  name: string
  address: string | null
  latitude: number | null
  longitude: number | null
  sourceUrl: string | null
  notes: string | null
}

/** Decimal degrees; the server keeps six decimal places. */
export interface UpdateCoordinatesRequest {
  latitude: number
  longitude: number
}

export type OutreachTone = 'PROFESSIONAL' | 'FRIENDLY' | 'CONCISE'

/** What the user reports: the API never sends email itself. */
export type OutreachStatus = 'DRAFT' | 'SENT' | 'REPLIED'

/** An email to a venue's owner, written by the AI for the user to edit and send themselves. */
export interface OutreachDraft {
  id: string
  locationId: string
  recipientName: string | null
  recipientEmail: string | null
  subject: string
  body: string
  tone: OutreachTone
  /** The model that wrote it. */
  generatedBy: string | null
  status: OutreachStatus
  /** Set by the server when the status leaves DRAFT, cleared when it goes back. */
  sentAt: string | null
  createdAt: string
  updatedAt: string
}

/** All optional; the tone defaults to PROFESSIONAL. */
export interface GenerateOutreachRequest {
  tone: OutreachTone
  recipientName: string | null
  recipientEmail: string | null
  /** Free text for the AI to work in, e.g. "we can shoot on a weekday". */
  additionalContext: string | null
}

/** Full replacement (PUT): null recipient fields clear them. */
export interface UpdateOutreachRequest {
  subject: string
  body: string
  tone: OutreachTone
  status: OutreachStatus
  recipientName: string | null
  recipientEmail: string | null
}

/** What one scouting run saved for a scene. */
export interface ScoutingResult {
  /** The new candidate locations, best fit first. */
  added: Location[]
  /** Venues found again that were already saved; left untouched. */
  alreadySaved: number
  /** Venues found but dropped because the AI could not assess them. */
  unassessed: number
  /** Search results left out because they were not about one venue (a directory, a "best of" list, an article). */
  notVenues: number
  /** Venues left out because the AI found them unusable for the scene, e.g. in another city. */
  unsuitable: number
}

// The logistics report (com.cinescout.logistics.LogisticsReport). Local times are ISO-8601 with the
// location's own UTC offset, e.g. "2026-09-28T06:49:00-04:00".

export type SectionStatus = 'OK' | 'PARTIAL' | 'UNAVAILABLE'
export type NoiseLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type SceneLight = 'DAWN' | 'DUSK' | 'GOLDEN_HOUR' | 'BLUE_HOUR' | 'NIGHT' | 'DAY'
export type WeatherBasis = 'FORECAST' | 'RECORDED' | 'PAST_YEAR'
export type PlaceKind =
  | 'HOSPITAL' | 'PHARMACY' | 'PARKING' | 'FOOD' | 'TOILETS' | 'FUEL' | 'LODGING' | 'HARDWARE' | 'GROCERY'
  | 'AIRPORT' | 'HELIPORT' | 'STADIUM' | 'RAILWAY' | 'EMERGENCY_STATION' | 'CONSTRUCTION' | 'MAJOR_ROAD'
  | 'SCHOOL' | 'NIGHTLIFE' | 'PLACE_OF_WORSHIP'

export interface TimeWindow {
  start: string
  end: string
}

export interface SolarDay {
  date: string
  /** Null when the sun does not rise (or set) that day. */
  sunrise: string | null
  sunset: string | null
  solarNoon: string
  daylightMinutes: number
  goldenHours: TimeWindow[]
  blueHours: TimeWindow[]
  /** The windows matching the scene's time of day; empty if unknown or it never occurs. */
  sceneWindows: TimeWindow[]
}

export interface WeatherDay {
  date: string
  basis: WeatherBasis
  /** The shoot date, or the same date in an earlier year when the basis is PAST_YEAR. */
  referenceDate: string
  summary: string | null
  temperatureMaxC: number | null
  temperatureMinC: number | null
  precipitationMm: number | null
  precipitationProbabilityPercent: number | null
  windSpeedMaxKmh: number | null
  windGustsMaxKmh: number | null
  cloudCoverPercent: number | null
  warnings: string[]
}

export interface NoiseSource {
  kind: PlaceKind
  name: string | null
  distanceMeters: number
  level: NoiseLevel
  advice: string
}

export interface NearbyService {
  kind: PlaceKind
  name: string | null
  distanceMeters: number
  latitude: number | null
  longitude: number | null
}

export interface LogisticsReport {
  version: number
  generatedAt: string
  position: { latitude: number; longitude: number; geocoded: boolean }
  /** The IANA zone of every local time in the report. */
  timeZone: string
  /** `assumed`: the scene has no dates, so the coming week was used; `truncated`: only the window's start is covered. */
  shootWindow: { start: string; end: string; assumed: boolean; truncated: boolean }
  solar: { timeOfDay: string | null; sceneLight: SceneLight | null; days: SolarDay[] }
  weather: { status: SectionStatus; message: string | null; days: WeatherDay[] }
  environment: {
    status: SectionStatus
    message: string | null
    acousticSensitivity: AcousticSensitivity | null
    noiseRisk: NoiseLevel | null
    /** Loudest and nearest first. */
    noiseSources: NoiseSource[]
    /** The nearest few of each kind, grouped by kind. */
    nearbyServices: NearbyService[]
  }
  notes: string[]
  /** Credits the data licences require to be shown with the data. */
  attribution: string[]
}

/** A video about a venue. Links are built from the id (see lib/video.ts), never taken from elsewhere. */
export interface Video {
  id: string
  title: string
  channel: string
  publishedAt: string | null
}

export interface LocationVideos {
  /** What was searched for: the venue's name and where it is. */
  query: string
  /** When the search ran; the server caches it for a week. */
  fetchedAt: string
  videos: Video[]
}
