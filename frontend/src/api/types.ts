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
  /** Sent emails left unanswered too long, waiting on a follow-up. */
  followUpCount: number
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
  /** What stands in the way, by date, problems first; always empty on a shared call sheet. */
  conflicts: ScheduleConflict[]
}

export type ConflictKind = 'UNAVAILABLE' | 'HOLD_EXPIRES' | 'DOUBLE_BOOKED' | 'PERMIT_LEAD_TIME'

export interface ScheduleConflict {
  kind: ConflictKind
  /** True when it must be sorted out; false for a warning (two scenes at one venue at once). */
  problem: boolean
  date: string
  sceneIds: string[]
  locationId: string
  venueName: string
  /** The conflict in plain English. */
  message: string
}

/** Who to ask about filming at a venue in a public place, and how far ahead. */
export interface PermitGuidance {
  /** FOUND: a listed office; FALLBACK: the UK but no listed area; the rest say why there is no office. */
  status: 'FOUND' | 'FALLBACK' | 'OUTSIDE_COVERAGE' | 'NEEDS_POSITION' | 'LOOKUP_FAILED'
  /** Whether the venue is marked as a public space. */
  applies: boolean
  /** The local authority area of the venue's position, as the map names it. */
  areaName: string | null
  office: PermitOffice | null
  /** When the guide's data was last checked (ISO date). */
  lastReviewed: string
  sources: { name: string; url: string }[]
  /** Where to correct the guide's data. */
  editUrl: string | null
}

export interface PermitOffice {
  area: string
  name: string
  contactUrl: string
  /** Working days ahead to apply, for the scene's crew size; null when not known. */
  leadTimeWorkingDays: number | null
  /** The source's own wording. */
  leadTimeText: string | null
  note: string | null
  checklist: string[]
  /** False for the fallback, "the local council". */
  listed: boolean
}

/** One version of a venue's location release (a PDF template). */
export interface Agreement {
  id: string
  locationId: string
  version: number
  sizeBytes: number
  createdByName: string | null
  createdAt: string
}

/** How far booking a venue for a day has got, or that it cannot be had. */
export type AvailabilityState = 'PENCILLED' | 'HELD' | 'CONFIRMED' | 'UNAVAILABLE'

export interface Availability {
  id: string
  locationId: string
  /** ISO date. */
  day: string
  state: AvailabilityState
  /** When a pencil or hold lapses; null for none. */
  holdExpiresOn: string | null
  note: string | null
  setByName: string | null
  updatedAt: string
}

/** Sets a venue's state on `from`, or on every day from `from` to `to` (at most 62 days). */
export interface AvailabilityRequest {
  from: string
  to: string | null
  state: AvailabilityState
  holdExpiresOn: string | null
  note: string | null
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
  /** The venue's state on the scene's (first) day; null when not recorded, and on a shared call sheet. */
  booking: { state: AvailabilityState; holdExpiresOn: string | null } | null
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
  /** The sun at the start, middle and end of the scene's time ("Sun from SW (225°), 18° high at 16:00"). */
  sun: { time: string | null; azimuth: number | null; elevation: number | null; compass: string | null; text: string | null }[]
}

export interface ScheduledScene {
  id: string
  sceneNumber: number | null
  title: string
  shootDateStart: string | null
  shootDateEnd: string | null
  /** When the crew is called and the scene wraps on its days ("07:30:00"); a wrap at or before the call is the next morning. */
  callTime: string | null
  wrapTime: string | null
  /** Null until the scene has been analysed. */
  settingType: string | null
  timeOfDay: string | null
  /** The speaking parts, from the script. */
  characters: string[]
  /** The scene's confirmed locations; empty while none is confirmed. */
  venues: ScheduledVenue[]
  /** How many candidate locations the scene has in all. */
  candidates: number
  /** The scene's backup venues, in the order they were added; one since confirmed is left out. */
  covers: ScheduledCover[]
}

/** A backup venue for a scheduled scene, with who to call there. */
export interface ScheduledCover {
  /** The cover set's id. */
  id: string
  locationId: string
  name: string
  address: string | null
  contactName: string | null
  contactPhone: string | null
  /** When to switch to it ("if rain > 60%"); null when not given. */
  trigger: string | null
}

/** A cover set: one of the scene's candidates kept as its backup venue. */
export interface CoverSet {
  id: string
  sceneId: string
  locationId: string
  venueName: string
  address: string | null
  status: LocationStatus
  trigger: string | null
  /** Null once that account is gone. */
  addedByName: string | null
  createdAt: string
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
  /** When the crew is called and the scene wraps on its days ("07:30:00"); null when not set. */
  callTime: string | null
  wrapTime: string | null
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
  /** Why the crew passed on it, while it is REJECTED; null when they did not say. */
  rejectionReason: string | null
  /** The recce photo shown as its picture (then `imageUrl` is that photo's short-lived link); null for the web page's picture. */
  coverPhotoId: string | null
  /** The tech recce checklist: answers by field name, each with who gave it and when. */
  recce: Record<string, RecceEntry>
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
  /** Why the crew passed on it; only kept with REJECTED, and left as it was when absent. */
  rejectionReason?: string | null
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
  /** When the email was found unanswered for too long; null while it waits on no follow-up. */
  followUpFlaggedAt: string | null
  /** The earlier email this one chases; null for a first email. */
  followUpOfId: string | null
  createdAt: string
  updatedAt: string
}

/** What the project's outreach list can be narrowed to: a status, or the emails waiting on a follow-up. */
export type OutreachFilter = OutreachStatus | 'FOLLOW_UP'

