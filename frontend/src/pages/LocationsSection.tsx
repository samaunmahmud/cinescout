import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Location, LocationStatus, Page, Scene, ScoutFilters } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { useUpdateLocation } from '../components/locationHooks'
import { usePictureLookups } from '../components/usePictureLookups'
import { FitLabel, FitScore, LocationBadges, StatusSelect } from '../components/locationParts'
import { Stamp } from '../components/stickers'
import { locationPin } from '../components/map/locationPin'
import type { MapPin } from '../components/map/types'
import { VenueMap } from '../components/map/VenueMap'
import { linkButton } from '../components/buttonStyles'
import { DirectorLinkPanel } from '../components/DirectorLinkPanel'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { ChevronDown, Columns3, MapPin as PinIcon, MapPinned, Plus, Radar, SlidersHorizontal, TriangleAlert } from 'lucide-react'
import { EmptyState, Section } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { scoutingSummary } from '../lib/format'
import { describeFilters } from '../lib/scoutFilters'
import { ScoutFiltersForm } from '../components/ScoutFiltersForm'
import { displayHost, safeHttpUrl } from '../lib/url'
import { VenuePicture } from '../components/VenuePicture'
import { MapSnapshot } from '../components/MapSnapshot'
import { useCanEdit } from '../components/projectRole'

/**
 * The scene's candidate venues and the button that scouts for more. `locationArea` is the project's search
 * area: undefined while the project loads, null when it has none (scouting then has nowhere to search).
 */
export function LocationsSection({ scene, locationArea }: { scene: Scene; locationArea: string | null | undefined }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [page, setPage] = usePageParam()
  const locations = useQuery({
    queryKey: queryKeys.locationPage(scene.id, page),
    queryFn: () => api.locations.list(scene.id, page),
    placeholderData: previousPageOf<Page<Location>>(queryKeys.locationList(scene.id)),
  })
  useStayInRange(locations.data, setPage)
  usePictureLookups(locations.data?.items)

  const [choosingFilters, setChoosingFilters] = useState(false)
  const scout = useMutation({
    mutationFn: (filters?: ScoutFilters) => api.scenes.scout(scene.id, filters),
    onSuccess: () => setChoosingFilters(false),
    // Scouting parses an unparsed scene first, so the scene may have changed too.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.locationList(scene.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.scene(scene.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(scene.projectId) })
    },
  })

  const noArea = locationArea === null
  const hasLocations = (locations.data?.totalItems ?? 0) > 0

  return (
    <Section
      titleId="locations-heading"
      title="Locations"
      eyebrow="Scouted venues"
      icon={MapPinned}
      description={locationArea && `Scouting in ${locationArea}`}
      actions={
        <>
          {(locations.data?.totalItems ?? 0) > 1 && (
            <Link to={`/scenes/${scene.id}/compare`} className={linkButton('ghost')}>
              <Columns3 aria-hidden className="size-4" />
              Compare
            </Link>
          )}
          {canEdit && (
            <>
              <Link to={`/scenes/${scene.id}/locations/new`} className={linkButton('ghost')}>
                <Plus aria-hidden className="size-4" />
                Add venue
              </Link>
              <Button variant={hasLocations ? 'secondary' : 'primary'} busy={scout.isPending} disabled={noArea} onClick={() => scout.mutate(undefined)}>
                {!scout.isPending && <Radar aria-hidden className="size-4" />}
                {hasLocations ? 'Scout again' : 'Scout locations'}
              </Button>
            </>
          )}
        </>
      }
    >

      {canEdit && !noArea && (
        <RunFilters
          projectId={scene.projectId}
          open={choosingFilters}
          onToggle={() => setChoosingFilters(!choosingFilters)}
          busy={scout.isPending}
          error={scout.error}
          onScout={(filters) => scout.mutate(filters)}
        />
      )}

      {noArea && (
        <p className="rounded-lg border border-cue bg-cue-wash px-4 py-3 text-sm text-cue-ink">
          Scouting searches the project's location area, and this project has none yet.{' '}
          <Link to={`/projects/${scene.projectId}`} className="font-semibold underline hover:text-ink">
            Set one on the project
          </Link>
          .
        </p>
      )}

      {scout.isPending ? (
        <Spinner label="Searching for venues and assessing each one. This can take a few minutes." />
      ) : scout.isError ? (
        <ErrorAlert error={scout.error} />
      ) : (
        scout.data && (
          <p role="status" className="rounded-lg border border-go-mid bg-go-wash px-4 py-3 text-sm text-go-ink">
            {scoutingSummary(scout.data)}
          </p>
        )
      )}

      {locations.isPending ? (
        <Spinner label="Loading locations" />
      ) : locations.isError ? (
        <ErrorAlert error={locations.error} onRetry={() => locations.refetch()} />
      ) : locations.data.items.length === 0 ? (
        !scout.isPending && (
          <EmptyState icon={Radar}>
            No locations yet. Scouting searches the web for real venues that suit the scene and rates how well each one fits.
          </EmptyState>
        )
      ) : (
        <>
          <LocationsMap locations={locations.data.items} paged={locations.data.totalPages > 1} />
          <ul aria-label="Candidate locations" className="space-y-3">
            {locations.data.items.map((location) => (
              <li key={location.id}>
                <LocationCard location={location} />
              </li>
            ))}
          </ul>
          <Pager data={locations.data} onChange={setPage} label="Location pages" />
          <DirectorLinkPanel scope={{ kind: 'scene', id: scene.id }} />
        </>
      )}
    </Section>
  )
}

