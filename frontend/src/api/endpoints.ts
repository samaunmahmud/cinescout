import { ApiError, download, request } from './client'
import type {
  PermitGuidance,
  BookingFriction,
  Activity,
  ActivityKind,
  AddMemberResult,
  Agreement,
  Alert,
  Availability,
  CoverSet,
  AvailabilityRequest,
  BatchLogisticsResult,
  BatchParseResult,
  ChangePasswordRequest,
  CommentRequest,
  CreateLocationRequest,
  CreateProjectRequest,
  DirectorCall,
  DirectorCallRequest,
  DirectorLink,
  DirectorScope,
  Crew,
  GenerateOutreachRequest,
  InvitePreview,
  LibraryVenue,
  Photo,
  PhotoUpload,
  LoginRequest,
  ManualReplyRequest,
  Location,
  LocationSort,
  LocationStatus,
  LocationVideos,
  LogisticsReport,
  Moves,
  Member,
  OutreachDraft,
  OutreachReply,
  OutreachFilter,
  Page,
  Project,
  ProjectLocation,
  ProjectOutreach,
  ProjectSettings,
  ProjectProgress,
  ProjectRole,
  ProjectStatus,
  PublicCallSheet,
  PublicShortlist,
  RecceAnswers,
  RegisterRequest,
  Scene,
  Schedule,
  SceneRequest,
  ScoutFilters,
  TagCount,
  ScoutingResult,
  ScriptImport,
  UpdateContactRequest,
  UpdateCoordinatesRequest,
  UpdateLocationRequest,
  VenueComment,
  UpdateOutreachRequest,
  UpdateProjectRequest,
  User,
} from './types'

/** Items per page for every list the app shows. */
export const PAGE_SIZE = 24

const pageQuery = (page: number) => `page=${page}&size=${PAGE_SIZE}`

/** What needs no login: a shared call sheet, whose token is the permission. */
const directorBase = (scope: DirectorScope) => `/api/${scope.kind === 'project' ? 'projects' : 'scenes'}/${encodeURIComponent(scope.id)}`

