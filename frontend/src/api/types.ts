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
  sceneCount: number
  /** Scenes with a confirmed location. */
  confirmedSceneCount: number
  /** A picture of one of its venues (a confirmed one first), for its poster; null while none has one. */
  posterImageUrl: string | null
  /** The user's role on the project: what they may do there. */
  role: ProjectRole
  createdAt: string
  updatedAt: string
}

/** What a member may do, weakest first: VIEWER reads, EDITOR changes the work, OWNER also runs the project and its crew. */
export type ProjectRole = 'VIEWER' | 'EDITOR' | 'OWNER'

export interface Member {
  userId: string
  displayName: string
  email: string
  role: ProjectRole
  joinedAt: string
}

export interface Invite {
  id: string
  email: string
  role: ProjectRole
  createdAt: string
  expiresAt: string
  /** The link's secret: only in the answer that made the invite. */
  token: string | null
}

export interface Crew {
  members: Member[]
  /** Open invites; only the owner sees them. */
  invites: Invite[]
}

/** Adding someone: an account that exists joins (`member`); anyone else gets an invite to pass on (`invite`). */
export interface AddMemberResult {
  member: Member | null
  invite: Invite | null
}

export interface InvitePreview {
  projectId: string
  projectTitle: string
  invitedBy: string | null
  email: string
  role: ProjectRole
  expiresAt: string
  state: 'OPEN' | 'ACCEPTED' | 'EXPIRED'
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

/** A project's shoot as a calendar: the scenes by the day their shoot starts, and where each will be shot. */
export interface Schedule {
  /** Earliest first; each day's scenes in script order. */
  days: { date: string; scenes: ScheduledScene[] }[]
  /** Scenes without shoot dates, in script order. */
  unscheduled: ScheduledScene[]
}

export interface ScheduledVenue {
  id: string
  name: string
  address: string | null
  latitude: number | null
  longitude: number | null
  /** Who to call there on the day, when the user has recorded it. */
  contactName: string | null
  contactPhone: string | null
  /** The light and weather there on the scene's day; null until the venue's logistics cover that day. */
  day: DayConditions | null
}

/** One day at a venue, as a call sheet gives it; times are the venue's own clock ("07:04"). */
export interface DayConditions {
  sunrise: string | null
  sunset: string | null
  weather: string | null
  temperatureMinC: number | null
  temperatureMaxC: number | null
  /** What the weather means for the shoot ("Rain likely: plan cover ..."). */
  warnings: string[]
}

export interface ScheduledScene {
  id: string
  sceneNumber: number | null
  title: string
  shootDateStart: string | null
  shootDateEnd: string | null
  /** Null until the scene has been analysed. */
  settingType: string | null
  timeOfDay: string | null
  /** The speaking parts, from the script. */
  characters: string[]
  /** The scene's confirmed locations; empty while none is confirmed. */
  venues: ScheduledVenue[]
  /** How many candidate locations the scene has in all. */
  candidates: number
}

/** What one run over a project's confirmed venues without logistics did. */
export interface BatchLogisticsResult {
  updated: number
  /** Venues that could not be worked out, e.g. not found on the map. */
  failed: number
  /** Confirmed venues still without logistics, the failed ones included. */
  remaining: number
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
  /** The speaking parts, read from the script's format, in the order they first speak. */
  characters: string[]
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
  /** Who to talk to at the venue, as the user entered it. */
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  /** What the venue asks for the shoot, in the user's words. */
  quote: string | null
  /** The picture the venue's web page offers; null before it is looked up (imageCheckedAt null) and when it has none. */
  imageUrl: string | null
  imageCheckedAt: string | null
  createdAt: string
  updatedAt: string
}

/** Full replacement: a null field clears it. */
export interface UpdateContactRequest {
  name: string | null
  email: string | null
  phone: string | null
  quote: string | null
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
  imageUrl: string | null
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
/** An outreach email as a row of a project-wide list: who was written to about which venue, and how far it got. */
export interface ProjectOutreach {
  id: string
  locationId: string
  locationName: string
  sceneId: string
  sceneNumber: number | null
  sceneTitle: string
  recipientName: string | null
  recipientEmail: string | null
  subject: string
  tone: OutreachTone
  status: OutreachStatus
  sentAt: string | null
  createdAt: string
  updatedAt: string
}

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

/** A call sheet as the crew sees it through a shared link. */
export type ActivityKind = 'VENUE' | 'SCOUTING' | 'OUTREACH' | 'CREW' | 'SCHEDULE' | 'COMMENT'

export type ActivityVerb =
  | 'VENUE_STATUS_CHANGED'
  | 'DIRECTOR_CALLED'
  | 'SCOUTED'
  | 'OUTREACH_STATUS_CHANGED'
  | 'MEMBER_JOINED'
  | 'MEMBER_LEFT'
  | 'MEMBER_REMOVED'
  | 'ROLE_CHANGED'
  | 'OWNERSHIP_TRANSFERRED'
  | 'SHOOT_DATES_CHANGED'
  | 'COMMENTED'

/** A line of a project's activity log; `payload` holds the facts the line needs, which differ by verb. */
export interface Activity {
  id: string
  kind: ActivityKind
  verb: ActivityVerb
  targetType: 'PROJECT' | 'SCENE' | 'LOCATION' | 'OUTREACH_DRAFT' | 'MEMBER' | 'COMMENT'
  targetId: string | null
  actorId: string | null
  /** As it was at the time; a guest's typed name when `payload.guest`. */
  actorName: string | null
  payload: Record<string, string | number | boolean | null>
  createdAt: string
}

/** A comment on a venue; `guest` when it came through a director link, under the name the guest typed. */
export interface VenueComment {
  id: string
  locationId: string
  parentId: string | null
  /** Null for a guest, or for a member whose account is gone (then `authorName` is null too). */
  authorId: string | null
  authorName: string | null
  guest: boolean
  body: string
  mentions: { userId: string; displayName: string }[]
  edited: boolean
  createdAt: string
  updatedAt: string
  /** On a thread's first comment: every reply, oldest first. */
  replies: VenueComment[]
}

export interface CommentRequest {
  body: string
  parentId?: string | null
  mentions: string[]
}

/** A guest's call on a venue through a director link. */
export type DirectorVerdict = 'APPROVE' | 'MAYBE' | 'NO'

/** What a director link is for: the whole project, or one scene. */
export interface DirectorScope {
  kind: 'project' | 'scene'
  id: string
}

export interface DirectorLink {
  token: string
  /** Null for a link to the whole project. */
  sceneId: string | null
  /** Whether it also shows private notes, quotes and the reasons behind fit scores. */
  showPrivate: boolean
  createdAt: string
}

/** A guest's call on a venue, under the name they typed: one per name per venue, the latest kept. */
export interface DirectorCall {
  id: string
  locationId: string
  guestName: string
  verdict: DirectorVerdict
  comment: string | null
  createdAt: string
  updatedAt: string
}

export interface DirectorCallRequest {
  guestName: string
  verdict: DirectorVerdict
  comment?: string | null
}

/** A venue as a director link shows it; `fitReason`, `notes` and `quote` are null unless the link shows them. */
export interface ShortlistVenue {
  id: string
  sceneId: string
  sceneTitle: string
  name: string
  address: string | null
  latitude: number | null
  longitude: number | null
  imageUrl: string | null
  fitScore: number | null
  bookingFriction: BookingFriction | null
  frictionNote: string | null
  warnings: string[]
  status: LocationStatus
  fitReason: string | null
  notes: string | null
  quote: string | null
  responses: DirectorCall[]
}

export interface PublicShortlist {
  projectTitle: string
  /** Set when the link is for one scene. */
  sceneTitle: string | null
  sharedBy: string | null
  showPrivate: boolean
  venues: Page<ShortlistVenue>
}

export interface PublicCallSheet {
  projectTitle: string
  locationArea: string | null
  preparedBy: string
  schedule: Schedule
}