/** The venues that have a position, and a word on the ones that do not. `paged`: these are one page of several. */
function LocationsMap({ locations, paged }: { locations: Location[]; paged: boolean }) {
  const pins = locations.map((location) => locationPin(location)).filter((pin): pin is MapPin => pin !== null)
  const unplaced = locations.length - pins.length
  const hint = 'Scouted venues get a position when their logistics are worked out, or you can set one on the venue’s page.'
  return (
    <div className="space-y-2">
      {pins.length > 0 && <VenueMap pins={pins} label="Map of candidate locations" className="h-80" />}
      {pins.length > 0 && paged && <p className="text-sm text-muted">The map shows the venues on this page.</p>}
      {unplaced > 0 && (
        <p className="text-sm text-muted">
          {pins.length === 0 ? 'None of these venues is on a map yet.' : `${unplaced} of ${locations.length} venues are not on the map yet.`} {hint}
        </p>
      )}
    </div>
  )
}

function LocationCard({ location }: { location: Location }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const update = useUpdateLocation(location)
  const sourceUrl = safeHttpUrl(location.sourceUrl)

  const remove = useMutation({
    mutationFn: () => api.locations.remove(location.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.location(location.id) })
      // Out of the page at once; then the list again, as the pages and totals have moved.
      queryClient.setQueriesData<Page<Location>>({ queryKey: queryKeys.locationList(location.sceneId) }, (page) =>
        page && { ...page, items: page.items.filter((l) => l.id !== location.id) },
      )
      queryClient.invalidateQueries({ queryKey: queryKeys.locationList(location.sceneId) })
    },
  })

  return (
    <article
      aria-label={location.name}
      className={`board-card relative space-y-3 rounded-lg bg-white p-5 ${location.status === 'REJECTED' ? 'opacity-60' : ''}`}
    >
      <StatusStamp status={location.status} className="absolute top-3 right-40 hidden md:inline-block" />
      <div className="flex flex-wrap items-start gap-5">
        <Polaroid location={location} />
        <div className="min-w-0 flex-1 basis-44 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3>
              <Link
                to={`/locations/${location.id}`}
                className="font-display text-[1.4rem] leading-tight font-extrabold text-ink decoration-cue decoration-2 underline-offset-4 hover:underline"
              >
                {location.name}
              </Link>
            </h3>
            <LocationBadges location={location} />
          </div>
          {location.address && (
            <p className="flex items-center gap-1.5 font-script text-sm text-muted">
              <PinIcon aria-hidden className="size-3.5 shrink-0 text-cue-ink" />
              {location.address}
            </p>
          )}
          {sourceUrl && (
            <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="text-sm font-semibold text-cue-ink underline hover:text-cue-deep">
              {displayHost(sourceUrl)}
              <span className="sr-only"> (opens in a new tab)</span>
            </a>
          )}
        </div>
        <div className="flex shrink-0 flex-col items-center gap-2">
          {location.fitScore != null && (
            <>
              <FitScore score={location.fitScore} size="lg" />
              <FitLabel score={location.fitScore} />
            </>
          )}
          <StatusSelect location={location} update={update} />
        </div>
      </div>

      {location.status === 'REJECTED' && location.rejectionReason && (
        <p className="font-marker text-[15px] text-stop-ink">Passed: {location.rejectionReason}</p>
      )}
      {location.fitReason && <p className="text-[15px] leading-relaxed text-graphite">{location.fitReason}</p>}
      {location.frictionNote && <p className="text-sm text-muted">{location.frictionNote}</p>}
      {location.footprintWarnings.length > 0 && (
        <ul aria-label="Warnings" className="flex flex-wrap gap-2">
          {location.footprintWarnings.map((warning, i) => (
            <li key={warning} style={{ transform: `rotate(${i % 2 ? 0.8 : -0.8}deg)` }} className="tape flex items-center gap-1.5 !bg-highlight px-3 py-1 text-sm font-semibold">
              <TriangleAlert aria-hidden className="size-3.5 shrink-0" />
              {warning}
            </li>
          ))}
        </ul>
      )}
      {location.notes && <p className="border-l-2 border-cue pl-3 font-marker text-[15px] whitespace-pre-line text-graphite">{location.notes}</p>}

      <ErrorAlert error={update.error} />

      {confirmingDelete ? (
        <ConfirmDelete
          title={`Remove “${location.name}”?`}
          confirmLabel="Remove location"
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirmingDelete(false)}
        >
          This also deletes its outreach drafts. Scouting again may find it again as a new suggestion.
        </ConfirmDelete>
      ) : (
        canEdit && (
          <div className="flex justify-end">
            <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
              Remove
            </Button>
          </div>
        )
      )}
    </article>
  )
}

