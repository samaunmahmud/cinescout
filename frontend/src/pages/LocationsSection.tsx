import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { BookingFriction, Location, LocationStatus, Scene } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { Badge, Button, ErrorAlert, Spinner } from '../components/ui'
import { scoutingSummary } from '../lib/format'
import { displayHost, safeHttpUrl } from '../lib/url'

const statusLabels: Record<LocationStatus, string> = {
  SUGGESTED: 'Suggested',
  SHORTLISTED: 'Shortlisted',
  REJECTED: 'Rejected',
  CONTACTED: 'Contacted',
  CONFIRMED: 'Confirmed',
}

const friction: Record<BookingFriction, { text: string; tone: 'green' | 'amber' | 'red' }> = {
  PUBLIC: { text: 'Public space', tone: 'green' },
  COMMERCIAL: { text: 'Business', tone: 'amber' },
  PRIVATE: { text: 'Private property', tone: 'red' },
}

/**
 * The scene's candidate venues and the button that scouts for more. `locationArea` is the project's search
 * area: undefined while the project loads, null when it has none (scouting then has nowhere to search).
 */
export function LocationsSection({ scene, locationArea }: { scene: Scene; locationArea: string | null | undefined }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const locations = useQuery({ queryKey: queryKeys.locationList(scene.id), queryFn: () => api.locations.list(scene.id) })

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
  const hasLocations = (locations.data?.length ?? 0) > 0

  return (
    <section aria-labelledby="locations-heading" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 id="locations-heading" className="text-lg font-semibold">
            Locations
          </h2>
          {locationArea && <p className="text-sm text-stone-400">Scouting in {locationArea}</p>}
        </div>
        <Button variant={hasLocations ? 'secondary' : 'primary'} busy={scout.isPending} disabled={noArea} onClick={() => scout.mutate()}>
          {hasLocations ? 'Scout again' : 'Scout locations'}
        </Button>
      </div>

      {noArea && (
        <p className="rounded-md border border-amber-900 bg-amber-950/40 px-4 py-3 text-sm text-amber-200">
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
          <p role="status" className="rounded-md border border-emerald-900 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-200">
            {scoutingSummary(scout.data)}
          </p>
        )
      )}

      {locations.isPending ? (
        <Spinner label="Loading locations" />
      ) : locations.isError ? (
        <ErrorAlert error={locations.error} onRetry={() => locations.refetch()} />
      ) : locations.data.length === 0 ? (
        !scout.isPending && (
          <p className="rounded-lg border border-dashed border-stone-800 px-6 py-10 text-center text-stone-400">
            No locations yet. Scouting searches the web for real venues that suit the scene and rates how well each one fits.
          </p>
        )
      ) : (
        <ul aria-label="Candidate locations" className="space-y-3">
          {locations.data.map((location) => (
            <li key={location.id}>
              <LocationCard location={location} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function FitScore({ score }: { score: number }) {
  const tone = score >= 75 ? 'text-emerald-300 border-emerald-800' : score >= 50 ? 'text-amber-300 border-amber-800' : 'text-red-300 border-red-900'
  return (
    <span
      className={`flex size-12 shrink-0 flex-col items-center justify-center rounded-full border-2 ${tone}`}
      aria-label={`Fit ${score} out of 100`}
      title="How well the venue suits the scene, out of 100"
    >
      <span className="text-base leading-none font-bold">{score}</span>
    </span>
  )
}

function LocationCard({ location }: { location: Location }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const statusId = useId()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const listKey = queryKeys.locationList(location.sceneId)
  const sourceUrl = safeHttpUrl(location.sourceUrl)

  const update = useMutation({
    // A full replacement: the notes go back unchanged.
    mutationFn: (status: LocationStatus) => api.locations.update(location.id, { status, notes: location.notes }),
    onSuccess: (updated) =>
      queryClient.setQueryData<Location[]>(listKey, (list) => list?.map((l) => (l.id === updated.id ? updated : l))),
  })

  const remove = useMutation({
    mutationFn: () => api.locations.remove(location.id),
    onSuccess: () => queryClient.setQueryData<Location[]>(listKey, (list) => list?.filter((l) => l.id !== location.id)),
  })

  return (
    <article
      aria-label={location.name}
      className={`space-y-3 rounded-lg border border-stone-800 bg-stone-900/60 p-5 ${location.status === 'REJECTED' ? 'opacity-60' : ''}`}
    >
      <div className="flex items-start gap-4">
        {location.fitScore != null && <FitScore score={location.fitScore} />}
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold">{location.name}</h3>
            {location.bookingFriction && <Badge tone={friction[location.bookingFriction].tone}>{friction[location.bookingFriction].text}</Badge>}
            {location.fitScore == null && <Badge>Added by hand</Badge>}
          </div>
          {location.address && <p className="text-sm text-stone-400">{location.address}</p>}
          {sourceUrl && (
            <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="text-sm text-amber-300 underline hover:text-amber-200">
              {displayHost(sourceUrl)}
              <span className="sr-only"> (opens in a new tab)</span>
            </a>
          )}
        </div>
        <div className="shrink-0">
          <label htmlFor={statusId} className="sr-only">
            Status of {location.name}
          </label>
          <select
            id={statusId}
            value={location.status}
            disabled={update.isPending}
            onChange={(e) => update.mutate(e.target.value as LocationStatus)}
            className="rounded-md border border-stone-700 bg-stone-900 px-2 py-1.5 text-sm text-stone-100 focus:border-amber-400 focus:ring-1 focus:ring-amber-400 focus:outline-none disabled:opacity-50"
          >
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {location.fitReason && <p className="text-sm text-stone-200">{location.fitReason}</p>}
      {location.frictionNote && <p className="text-sm text-stone-400">{location.frictionNote}</p>}
      {location.footprintWarnings.length > 0 && (
        <ul aria-label="Warnings" className="list-inside list-disc space-y-1 text-sm text-amber-200">
          {location.footprintWarnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
      {location.notes && <p className="border-l-2 border-stone-700 pl-3 text-sm text-stone-300 italic">{location.notes}</p>}

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