/** A project's working settings. */
export interface ProjectSettings {
  /** Days a sent email may go unanswered before it is flagged for a follow-up (1 to 60, default 5). */
  followUpDays: number
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
  /** This email's own reply address, while reply tracking is set up; null otherwise. */
  replyTo: string | null
  /** When the email was found unanswered for too long; null while it waits on no follow-up. */
  followUpFlaggedAt: string | null
  /** The earlier email this one chases; null for a first email. */
  followUpOfId: string | null
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
/** One answer on a venue's tech recce. */
export interface RecceEntry {
  value: string | number | boolean
  by: string | null
  byName: string | null
  at: string
}

/** Answers to change on a tech recce: a value per field name, null to clear one. */
export type RecceAnswers = Record<string, string | number | boolean | null>

/** A reply to an outreach email: one that came in by mail, or one a member pasted in. Never sent to the AI. */
export interface OutreachReply {
  id: string
  draftId: string
  source: 'INBOUND' | 'MANUAL'
  fromAddress: string | null
  fromName: string | null
  subject: string | null
  text: string | null
  receivedAt: string
  /** Who pasted it in. */
  recordedBy: string | null
}

export interface ManualReplyRequest {
  fromAddress?: string | null
  fromName?: string | null
  subject?: string | null
  text?: string | null
}

/** A recce photo; `url` and `thumbUrl` are short-lived links (refetch the list for fresh ones). */
export interface Photo {
  id: string
  locationId: string
  url: string
  thumbUrl: string
  width: number
  height: number
  /** Where it was taken, when the photo said. */
  latitude: number | null
  longitude: number | null
  uploadedBy: string | null
  /** Whether the venue's polaroid shows it. */
  cover: boolean
  createdAt: string
}

/** A photo just taken up; `suggestPin` when the venue has no position and the photo has one. */
export interface PhotoUpload {
  photo: Photo
  suggestPin: boolean
}

/** A venue in the user's own library: the facts that hold whatever the scene, with their tags and notes. */
export interface LibraryVenue {
  id: string
  /** The scouted venue it was saved from, while that still exists. */
  sourceLocationId: string | null
  name: string
  address: string | null
  latitude: number | null
  longitude: number | null
  sourceUrl: string | null
  imageUrl: string | null
  bookingFriction: BookingFriction | null
  frictionNote: string | null
  footprintWarnings: string[]
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  tags: string[]
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface TagCount {
  tag: string
  venues: number
}

/** What a scouting run keeps to beyond the project's area; every field optional. */
export interface ScoutFilters {
  /** Where the radius is measured from, as an address; or a spot on the map in the two fields below. */
  baseAddress: string | null
  baseLatitude: number | null
  baseLongitude: number | null
  radiusKm: number | null
  /** The most a shooting day may cost, in the local currency; venues without a price are kept. */
  maxBudget: number | null
  /** Kinds of place to leave out, as free tags. */
  excludedTypes: string[]
  /** False leaves out private property; null means true. */
  includePrivate: boolean | null
}

/** How many venues a run's filters left out, by reason. */
export interface FilteredOut {
  outsideRadius: number
  overBudget: number
  excludedType: number
  privateProperty: number
}

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
  /** Venues the run's filters left out. */
  filteredOut?: FilteredOut
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
  | 'SCHOOL' | 'NIGHTLIFE' | 'PLACE_OF_WORSHIP' | 'UNIT_BASE'

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
  /** Where the sun is at the start, middle and end of the scene's time that day; missing in older reports. */
  sunPath?: SunPosition[] | null
}

/** The sun at one moment, seen from the venue. */
export interface SunPosition {
  /** Local time with offset. */
  at: string
  /** Degrees clockwise from true north. */
  azimuth: number
  /** Degrees above the horizon; negative below it. */
  elevation: number
  compass: string
  /** "Sun from SW (225°), 18° high at 16:00". */
  text: string
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

/** Somewhere the trucks could park near a venue. */
export interface UnitBaseSite {
  name: string | null
  /** "Open car park", "Roadside bays (lay-by)", "Rest area" or "Car park". */
  kind: string
  /** Spaces, where the map says. */
  capacity: number | null
  /** Roughly, from its outline; null for a point. */
  areaSquareMeters: number | null
  distanceMeters: number
  latitude: number | null
  longitude: number | null
}

export interface UnitBase {
  status: SectionStatus
  message: string | null
  radiusMeters: number
  /** The biggest first where the map says how big, then the nearest. */
  sites: UnitBaseSite[]
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
  /** Missing in reports made before unit bases were looked up. */
  unitBase?: UnitBase | null
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
  | 'AVAILABILITY_CHANGED'
  | 'COVER_SET_CHANGED'
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
  /** Only the moves already worked out; the rest PENDING. */
  moves: Moves
}

/** Company moves: on days with several venues, the drive from each to the next. */
export interface Moves {
  days: { date: string; moves: Move[] }[]
  /** Moves longer than this are flagged tooLong. */
  warnAfterMinutes: number
  attribution: string
}

export interface Move {
  fromLocationId: string
  fromName: string
  fromSceneId: string
  toLocationId: string
  toName: string
  toSceneId: string
  status: 'OK' | 'UNPLACED' | 'NO_ROUTE' | 'PENDING' | 'UNAVAILABLE'
  /** Without traffic, rounded up; null unless OK. */
  minutes: number | null
  kilometres: number | null
  tooLong: boolean
  /** "Starlite Diner to Neon Spoon Cafe: 21 min, 7.8 km by road". */
  text: string
}
