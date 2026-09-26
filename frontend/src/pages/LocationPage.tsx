import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { fieldErrors, isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Location } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { useStoreLocation, useUpdateLocation } from '../components/locationHooks'
import { FitScore, LocationBadges, StatusSelect } from '../components/locationParts'
import { locationPin } from '../components/map/locationPin'
import type { MapPin } from '../components/map/types'
import { VenueMap } from '../components/map/VenueMap'
import { Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { sceneLabel } from '../lib/format'
import { formatCoordinates, osmLink, parseCoordinates, roundCoordinates } from '../lib/geo'
import { blankToNull } from '../lib/text'
import { displayHost, safeHttpUrl } from '../lib/url'
import { LogisticsSection } from './LogisticsSection'
import { NotFoundPage } from './NotFoundPage'
import { OutreachSection } from './OutreachSection'

export function LocationPage() {
  const { locationId = '' } = useParams()
  const { api } = useSession()
  const location = useQuery({ queryKey: queryKeys.location(locationId), queryFn: () => api.locations.get(locationId) })

  if (location.isPending) return <Spinner label="Loading location" />
  if (location.isError) {
    if (isNotFound(location.error)) return <NotFoundPage />
    return <ErrorAlert error={location.error} onRetry={() => location.refetch()} />
  }
  return <LocationDetails location={location.data} />
}

function LocationDetails({ location }: { location: Location }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const update = useUpdateLocation(location)
  // Only for the breadcrumb; the page works without it.
  const scene = useQuery({ queryKey: queryKeys.scene(location.sceneId), queryFn: () => api.scenes.get(location.sceneId) })
  const scenePath = `/scenes/${location.sceneId}`
  const sourceUrl = safeHttpUrl(location.sourceUrl)

  const remove = useMutation({
    mutationFn: () => api.locations.remove(location.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.location(location.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.locationList(location.sceneId) })
      navigate(scenePath, { replace: true })
    },
  })

  return (
    <div className="space-y-8">
      <Link to={scenePath} className="text-sm text-stone-400 hover:text-stone-200">
        ← {scene.data ? sceneLabel(scene.data) : 'Scene'}
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-start gap-4">
          {location.fitScore != null && <FitScore score={location.fitScore} />}
          <div className="min-w-0 space-y-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold">{location.name}</h1>
              <LocationBadges location={location} />
            </div>
            {location.address && <p className="text-stone-400">{location.address}</p>}
            {sourceUrl && (
              <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="text-sm text-amber-300 underline hover:text-amber-200">
                {displayHost(sourceUrl)}
                <span className="sr-only"> (opens in a new tab)</span>
              </a>
            )}
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusSelect location={location} update={update} />
          <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
            Remove
          </Button>
        </div>
      </header>

      {confirmingDelete && (
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
      )}

      <Assessment location={location} />
      <Notes location={location} update={update} />
      <Position location={location} />
      <LogisticsSection location={location} />
      <OutreachSection location={location} />
    </div>
  )
}

