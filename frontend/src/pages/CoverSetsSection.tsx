import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Umbrella } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { CoverSet, Scene } from '../api/types'
import { useSession } from '../auth/context'
import { useCanEdit } from '../components/projectRole'
import { EmptyState, Section } from '../components/surfaces'
import { PlanB } from '../components/PlanB'
import { Button, ErrorAlert, SelectField, Spinner, TextField } from '../components/ui'

/** How many cover sets a scene may have (CoverService.MAX_COVERS). */
const MAX_COVERS = 5

/** Triggers offered as you type; any words will do. */
const triggerIdeas = ['if rain > 60%', 'if wind > 40 km/h', 'if the venue falls through', 'if we lose the light']

/**
 * Cover sets: the scene's backup venues, each one of its own candidates or a venue from the user's library (added to
 * the scene first), with when to switch to it. The schedule and call sheet list them under the scene.
 */
export function CoverSetsSection({ scene }: { scene: Scene }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const [adding, setAdding] = useState(false)
  const covers = useQuery({ queryKey: queryKeys.covers(scene.id), queryFn: () => api.covers.list(scene.id) })
  const full = (covers.data?.items.length ?? 0) >= MAX_COVERS

  return (
    <Section
      titleId="covers-heading"
      title="Cover sets"
      eyebrow="If the day goes wrong"
      icon={Umbrella}
      description="Backup venues for this scene, and when to switch to them."
      actions={
        canEdit &&
        covers.isSuccess &&
        !adding &&
        !full && (
          <Button variant="secondary" onClick={() => setAdding(true)}>
            Add a cover set
          </Button>
        )
      }
    >
      {adding && <AddCover scene={scene} covers={covers.data?.items ?? []} onDone={() => setAdding(false)} />}
      {covers.isPending ? (
        <Spinner label="Loading cover sets" />
      ) : covers.isError ? (
        <ErrorAlert error={covers.error} onRetry={() => covers.refetch()} />
      ) : covers.data.items.length === 0 ? (
        !adding && <EmptyState icon={Umbrella}>No cover set yet. Pick a backup venue in case of rain, wind or a cancellation.</EmptyState>
      ) : (
        <ul aria-label="Cover sets" className="divide-y divide-line-soft board-card rounded-lg bg-paper">
          {covers.data.items.map((cover) => (
            <li key={cover.id}>
              <CoverRow cover={cover} defaultDay={scene.shootDateStart} />
            </li>
          ))}
        </ul>
      )}
      {full && canEdit && <p className="text-sm text-muted">A scene can have {MAX_COVERS} cover sets; remove one to add another.</p>}
    </Section>
  )
}