export const publicApi = {
  callSheet: (token: string) => request<PublicCallSheet>(`/api/public/call-sheets/${encodeURIComponent(token)}`),
  /** A director link's shortlisted venues, a page at a time. */
  shortlist: (token: string, page = 0) => request<PublicShortlist>(`/api/public/shortlists/${encodeURIComponent(token)}?${pageQuery(page)}`),
  /** A guest's call on one venue; the same name answering again replaces the earlier call. */
  answer: (token: string, locationId: string, body: DirectorCallRequest) =>
    request<DirectorCall>(`/api/public/shortlists/${encodeURIComponent(token)}/venues/${encodeURIComponent(locationId)}/response`, {
      method: 'POST',
      body,
    }),
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
      /** Your calendar feed's link; 404 while the feed is off. The feed is `/api/public/calendars/{token}.ics`. */
      calendarLink: () => call<{ token: string }>('/api/account/calendar-link'),
      /** Turns the feed on, or gives it a new link that replaces the old one. */
      newCalendarLink: () => call<{ token: string }>('/api/account/calendar-link', { method: 'POST' }),
      turnOffCalendar: () => call<void>('/api/account/calendar-link', { method: 'DELETE' }),
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
      /** Works out the logistics of the confirmed venues that have none, up to six a call; the answer says how many are left. */
      refreshLogistics: (id: string) => call<BatchLogisticsResult>(`/api/projects/${encodeURIComponent(id)}/logistics`, { method: 'POST' }),
      /** The call sheet's shared link; 404 while it is not shared. */
      callSheetLink: (id: string) => call<{ token: string }>(`/api/projects/${encodeURIComponent(id)}/call-sheet-link`),
      /** A new shared link, replacing any earlier one. */
      shareCallSheet: (id: string) => call<{ token: string }>(`/api/projects/${encodeURIComponent(id)}/call-sheet-link`, { method: 'POST' }),
      stopSharingCallSheet: (id: string) => call<void>(`/api/projects/${encodeURIComponent(id)}/call-sheet-link`, { method: 'DELETE' }),
      /** Company moves between each shoot day's venues; new ones are looked up a few at a time (the rest PENDING). */
      moves: (id: string) => call<Moves>(`/api/projects/${encodeURIComponent(id)}/moves`),
      /** The scenes by the day their shoot starts, each with its confirmed locations. */
      schedule: (id: string) => call<Schedule>(`/api/projects/${encodeURIComponent(id)}/schedule`),
      /** The project's activity log, newest first; `kind` null means every kind. */
      activity: (id: string, kind: ActivityKind | null, page = 0) =>
        call<Page<Activity>>(`/api/projects/${encodeURIComponent(id)}/activity?${kind ? `kind=${kind}&` : ''}${pageQuery(page)}`),
      /** The project's scouting filters; every field empty while none are set. */
      scoutFilters: (id: string) => call<ScoutFilters>(`/api/projects/${encodeURIComponent(id)}/scout-filters`),
      setScoutFilters: (id: string, body: ScoutFilters) =>
        call<ScoutFilters>(`/api/projects/${encodeURIComponent(id)}/scout-filters`, { method: 'PUT', body }),
      /** The project's working settings (how long before an unanswered email is flagged for a follow-up). */
      settings: (id: string) => call<ProjectSettings>(`/api/projects/${encodeURIComponent(id)}/settings`),
      setSettings: (id: string, body: ProjectSettings) =>
        call<ProjectSettings>(`/api/projects/${encodeURIComponent(id)}/settings`, { method: 'PUT', body }),
      /** Everyone on the project; the owner also gets the open invites. */
      crew: (id: string) => call<Crew>(`/api/projects/${encodeURIComponent(id)}/members`),
      addMember: (id: string, email: string, role: ProjectRole) =>
        call<AddMemberResult>(`/api/projects/${encodeURIComponent(id)}/members`, { method: 'POST', body: { email, role } }),
      changeRole: (id: string, userId: string, role: ProjectRole) =>
        call<Member>(`/api/projects/${encodeURIComponent(id)}/members/${encodeURIComponent(userId)}`, { method: 'PUT', body: { role } }),
      /** Removes a member, or (for the user's own id) leaves the project. */
      removeMember: (id: string, userId: string) =>
        call<void>(`/api/projects/${encodeURIComponent(id)}/members/${encodeURIComponent(userId)}`, { method: 'DELETE' }),
      transfer: (id: string, userId: string) => call<Crew>(`/api/projects/${encodeURIComponent(id)}/transfer`, { method: 'POST', body: { userId } }),
      revokeInvite: (id: string, inviteId: string) =>
        call<void>(`/api/projects/${encodeURIComponent(id)}/invites/${encodeURIComponent(inviteId)}`, { method: 'DELETE' }),
    },
    director: {
      /** The link for the project or the scene; 404 while there is none. */
      link: (scope: DirectorScope) => call<DirectorLink>(`${directorBase(scope)}/director-link`),
      /** A new link, replacing the old one. Only the owner may set `showPrivate`. */
      share: (scope: DirectorScope, showPrivate: boolean) =>
        call<DirectorLink>(`${directorBase(scope)}/director-link`, { method: 'POST', body: { showPrivate } }),
      stop: (scope: DirectorScope) => call<void>(`${directorBase(scope)}/director-link`, { method: 'DELETE' }),
      /** The calls on one venue, latest first (one per guest name, so few). */
      forLocation: (locationId: string) =>
        call<Page<DirectorCall>>(`/api/locations/${encodeURIComponent(locationId)}/director-responses?page=0&size=100`),
      /** The calls on a scene's venues, latest first. */
      forScene: (sceneId: string) => call<Page<DirectorCall>>(`/api/scenes/${encodeURIComponent(sceneId)}/director-responses?page=0&size=100`),
    },
    comments: {
      /** The venue's threads, oldest first, each with all its replies. */
      list: (locationId: string, page = 0) => call<Page<VenueComment>>(`/api/locations/${encodeURIComponent(locationId)}/comments?${pageQuery(page)}`),
      post: (locationId: string, body: CommentRequest) =>
        call<VenueComment>(`/api/locations/${encodeURIComponent(locationId)}/comments`, { method: 'POST', body }),
      edit: (id: string, body: Omit<CommentRequest, 'parentId'>) => call<VenueComment>(`/api/comments/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/comments/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    },
    photos: {
      list: (locationId: string) => call<Page<Photo>>(`/api/locations/${encodeURIComponent(locationId)}/photos?page=0&size=30`),
      /** A JPEG or PNG; `gps` is where a photo the browser converted (HEIC) said it was taken. */
      upload: (locationId: string, file: Blob, filename: string, gps: { latitude: number; longitude: number } | null) => {
        const form = new FormData()
        form.append('file', file, filename)
        if (gps) {
          form.append('latitude', String(gps.latitude))
          form.append('longitude', String(gps.longitude))
        }
        return call<PhotoUpload>(`/api/locations/${encodeURIComponent(locationId)}/photos`, { method: 'POST', body: form })
      },
      remove: (id: string) => call<void>(`/api/photos/${encodeURIComponent(id)}`, { method: 'DELETE' }),
      /** `photoId` null goes back to the web page's picture. */
      setCover: (locationId: string, photoId: string | null) =>
        call<Location>(`/api/locations/${encodeURIComponent(locationId)}/cover`, { method: 'PUT', body: { photoId } }),
    },
    library: {
      /** Newest first; `search` matches the name, address, notes or a tag; `tag` keeps one tag. */
      list: (search: string, tag: string | null, page = 0) => {
        const query = new URLSearchParams()
        if (search.trim()) query.set('q', search.trim())
        if (tag) query.set('tag', tag)
        query.set('page', String(page))
        query.set('size', String(PAGE_SIZE))
        return call<Page<LibraryVenue>>(`/api/library?${query.toString().replace(/\+/g, '%20')}`)
      },
      tags: () => call<TagCount[]>('/api/library/tags'),
      /** Saving the same venue again returns the copy already saved. */
      save: (locationId: string) => call<LibraryVenue>('/api/library', { method: 'POST', body: { locationId } }),
      update: (id: string, body: { name: string; tags: string[]; notes: string | null }) =>
        call<LibraryVenue>(`/api/library/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/library/${encodeURIComponent(id)}`, { method: 'DELETE' }),
      /** Copies the venue into the scene, without an AI call; 409 when the scene has a venue from the same page. */
      addToScene: (sceneId: string, libraryVenueId: string) =>
        call<Location>(`/api/scenes/${encodeURIComponent(sceneId)}/locations/from-library`, { method: 'POST', body: { libraryVenueId } }),
    },
    invites: {
      preview: (token: string) => call<InvitePreview>(`/api/invites/${encodeURIComponent(token)}`),
      accept: (token: string) => call<InvitePreview>(`/api/invites/${encodeURIComponent(token)}/accept`, { method: 'POST' }),
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
      /** Sets the shoot window and its times and nothing else; a null date or time clears it. */
      reschedule: (
        id: string,
        body: { shootDateStart: string | null; shootDateEnd: string | null; callTime: string | null; wrapTime: string | null },
      ) =>
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
      /** Keeps to the project's scouting filters, or to `filters` for this run. */
      scout: (id: string, filters?: ScoutFilters) =>
        call<ScoutingResult>(`/api/scenes/${encodeURIComponent(id)}/scout`, { method: 'POST', ...(filters ? { body: { filters } } : {}) }),
    },
    locations: {
      /** Best fit first; venues added by hand (no score) last. */
      list: (sceneId: string, page = 0, sort: LocationSort = 'FIT', status: LocationStatus | null = null) =>
        call<Page<Location>>(
          `/api/scenes/${encodeURIComponent(sceneId)}/locations?${sort !== 'FIT' ? `sort=${sort}&` : ''}${status ? `status=${status}&` : ''}${pageQuery(page)}`,
        ),
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
      /** The permit guide: the filming office for the venue's area, how far ahead to apply, what to have ready. */
      permit: (id: string) => call<PermitGuidance>(`/api/locations/${encodeURIComponent(id)}/permit`),
      /** Who has to say yes to filming there; null clears it. */
      setBookingRoute: (id: string, bookingFriction: BookingFriction | null) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}/booking-route`, { method: 'PUT', body: { bookingFriction } }),
      updateContact: (id: string, body: UpdateContactRequest) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}/contact`, { method: 'PUT', body }),
      /** Changes only the tech recce answers sent; null clears one. */
      answerRecce: (id: string, answers: RecceAnswers) =>
        call<Location>(`/api/locations/${encodeURIComponent(id)}/recce`, { method: 'PATCH', body: answers }),
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
    agreements: {
      /** A venue's location releases, newest version first. */
      list: (locationId: string) => call<Page<Agreement>>(`/api/locations/${encodeURIComponent(locationId)}/agreements?page=0&size=50`),
      /** Makes a new version from the venue's details; 409 once the venue has 30. */
      generate: (locationId: string, productionCompany: string | null) =>
        call<Agreement>(`/api/locations/${encodeURIComponent(locationId)}/agreements`, { method: 'POST', body: { productionCompany } }),
      /** The PDF, to save. */
      download: (id: string) => guarded(() => download(`/api/agreements/${encodeURIComponent(id)}/file`, 'application/pdf')),
      remove: (id: string) => call<void>(`/api/agreements/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    },
    alerts: {
      /** Your newest alerts first. */
      list: (page = 0, size = 10) => call<Page<Alert>>(`/api/alerts?page=${page}&size=${size}`),
      unreadCount: () => call<{ unread: number }>('/api/alerts/unread-count'),
      markRead: (id: string) => call<void>(`/api/alerts/${encodeURIComponent(id)}/read`, { method: 'POST' }),
      markAllRead: () => call<void>('/api/alerts/read-all', { method: 'POST' }),
    },
    covers: {
      /** A scene's cover sets, in the order they were added (at most 5). */
      list: (sceneId: string) => call<Page<CoverSet>>(`/api/scenes/${encodeURIComponent(sceneId)}/covers?page=0&size=50`),
      /** Makes one of the scene's candidates a cover; 409 for its confirmed venue, a repeat, or a sixth. */
      add: (sceneId: string, locationId: string, trigger: string | null) =>
        call<CoverSet>(`/api/scenes/${encodeURIComponent(sceneId)}/covers`, { method: 'POST', body: { locationId, trigger } }),
      /** Null or blank clears it. */
      setTrigger: (id: string, trigger: string | null) =>
        call<CoverSet>(`/api/covers/${encodeURIComponent(id)}`, { method: 'PUT', body: { trigger } }),
      /** The venue stays a candidate of the scene. */
      remove: (id: string) => call<void>(`/api/covers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    },
    availability: {
      /** A venue's recorded days, earliest first. */
      list: (locationId: string) => call<Page<Availability>>(`/api/locations/${encodeURIComponent(locationId)}/availability?page=0&size=100`),
      /** Sets the state of a day or a run of days, replacing what was there. */
      set: (locationId: string, body: AvailabilityRequest) =>
        call<Availability[]>(`/api/locations/${encodeURIComponent(locationId)}/availability`, { method: 'PUT', body }),
      clear: (locationId: string, day: string) =>
        call<void>(`/api/locations/${encodeURIComponent(locationId)}/availability/${encodeURIComponent(day)}`, { method: 'DELETE' }),
    },
    outreach: {
      /** The replies to an email, latest first. */
      replies: (draftId: string) => call<Page<OutreachReply>>(`/api/outreach-drafts/${encodeURIComponent(draftId)}/replies?page=0&size=50`),
      /** Records a reply by hand (every field optional) and marks the email replied. */
      recordReply: (draftId: string, body: ManualReplyRequest) =>
        call<OutreachReply>(`/api/outreach-drafts/${encodeURIComponent(draftId)}/replies`, { method: 'POST', body }),
      /**
       * Every draft of the project, newest first, each with its venue and scene; `filter` null means all, FOLLOW_UP the
       * emails waiting on a follow-up (oldest sent first).
       */
      listForProject: (projectId: string, filter: OutreachFilter | null, page = 0) =>
        call<Page<ProjectOutreach>>(
          `/api/projects/${encodeURIComponent(projectId)}/outreach-drafts?${
            filter === 'FOLLOW_UP' ? 'followUp=true&' : filter ? `status=${filter}&` : ''
          }${pageQuery(page)}`,
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
      /**
       * Has the AI write a short chaser for an email marked as sent, saved as a new draft. It sees the first email's
       * subject and the day it went, never its text or any reply. 409 unless the email is marked sent.
       */
      followUp: (id: string) => call<OutreachDraft>(`/api/outreach-drafts/${encodeURIComponent(id)}/follow-up`, { method: 'POST' }),
      update: (id: string, body: UpdateOutreachRequest) =>
        call<OutreachDraft>(`/api/outreach-drafts/${encodeURIComponent(id)}`, { method: 'PUT', body }),
      remove: (id: string) => call<void>(`/api/outreach-drafts/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    },
  }
}

export type Api = ReturnType<typeof createApi>
