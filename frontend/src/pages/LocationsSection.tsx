import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState, type ChangeEvent } from 'react'
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
import { ChevronDown, Columns3, Image as ImageIcon, MapPin as PinIcon, MapPinned, Plus, Radar, SlidersHorizontal, TriangleAlert } from 'lucide-react'
import { EmptyState, Section } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { preparePhoto } from '../lib/photoPrep'
import { scoutingSummary } from '../lib/format'
import { describeFilters } from '../lib/scoutFilters'
import { ScoutFiltersForm } from '../components/ScoutFiltersForm'
import { displayHost, safeHttpUrl } from '../lib/url'
import { VenuePicture } from '../components/VenuePicture'
import { MapSnapshot } from '../components/MapSnapshot'
import { useCanEdit } from '../components/projectRole'
import { ActionMenu } from '../components/ActionMenu'
import { venueActions } from '../components/venueActions'
import { ListControls } from '../components/ListControls'
import { useVenueListView } from '../components/venueListView'
import { statusLabels } from '../lib/status'

/**
 * The scene's candidate venues and the button that scouts for more. `locationArea` is the project's search
 * area: undefined while the project loads, null when it has none (scouting then has nowhere to search).
 */
export function LocationsSection({ scene, locationArea }: { scene: Scene; locationArea: string | null | undefined }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [page, setPage] = usePageParam()
  const listView = useVenueListView()
  const locations = useQuery({
    queryKey: queryKeys.locationPage(scene.id, page, listView.sort, listView.status),
    queryFn: () => api.locations.list(scene.id, page, listView.sort, listView.status),
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

  const photoInput = useId()
  const fromPhoto = useMutation({
    mutationFn: async (file: File) => {
      const prepared = await preparePhoto(file)
      return api.scenes.scoutFromPhoto(scene.id, prepared.blob, prepared.filename)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.locationList(scene.id) }),
  })
  const scouting = scout.isPending || fromPhoto.isPending

  function pickPhoto(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (file) {
      scout.reset()
      fromPhoto.mutate(file)
    }
  }

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
              {!noArea && (
                <>
                  <input id={photoInput} type="file" accept="image/jpeg,image/png,image/heic,image/heif" onChange={pickPhoto} disabled={scouting} className="peer sr-only" />
                  <label
                    htmlFor={photoInput}
                    title="Scout for places that look like a reference photo"
                    className={`${linkButton('ghost')} cursor-pointer peer-focus-visible:ring-4 peer-focus-visible:ring-cue/40 peer-disabled:cursor-wait peer-disabled:opacity-60`}
                  >
                    <ImageIcon aria-hidden className="size-4" />
                    From a photo
                  </label>
                </>
              )}
              <Button
                variant={hasLocations ? 'secondary' : 'primary'}
                busy={scout.isPending}
                disabled={noArea || fromPhoto.isPending}
                onClick={() => {
                  fromPhoto.reset()
                  scout.mutate(undefined)
                }}
              >
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

      {fromPhoto.isPending ? (
        <Spinner label="Reading your photo, then searching for places like it. This can take a few minutes." />
      ) : fromPhoto.isError ? (
        <ErrorAlert error={fromPhoto.error} />
      ) : (
        fromPhoto.data && (
          <div role="status" className="space-y-1 rounded-lg border border-go-mid bg-go-wash px-4 py-3 text-sm text-go-ink">
            <p>
              Your photo reads as <strong>{fromPhoto.data.look.settingType}</strong>
              {fromPhoto.data.look.visualMood ? ` (${fromPhoto.data.look.visualMood})` : ''}
              {fromPhoto.data.look.features.length > 0 ? `: ${fromPhoto.data.look.features.join(', ')}` : ''}.
            </p>
            <p>{scoutingSummary(fromPhoto.data.result)}</p>
          </div>
        )
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
      ) : locations.data.items.length === 0 && listView.status === null ? (
        !scouting && (
          <EmptyState icon={Radar}>
            No locations yet. Scouting searches the web for real venues that suit the scene and rates how well each one fits.
          </EmptyState>
        )
      ) : (
        <>
          <ListControls state={listView} />
          {locations.data.items.length === 0 ? (
            <p className="rounded-lg border-2 border-dashed border-line px-4 py-6 text-center text-sm text-muted">
              No venue here is {listView.status === 'REJECTED' ? 'passed on' : statusLabels[listView.status!].toLowerCase()}.{' '}
              <button type="button" onClick={() => listView.setStatus(null)} className="font-semibold text-cue-ink underline">
                Show all
              </button>
            </p>
          ) : (
            <LocationsMap locations={locations.data.items} paged={locations.data.totalPages > 1} />
          )}
          {listView.view === 'list' ? (
            <ul aria-label="Candidate locations" className="divide-y divide-line-soft overflow-visible board-card rounded-lg bg-white">
              {locations.data.items.map((location) => (
                <li key={location.id}>
                  <LocationRow location={location} />
                </li>
              ))}
            </ul>
          ) : (
            <ul aria-label="Candidate locations" className="space-y-3">
              {locations.data.items.map((location) => (
                <li key={location.id}>
                  <LocationCard location={location} />
                </li>
              ))}
            </ul>
          )}
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

/** A venue's status control and its ⋯ quick actions, with what the last quick action did (or why it could not). */
function useVenueMenu(location: Location) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const update = useUpdateLocation(location)
  const quick = useMutation({ mutationFn: (action: () => Promise<string>) => action() })
  const actions = venueActions(location, {
    canEdit,
    setStatus: (status) => update.mutate({ status, notes: location.notes }),
    run: (action) => quick.mutate(action),
    makeCover: async () => {
      await api.covers.add(location.sceneId, location.id, null)
      queryClient.invalidateQueries({ queryKey: queryKeys.covers(location.sceneId) })
      return `${location.name} is now a cover set for this scene.`
    },
    saveToLibrary: async () => {
      await api.library.save(location.id)
      queryClient.invalidateQueries({ queryKey: queryKeys.libraryList })
      return `${location.name} is in your library.`
    },
  })
  return { update, quick, actions }
}

function MenuOutcome({ menu }: { menu: ReturnType<typeof useVenueMenu> }) {
  return (
    <>
      <ErrorAlert error={menu.update.error ?? menu.quick.error} />
      {menu.quick.isSuccess && (
        <p role="status" className="text-sm font-semibold text-go-ink">
          {menu.quick.data}
        </p>
      )}
    </>
  )
}

/** A venue as one line of the compact list: fit, name and address, status, quick actions. */
function LocationRow({ location }: { location: Location }) {
  const menu = useVenueMenu(location)
  return (
    <article aria-label={location.name} className={`space-y-2 px-4 py-3 ${location.status === 'REJECTED' ? 'opacity-60' : ''}`}>
      <div className="flex flex-wrap items-center gap-3">
        <span className="flex w-12 shrink-0 justify-center">
          {location.fitScore != null ? <FitScore score={location.fitScore} /> : <span className="text-sm text-subtle">—</span>}
        </span>
        <div className="min-w-0 flex-1 basis-40">
          <Link to={`/locations/${location.id}`} className="block truncate font-semibold text-ink decoration-cue decoration-2 underline-offset-4 hover:underline">
            {location.name}
          </Link>
          {location.address && <p className="truncate font-script text-xs text-muted">{location.address}</p>}
        </div>
        <div className="flex items-center gap-1">
          <StatusSelect location={location} update={menu.update} />
          <ActionMenu label={`Quick actions for ${location.name}`} actions={menu.actions} />
        </div>
      </div>
      <MenuOutcome menu={menu} />
    </article>
  )
}

function LocationCard({ location }: { location: Location }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const menu = useVenueMenu(location)
  const update = menu.update
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
      className={`group relative flex overflow-hidden rounded-2xl border border-line bg-white shadow-[var(--shadow-card)] transition duration-300 hover:-translate-y-0.5 hover:shadow-[var(--shadow-lift)] ${location.status === 'REJECTED' ? 'opacity-60' : ''}`}
    >
      <VenueStill location={location} />
      <div className="min-w-0 flex-1 space-y-3 p-5">
      <div className="flex flex-wrap items-start gap-5">
        <div className="min-w-0 flex-1 basis-44 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <StatusStamp status={location.status} className="sm:hidden" />
            <h3>
              <Link
                to={`/locations/${location.id}`}
                className="font-display text-xl leading-tight font-semibold text-ink decoration-cue-ink decoration-2 underline-offset-4 after:absolute after:inset-0 after:content-[''] hover:underline"
              >
                {location.name}
              </Link>
            </h3>
            <LocationBadges location={location} />
          </div>
          {location.address && (
            <p className="flex items-center gap-1.5 text-sm text-muted">
              <PinIcon aria-hidden className="size-3.5 shrink-0 text-cue-ink" />
              {location.address}
            </p>
          )}
          {sourceUrl && (
            <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="relative z-10 text-sm font-medium text-cue-ink underline-offset-2 hover:underline">
              {displayHost(sourceUrl)}
              <span className="sr-only"> (opens in a new tab)</span>
            </a>
          )}
        </div>
        <div className="relative z-10 flex shrink-0 flex-col items-center gap-2">
          {location.fitScore != null && (
            <>
              <FitScore score={location.fitScore} size="lg" />
              <FitLabel score={location.fitScore} />
            </>
          )}
          <div className="flex items-center gap-1">
            <StatusSelect location={location} update={update} />
            <ActionMenu label={`Quick actions for ${location.name}`} actions={menu.actions} />
          </div>
        </div>
      </div>

      {location.status === 'REJECTED' && location.rejectionReason && (
        <p className="text-sm font-medium text-stop-ink">Passed: {location.rejectionReason}</p>
      )}
      {location.fitReason && <p className="text-[15px] leading-relaxed text-graphite">{location.fitReason}</p>}
      {location.frictionNote && <p className="text-sm text-muted">{location.frictionNote}</p>}
      {location.footprintWarnings.length > 0 && (
        <ul aria-label="Warnings" className="flex flex-wrap gap-2">
          {location.footprintWarnings.map((warning) => (
            <li key={warning} className="flex items-center gap-1.5 rounded-lg bg-cue-wash px-2.5 py-1 text-[13px] font-medium text-cue-deep ring-1 ring-cue-soft ring-inset">
              <TriangleAlert aria-hidden className="size-3.5 shrink-0" />
              {warning}
            </li>
          ))}
        </ul>
      )}
      {location.notes && <p className="rounded-lg bg-ground px-3 py-2 text-sm whitespace-pre-line text-graphite">{location.notes}</p>}

      <div className="relative z-10 space-y-3">
      <MenuOutcome menu={menu} />

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
      </div>
      </div>
    </article>
  )
}

/**
 * The venue's picture as a polaroid taped to the board, its name scrawled at the foot. Without a picture (or while
 * one loads, or if it fails), the street map round the venue; a blank one until it has either.
 */
/** The venue's still down the card's side: its picture, or the map round its pin; the status pill over it. */
function VenueStill({ location }: { location: Location }) {
  const placed = location.latitude != null && location.longitude != null
  return (
    <div className="relative hidden w-44 shrink-0 overflow-hidden bg-ground sm:block">
      <div aria-hidden className="absolute inset-0 transition-transform duration-700 ease-out group-hover:scale-105">
        {placed ? (
          <MapSnapshot latitude={location.latitude!} longitude={location.longitude!} />
        ) : (
          <div className="flex h-full items-center justify-center">
            <PinIcon className="size-6 text-line" />
          </div>
        )}
        {location.imageUrl && <VenuePicture src={location.imageUrl} className="absolute inset-0 h-full w-full" />}
      </div>
      <StatusStamp status={location.status} className="absolute top-3 left-3 shadow-[var(--shadow-card)]" />
    </div>
  )
}

const stamps: Partial<Record<LocationStatus, { text: string; tone: 'cue' | 'go' | 'stop' | 'ink' }>> = {
  SHORTLISTED: { text: 'Shortlisted', tone: 'cue' },
  CONTACTED: { text: 'Contacted', tone: 'go' },
  CONFIRMED: { text: 'Confirmed', tone: 'go' },
  REJECTED: { text: 'Passed', tone: 'stop' },
}

/** The pill for where the venue stands; none while it is only a suggestion. The select says it in words. */
function StatusStamp({ status, className = '' }: { status: LocationStatus; className?: string }) {
  const stamp = stamps[status]
  return stamp ? (
    <Stamp tone={stamp.tone} className={className}>
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