/** A small tilt, stable per venue, so the board does not look ruled. */
function tiltOf(id: string, range = 3): number {
  let h = 0
  for (const c of id) h = (h * 31 + c.charCodeAt(0)) | 0
  return ((Math.abs(h) % (range * 20 + 1)) - range * 10) / 10
}

/**
 * The venue's picture as a polaroid taped to the board, its name scrawled at the foot. Without a picture (or while
 * one loads, or if it fails), the street map round the venue; a blank one until it has either.
 */
function Polaroid({ location }: { location: Location }) {
  const placed = location.latitude != null && location.longitude != null
  return (
    <div aria-hidden style={{ transform: `rotate(${tiltOf(location.id)}deg)` }} className="polaroid relative hidden w-36 shrink-0 sm:block">
      <span className="tape-piece -top-2 left-10 z-10 w-14 -rotate-3" />
      <div className="relative h-24 bg-ground">
        {placed ? (
          <MapSnapshot latitude={location.latitude!} longitude={location.longitude!} />
        ) : (
          <div className="flex h-full items-center justify-center">
            <PinIcon className="size-6 text-line" />
          </div>
        )}
        {location.imageUrl && <VenuePicture src={location.imageUrl} className="absolute inset-0 h-full w-full" />}
      </div>
      <span className="absolute right-2 bottom-1 left-2 truncate font-marker text-[13px] text-graphite">{location.name}</span>
    </div>
  )
}

const stamps: Partial<Record<LocationStatus, { text: string; tone: 'cue' | 'go' | 'stop' | 'ink' }>> = {
  SHORTLISTED: { text: 'Shortlisted', tone: 'cue' },
  CONTACTED: { text: 'Contacted', tone: 'go' },
  CONFIRMED: { text: 'Locked', tone: 'ink' },
  REJECTED: { text: 'Passed', tone: 'stop' },
}

/** The rubber stamp for where the venue stands; none while it is only a suggestion. The select says it in words. */
function StatusStamp({ status, className = '' }: { status: LocationStatus; className?: string }) {
  const stamp = stamps[status]
  return stamp ? (
    <Stamp tone={stamp.tone} className={`text-sm ${className}`}>
      {stamp.text}
    </Stamp>
  ) : null
}

/**
 * Scouting with other filters than the project's, for one run: folded until asked for, then the project's filters
 * to start from. The project's own stay as they were.
 */
function RunFilters({
  projectId,
  open,
  onToggle,
  busy,
  error,
  onScout,
}: {
  projectId: string
  open: boolean
  onToggle: () => void
  busy: boolean
  error: unknown
  onScout: (filters: ScoutFilters) => void
}) {
  const { api } = useSession()
  const filters = useQuery({ queryKey: queryKeys.scoutFilters(projectId), queryFn: () => api.projects.scoutFilters(projectId), enabled: open })
  return (
    <div className="rounded-lg border-2 border-dashed border-line bg-paper px-4 py-3">
      <button
        type="button"
        aria-expanded={open}
        onClick={onToggle}
        className="flex w-full items-center gap-2 text-left text-sm font-semibold text-graphite hover:text-ink focus-visible:outline-2 focus-visible:outline-ink"
      >
        <SlidersHorizontal aria-hidden className="size-4 text-cue-ink" />
        Scout with other filters this time
        <ChevronDown aria-hidden className={`ml-auto size-4 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="space-y-3 pt-3">
          {filters.isPending ? (
            <Spinner label="Loading the project’s filters" />
          ) : filters.isError ? (
            <ErrorAlert error={filters.error} onRetry={() => filters.refetch()} />
          ) : (
            <>
              <p className="text-sm text-muted">
                The project’s filters: {describeFilters(filters.data)}{' '}
                <Link to={`/projects/${projectId}/settings?tab=scouting`} className="font-semibold text-cue-ink underline">
                  Change them for every run
                </Link>
              </p>
              <ScoutFiltersForm initial={filters.data} submitLabel="Scout with these filters" busy={busy} error={error} onSubmit={onScout} />
            </>
          )}
        </div>
      )}
    </div>
  )
}
