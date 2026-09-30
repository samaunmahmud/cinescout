import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Location, Page, Scene } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { useUpdateLocation } from '../components/locationHooks'
import { FitScore, LocationBadges, StatusSelect } from '../components/locationParts'
import { locationPin } from '../components/map/locationPin'
import type { MapPin } from '../components/map/types'
import { VenueMap } from '../components/map/VenueMap'
import { linkButton } from '../components/buttonStyles'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { Columns3, MapPin as PinIcon, MapPinned, Plus, Radar, TriangleAlert } from 'lucide-react'
import { EmptyState, Section } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { scoutingSummary } from '../lib/format'
import { displayHost, safeHttpUrl } from '../lib/url'

/**
 * The scene's candidate venues and the button that scouts for more. `locationArea` is the project's search
 * area: undefined while the project loads, null when it has none (scouting then has nowhere to search).
 */
export function LocationsSection({ scene, locationArea }: { scene: Scene; locationArea: string | null | undefined }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [page, setPage] = usePageParam()
  const locations = useQuery({
    queryKey: queryKeys.locationPage(scene.id, page),
    queryFn: () => api.locations.list(scene.id, page),
    placeholderData: previousPageOf<Page<Location>>(queryKeys.locationList(scene.id)),
  })
  useStayInRange(locations.data, setPage)

  const scout = useMutation({
    mutationFn: () => api.scenes.scout(scene.id),
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
          <Link to={`/scenes/${scene.id}/locations/new`} className={linkButton('ghost')}>
            <Plus aria-hidden className="size-4" />
            Add venue
          </Link>
          <Button variant={hasLocations ? 'secondary' : 'primary'} busy={scout.isPending} disabled={noArea} onClick={() => scout.mutate()}>
            {!scout.isPending && <Radar aria-hidden className="size-4" />}
            {hasLocations ? 'Scout again' : 'Scout locations'}
          </Button>
        </>
      }
    >

      {noArea && (
        <p className="rounded-lg border border-amber-900/70 bg-amber-950/40 px-4 py-3 text-sm text-amber-200">
          Scouting searches the project's location area, and this project has none yet.{' '}
          <Link to={`/projects/${scene.projectId}`} className="font-semibold underline hover:text-white">
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
          <p role="status" className="rounded-lg border border-emerald-900/70 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-200">
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
      {pins.length > 0 && <VenueMap pins={pins} label="Map of candidate locations" className="h-80 shadow-xl shadow-black/40" />}
      {pins.length > 0 && paged && <p className="text-sm text-stone-400">The map shows the venues on this page.</p>}
      {unplaced > 0 && (
        <p className="text-sm text-stone-400">
          {pins.length === 0 ? 'None of these venues is on a map yet.' : `${unplaced} of ${locations.length} venues are not on the map yet.`} {hint}
        </p>
      )}
    </div>
  )
}

function LocationCard({ location }: { location: Location }) {
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
      className={`space-y-3 rounded-xl border border-white/[0.07] bg-gradient-to-b from-frame/90 to-reel/90 p-5 shadow-lg shadow-black/30 transition hover:border-white/15 ${location.status === 'REJECTED' ? 'opacity-55' : ''}`}
    >
      <div className="flex flex-wrap items-start gap-4">
        {location.fitScore != null && <FitScore score={location.fitScore} />}
        <div className="min-w-0 flex-1 basis-44 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold">
              <Link to={`/locations/${location.id}`} className="text-lg text-stone-50 hover:text-amber-300">
                {location.name}
              </Link>
            </h3>
            <LocationBadges location={location} />
          </div>
          {location.address && (
            <p className="flex items-center gap-1.5 text-sm text-stone-400">
              <PinIcon aria-hidden className="size-3.5 shrink-0 text-amber-400/70" />
              {location.address}
            </p>
          )}
          {sourceUrl && (
            <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="text-sm text-amber-300 underline hover:text-amber-200">
              {displayHost(sourceUrl)}
              <span className="sr-only"> (opens in a new tab)</span>
            </a>
          )}
        </div>
        <div className="shrink-0">
          <StatusSelect location={location} update={update} />
        </div>
      </div>

      {location.fitReason && <p className="text-sm text-stone-200">{location.fitReason}</p>}
      {location.frictionNote && <p className="text-sm text-stone-400">{location.frictionNote}</p>}
      {location.footprintWarnings.length > 0 && (
        <ul aria-label="Warnings" className="space-y-1 text-sm text-amber-200/90">
          {location.footprintWarnings.map((warning) => (
            <li key={warning} className="flex items-start gap-2">
              <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0 text-amber-400" />
              {warning}
            </li>
          ))}
        </ul>
      )}
      {location.notes && <p className="border-l-2 border-stone-700 pl-3 text-sm whitespace-pre-line text-stone-300 italic">{location.notes}</p>}

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
        <div className="flex justify-end">
          <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
            Remove
          </Button>
        </div>
      )}
    </article>
  )
}