/** What the AI made of the venue; venues added by hand have none. */
function Assessment({ location }: { location: Location }) {
  if (location.fitScore == null) return null
  return (
    <section aria-labelledby="assessment-heading" className="space-y-3 rounded-lg border border-stone-800 bg-stone-900/60 p-6">
      <h2 id="assessment-heading" className="text-lg font-semibold">
        Assessment
      </h2>
      {location.fitReason && <p className="text-stone-200">{location.fitReason}</p>}
      {location.frictionNote && <p className="text-sm text-stone-400">{location.frictionNote}</p>}
      {location.footprintWarnings.length > 0 && (
        <ul aria-label="Warnings" className="list-inside list-disc space-y-1 text-sm text-amber-200">
          {location.footprintWarnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
      {location.sourceExcerpt && (
        <figure className="space-y-1">
          <figcaption className="text-xs font-medium tracking-wide text-stone-500 uppercase">From the source</figcaption>
          <blockquote className="border-l-2 border-stone-700 pl-3 text-sm text-stone-300">{location.sourceExcerpt}</blockquote>
        </figure>
      )}
    </section>
  )
}

function Notes({ location, update }: { location: Location; update: ReturnType<typeof useUpdateLocation> }) {
  const [notes, setNotes] = useState(location.notes ?? '')
  const changed = (blankToNull(notes) ?? null) !== (location.notes ?? null)

  function save(e: FormEvent) {
    e.preventDefault()
    if (changed) update.mutate({ status: location.status, notes: blankToNull(notes) })
  }

  return (
    <section aria-labelledby="notes-heading" className="space-y-3">
      <h2 id="notes-heading" className="text-lg font-semibold">
        Notes
      </h2>
      <form onSubmit={save} className="space-y-3" noValidate>
        <ErrorAlert error={update.error} />
        <TextArea
          label="Your notes on this venue"
          maxLength={4000}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          error={fieldErrors(update.error).notes}
          hint="Private to you. Never shared with the venue."
        />
        <div className="flex justify-end">
          <Button type="submit" variant="secondary" busy={update.isPending} disabled={!changed}>
            Save notes
          </Button>
        </div>
      </form>
    </section>
  )
}

/** Where the venue is: logistics are worked out for this spot. */
function Position({ location }: { location: Location }) {
  const { api } = useSession()
  const store = useStoreLocation()
  const current = location.latitude != null && location.longitude != null ? { latitude: location.latitude, longitude: location.longitude } : null
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState('')
  const parsed = parseCoordinates(text)
  const pin = locationPin(location, { withLink: false })
  // While editing, the pin follows what has been typed or picked, as long as it reads as coordinates.
  const placed = parsed.ok && parsed.value ? parsed.value : current
  const editedPin: MapPin | null = placed && { ...(pin ?? { id: location.id, label: location.name, tone: 'neutral' }), position: placed }

  const relocate = useMutation({
    mutationFn: (body: { latitude: number; longitude: number }) => api.locations.updateCoordinates(location.id, body),
    onSuccess: (updated) => {
      store(updated)
      setEditing(false)
    },
  })

  function save(e: FormEvent) {
    e.preventDefault()
    if (parsed.ok && parsed.value) relocate.mutate(parsed.value)
  }

  const server = fieldErrors(relocate.error)
  return (
    <section aria-labelledby="position-heading" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 id="position-heading" className="text-lg font-semibold">
          Position
        </h2>
        {!editing && (
          <Button
            variant="secondary"
            onClick={() => {
              setText(current ? formatCoordinates(current) : '')
              relocate.reset()
              setEditing(true)
            }}
          >
            {current ? 'Move pin' : 'Set coordinates'}
          </Button>
        )}
      </div>

      {editing ? (
        <form onSubmit={save} className="space-y-3 rounded-lg border border-stone-800 bg-stone-900/60 p-5" noValidate>
          <ErrorAlert error={relocate.error} />
          <TextField
            label="Coordinates"
            placeholder="40.6745, -73.9633"
            hint="Latitude, longitude in decimal degrees, as copied from a map. Or click the spot on the map below."
            value={text}
            onChange={(e) => setText(e.target.value)}
            error={(!parsed.ok ? parsed.error : (server.latitude ?? server.longitude)) || undefined}
            autoFocus
          />
          <VenueMap
            pins={editedPin ? [editedPin] : []}
            label="Map: click to place the pin"
            onPick={(spot) => setText(formatCoordinates(roundCoordinates(spot)))}
            className="h-72"
          />
          {location.logistics && (
            <p role="note" className="rounded-md border border-amber-900 bg-amber-950/40 px-4 py-3 text-sm text-amber-200">
              Moving the pin discards the logistics report, which was worked out for the old spot.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setEditing(false)} disabled={relocate.isPending}>
              Cancel
            </Button>
            <Button type="submit" busy={relocate.isPending} disabled={!parsed.ok || !parsed.value}>
              Save position
            </Button>
          </div>
        </form>
      ) : current && pin ? (
        <div className="space-y-2">
          <VenueMap pins={[pin]} label={`Map of ${location.name}`} className="h-64" />
          <p className="text-sm text-stone-300">
            {formatCoordinates(current)} ·{' '}
            <a href={osmLink(current)} target="_blank" rel="noopener noreferrer" className="text-amber-300 underline hover:text-amber-200">
              View on OpenStreetMap
              <span className="sr-only"> (opens in a new tab)</span>
            </a>
          </p>
        </div>
      ) : (
        <p className="text-sm text-stone-400">
          Not set. Logistics look the venue up from its {location.address ? 'address' : 'name'}; set the coordinates if that finds the wrong place.
        </p>
      )}
    </section>
  )
}
