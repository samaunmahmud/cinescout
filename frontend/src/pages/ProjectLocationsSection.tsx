import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, MapPin as PinIcon, MapPinned } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Location, LocationStatus, Page, ProjectLocation, ProjectProgress } from '../api/types'
import { useSession } from '../auth/context'
import { useUpdateLocation } from '../components/locationHooks'
import { FitScore, LocationBadges, StatusSelect } from '../components/locationParts'
import { locationPin } from '../components/map/locationPin'
import type { MapPin } from '../components/map/types'
import { VenueMap } from '../components/map/VenueMap'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { EmptyState, Section } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { saveFile } from '../lib/saveFile'
import { progressSummary, sceneLabel } from '../lib/format'
import { isLocationStatus, locationStatuses, statusLabels } from '../lib/status'
import { VenuePicture } from '../components/VenuePicture'

/**
 * Every scene's candidate venues in one place: where the scouting stands, a filter by status (the shortlist,
 * say), one map, and the venues scene by scene. The lists are refetched on every visit, as statuses change on
 * the scene and location pages too.
 */
export function ProjectLocationsSection({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const [page, setPage] = usePageParam()
  const rawStatus = params.get('status')
  const status = isLocationStatus(rawStatus) ? rawStatus : null

  const progress = useQuery({
    queryKey: queryKeys.projectProgress(projectId),
    queryFn: () => api.projects.progress(projectId),
    refetchOnMount: 'always',
  })
  const locations = useQuery({
    queryKey: queryKeys.projectLocationPage(projectId, status, page),
    queryFn: () => api.locations.listForProject(projectId, status, page),
    placeholderData: previousPageOf<Page<ProjectLocation>>(queryKeys.projectLocationList(projectId)),
    refetchOnMount: 'always',
  })
  useStayInRange(locations.data, setPage)

  const exportCsv = useMutation({
    mutationFn: () => api.locations.exportForProject(projectId, status),
    onSuccess: (file) => saveFile(file.blob, file.filename ?? 'locations.csv'),
  })

  const selectStatus = (next: LocationStatus | null) =>
    setParams((current) => {
      const updated = new URLSearchParams(current)
      if (next) updated.set('status', next)
      else updated.delete('status')
      updated.delete('page')
      return updated
    })

  // A changed status shows at once; it also moves the venue between the filtered lists and changes the counts.
  const refresh = (updated: Location) => {
    queryClient.setQueriesData<Page<ProjectLocation>>({ queryKey: queryKeys.projectLocationList(projectId) }, (cached) =>
      cached && { ...cached, items: cached.items.map((l) => (l.id === updated.id ? { ...l, status: updated.status, notes: updated.notes } : l)) },
    )
    queryClient.invalidateQueries({ queryKey: queryKeys.projectLocationList(projectId) })
    queryClient.invalidateQueries({ queryKey: queryKeys.projectProgress(projectId) })
  }

  return (
    <Section
      titleId="project-locations-heading"
      title="Locations"
      eyebrow="Across every scene"
      icon={MapPinned}
      description={progress.data && progressSummary(progress.data)}
      actions={
        (locations.data?.totalItems ?? 0) > 0 && (
          <Button variant="secondary" busy={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
            {!exportCsv.isPending && <Download aria-hidden className="size-4" />}
            {status ? `Export ${statusLabels[status].toLowerCase()} as CSV` : 'Export as CSV'}
          </Button>
        )
      }
    >
      <ErrorAlert error={exportCsv.error} />
      {progress.data && progress.data.locations > 0 && <StatusFilter progress={progress.data} selected={status} onSelect={selectStatus} />}

      {locations.isPending ? (
        <Spinner label="Loading locations" />
      ) : locations.isError ? (
        <ErrorAlert error={locations.error} onRetry={() => locations.refetch()} />
      ) : locations.data.items.length === 0 ? (
        <EmptyState icon={MapPinned}>
          {status
            ? `No ${statusLabels[status].toLowerCase()} locations in this project.`
            : 'No locations yet. Open a scene and scout it, or add a venue by hand, and it shows up here with every other scene’s.'}
        </EmptyState>
      ) : (
        <>
          <ProjectMap locations={locations.data.items} paged={locations.data.totalPages > 1} />
          <div className="space-y-6">
            {groupByScene(locations.data.items).map((group) => (
              <section key={group.sceneId} aria-label={sceneLabel({ sceneNumber: group.sceneNumber, title: group.sceneTitle })} className="space-y-2">
                <h3 className="text-sm font-semibold tracking-wide text-stone-300">
                  <Link to={`/scenes/${group.sceneId}`} className="hover:text-amber-300">
                    {sceneLabel({ sceneNumber: group.sceneNumber, title: group.sceneTitle })}
                  </Link>
                </h3>
                <ul className="space-y-2">
                  {group.locations.map((location) => (
                    <li key={location.id}>
                      <LocationRow location={location} onSaved={refresh} />
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
          <Pager data={locations.data} onChange={setPage} label="Location pages" />
        </>
      )}
    </Section>
  )
}

function StatusFilter({
  progress,
  selected,
  onSelect,
}: {
  progress: ProjectProgress
  selected: LocationStatus | null
  onSelect: (status: LocationStatus | null) => void
}) {
  const options: { status: LocationStatus | null; label: string; count: number }[] = [
    { status: null, label: 'All', count: progress.locations },
    ...locationStatuses.map((status) => ({ status, label: statusLabels[status], count: progress.locationsByStatus[status] ?? 0 })),
  ]
  return (
    <div role="group" aria-label="Filter by status" className="flex flex-wrap gap-2">
      {options.map(({ status, label, count }) => {
        const active = status === selected
        return (
          <button
            key={label}
            type="button"
            aria-pressed={active}
            onClick={() => onSelect(status)}
            className={`rounded-full px-3 py-1.5 text-sm font-semibold ring-1 transition ring-inset focus-visible:outline-2 focus-visible:outline-amber-400 ${
              active ? 'bg-amber-500/15 text-amber-200 ring-amber-400/40' : 'bg-white/[0.03] text-stone-300 ring-white/10 hover:bg-white/[0.07]'
            }`}
          >
            {label}{' '}
            <span className={`ml-1 text-xs font-normal ${active ? 'text-amber-300/80' : 'text-stone-500'}`}>{count}</span>
          </button>
        )
      })}
    </div>
  )
}

interface SceneGroup {
  sceneId: string
  sceneNumber: number | null
  sceneTitle: string
  locations: ProjectLocation[]
}

/** The list comes scene by scene, so a scene's venues are next to each other. */
function groupByScene(locations: ProjectLocation[]): SceneGroup[] {
  const groups: SceneGroup[] = []
  for (const location of locations) {
    const last = groups[groups.length - 1]
    if (last && last.sceneId === location.sceneId) last.locations.push(location)
    else groups.push({ sceneId: location.sceneId, sceneNumber: location.sceneNumber, sceneTitle: location.sceneTitle, locations: [location] })
  }
  return groups
}

function ProjectMap({ locations, paged }: { locations: ProjectLocation[]; paged: boolean }) {
  const pins = locations.map((location) => locationPin(location)).filter((pin): pin is MapPin => pin !== null)
  if (pins.length === 0) return null
  const unplaced = locations.length - pins.length
  return (
    <div className="space-y-2">
      <VenueMap pins={pins} label="Map of the project’s locations" className="h-80 shadow-xl shadow-black/40" />
      {(paged || unplaced > 0) && (
        <p className="text-sm text-stone-400">
          {paged && 'The map shows the venues on this page. '}
          {unplaced > 0 && `${unplaced} of ${locations.length} venues are not on the map yet.`}
        </p>
      )}
    </div>
  )
}

function LocationRow({ location, onSaved }: { location: ProjectLocation; onSaved: (updated: Location) => void }) {
  const update = useUpdateLocation(location, onSaved)
  return (
    <article
      aria-label={location.name}
      className={`space-y-2 rounded-xl border border-white/[0.07] bg-gradient-to-b from-frame/90 to-reel/90 p-4 shadow-lg shadow-black/30 transition hover:border-white/15 ${location.status === 'REJECTED' ? 'opacity-55' : ''}`}
    >
      <div className="flex flex-wrap items-start gap-4">
        {location.imageUrl && <VenuePicture src={location.imageUrl} className="hidden h-20 w-32 shrink-0 rounded-md ring-1 ring-white/10 sm:block" />}
        {location.fitScore != null && <FitScore score={location.fitScore} />}
        <div className="min-w-0 flex-1 basis-44 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h4 className="font-semibold">
              <Link to={`/locations/${location.id}`} className="text-lg text-stone-50 hover:text-amber-300">
                {location.name}
              </Link>
            </h4>
            <LocationBadges location={location} />
          </div>
          {location.address && (
            <p className="flex items-center gap-1.5 text-sm text-stone-400">
              <PinIcon aria-hidden className="size-3.5 shrink-0 text-amber-400/70" />
              {location.address}
            </p>
          )}
          {location.fitReason && <p className="text-sm text-stone-300">{location.fitReason}</p>}
          {location.notes && <p className="border-l-2 border-stone-700 pl-3 text-sm whitespace-pre-line text-stone-300 italic">{location.notes}</p>}
        </div>
        <div className="shrink-0">
          <StatusSelect location={location} update={update} />
        </div>
      </div>
      <ErrorAlert error={update.error} />
    </article>
  )
}