function CoverRow({ cover, defaultDay }: { cover: CoverSet; defaultDay: string | null }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [trigger, setTrigger] = useState('')
  const done = () => queryClient.invalidateQueries({ queryKey: queryKeys.covers(cover.sceneId) })
  const save = useMutation({
    mutationFn: () => api.covers.setTrigger(cover.id, trigger.trim() || null),
    onSuccess: () => {
      setEditing(false)
      return done()
    },
  })
  const remove = useMutation({ mutationFn: () => api.covers.remove(cover.id), onSuccess: done })

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  return (
    <div className="space-y-3 px-4 py-3">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <Link to={`/locations/${cover.locationId}`} className="font-semibold text-ink hover:text-cue-deep hover:underline">
            {cover.venueName}
          </Link>
          {cover.address && <p className="truncate text-sm text-muted">{cover.address}</p>}
          {cover.status === 'CONFIRMED' && <p className="text-sm text-go-ink">Now confirmed for the scene, so no longer listed as its cover.</p>}
          <p className={`font-marker text-lg leading-tight ${cover.trigger ? 'text-cue-ink' : 'text-subtle'}`}>
            {cover.trigger ?? 'No trigger set'}
          </p>
        </div>
        {canEdit && !editing && (
          <div className="flex gap-2">
            <Button
              variant="ghost"
              aria-label={`Change when to switch to ${cover.venueName}`}
              onClick={() => {
                setTrigger(cover.trigger ?? '')
                save.reset()
                setEditing(true)
              }}
            >
              Trigger
            </Button>
            <Button variant="ghost" busy={remove.isPending} aria-label={`Stop using ${cover.venueName} as a cover set`} onClick={() => remove.mutate()}>
              Remove
            </Button>
          </div>
        )}
      </div>
      <ErrorAlert error={remove.error} />
      {canEdit && !editing && cover.status !== 'CONFIRMED' && cover.status !== 'REJECTED' && (
        <PlanB locationId={cover.locationId} venueName={cover.venueName} defaultDay={defaultDay} />
      )}
      {editing && (
        <form onSubmit={submit} aria-label={`When to switch to ${cover.venueName}`} className="flex flex-wrap items-end gap-3 rounded-lg bg-ground p-3 ring-1 ring-line">
          <ErrorAlert error={save.error} />
          <TriggerField value={trigger} onChange={setTrigger} />
          <div className="flex gap-2">
            <Button type="submit" busy={save.isPending}>
              Save
            </Button>
            <Button variant="ghost" disabled={save.isPending} onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}

function TriggerField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const listId = useId()
  return (
    <div className="min-w-56 flex-1">
      <TextField label="When to switch" placeholder="if rain > 60%" maxLength={200} list={listId} value={value} onChange={(e) => onChange(e.target.value)} />
      <datalist id={listId}>
        {triggerIdeas.map((idea) => (
          <option key={idea} value={idea} />
        ))}
      </datalist>
    </div>
  )
}

/** Picks the venue (a candidate of the scene, or one from the library) and the trigger. */
function AddCover({ scene, covers, onDone }: { scene: Scene; covers: CoverSet[]; onDone: () => void }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [choice, setChoice] = useState('')
  const [trigger, setTrigger] = useState('')
  const [missing, setMissing] = useState(false)
  const candidates = useQuery({ queryKey: queryKeys.locationTop(scene.id), queryFn: () => api.locations.listTop(scene.id) })
  const library = useQuery({ queryKey: queryKeys.libraryPage('', null, 0), queryFn: () => api.library.list('', null, 0) })

  const taken = new Set(covers.map((cover) => cover.locationId))
  const options = (candidates.data?.items ?? []).filter((location) => location.status !== 'CONFIRMED' && !taken.has(location.id))
  const libraryVenues = library.data?.items ?? []

  const add = useMutation({
    mutationFn: async () => {
      const [kind, id] = choice.split(':')
      let locationId = id
      if (kind === 'library') {
        // A library venue joins the scene as a candidate first; a cover is always one of the scene's own.
        const location = await api.library.addToScene(scene.id, id)
        queryClient.invalidateQueries({ queryKey: queryKeys.locationList(scene.id) })
        queryClient.invalidateQueries({ queryKey: queryKeys.locationTop(scene.id) })
        locationId = location.id
      }
      return api.covers.add(scene.id, locationId, trigger.trim() || null)
    },
    onSuccess: () => {
      onDone()
      return queryClient.invalidateQueries({ queryKey: queryKeys.covers(scene.id) })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!choice) {
      setMissing(true)
      return
    }
    add.mutate()
  }

  if (candidates.isPending) return <Spinner label="Loading the scene's venues" />
  return (
    <form onSubmit={submit} aria-label="New cover set" className="space-y-3 rounded-lg border-2 border-dashed border-ink/40 bg-paper p-4" noValidate>
      <ErrorAlert error={add.error ?? candidates.error} />
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-56 flex-1">
          <SelectField
            label="Backup venue"
            value={choice}
            error={missing ? 'Pick a venue.' : undefined}
            onChange={(e) => {
              setChoice(e.target.value)
              setMissing(false)
            }}
          >
            <option value="">Choose a venue</option>
            {options.length > 0 && (
              <optgroup label="This scene's candidates">
                {options.map((location) => (
                  <option key={location.id} value={`location:${location.id}`}>
                    {location.name}
                  </option>
                ))}
              </optgroup>
            )}
            {libraryVenues.length > 0 && (
              <optgroup label="Your library">
                {libraryVenues.map((venue) => (
                  <option key={venue.id} value={`library:${venue.id}`}>
                    {venue.name}
                  </option>
                ))}
              </optgroup>
            )}
          </SelectField>
        </div>
        <TriggerField value={trigger} onChange={setTrigger} />
      </div>
      {options.length === 0 && libraryVenues.length === 0 && !library.isPending && (
        <p className="text-sm text-muted">No other venue to choose: scout the scene or add a venue by hand first.</p>
      )}
      <div className="flex gap-2">
        <Button type="submit" busy={add.isPending}>
          Add cover set
        </Button>
        <Button variant="ghost" disabled={add.isPending} onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
