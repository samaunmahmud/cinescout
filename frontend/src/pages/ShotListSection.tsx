import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Camera, Plus, Sun, Sunset } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { fieldErrors } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Scene, Shot, ShotRequest, ShotSize } from '../api/types'
import { useSession } from '../auth/context'
import { useCanEdit } from '../components/projectRole'
import { EmptyState, Section } from '../components/surfaces'
import { Badge, Button, ErrorAlert, SelectField, Spinner, TextField } from '../components/ui'
import { facings, facingText, lightLabels, missingSun, shotSizeLabels, shotSizes } from '../lib/shots'

/**
 * The scene's shot list in shooting order. Each shot says how the sun will light it at its planned time, from which
 * way the camera faces, at the scene's confirmed venue on its first shoot day.
 */
export function ShotListSection({ scene }: { scene: Scene }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const canEdit = useCanEdit()
  const shots = useQuery({ queryKey: queryKeys.shots(scene.id), queryFn: () => api.shots.list(scene.id) })
  const store = (list: Shot[]) => queryClient.setQueryData(queryKeys.shots(scene.id), list)
  const [adding, setAdding] = useState(false)
  const count = shots.data?.length ?? 0
  const done = shots.data?.filter((shot) => shot.done).length ?? 0

  return (
    <Section
      titleId="shots-heading"
      title="Shot list"
      eyebrow="In shooting order"
      icon={Camera}
      description={count > 0 ? `${done} of ${count} shot` : 'Each shot says where the sun will be when you shoot it.'}
      actions={
        canEdit &&
        !adding && (
          <Button variant="secondary" onClick={() => setAdding(true)}>
            <Plus aria-hidden className="size-4" />
            Add a shot
          </Button>
        )
      }
    >
      <div className="space-y-4">
        {adding && (
          <ShotForm
            submitLabel="Add the shot"
            onCancel={() => setAdding(false)}
            save={(body) => api.shots.add(scene.id, body)}
            onSaved={(list) => {
              store(list)
              setAdding(false)
            }}
          />
        )}
        {shots.isPending ? (
          <Spinner label="Loading the shot list" />
        ) : shots.isError ? (
          <ErrorAlert error={shots.error} onRetry={() => shots.refetch()} />
        ) : shots.data.length === 0 ? (
          !adding && <EmptyState icon={Camera}>No shots yet. List them in the order you will shoot them.</EmptyState>
        ) : (
          <ol aria-label="Shots" className="divide-y divide-line rounded-lg border border-line bg-paper">
            {shots.data.map((shot, index) => (
              <ShotRow key={shot.id} shot={shot} first={index === 0} last={index === shots.data.length - 1} onSaved={store} />
            ))}
          </ol>
        )}
      </div>
    </Section>
  )
}

function ShotRow({ shot, first, last, onSaved }: { shot: Shot; first: boolean; last: boolean; onSaved: (list: Shot[]) => void }) {
  const { api } = useSession()
  const canEdit = useCanEdit()
  const [editing, setEditing] = useState(false)
  const update = useMutation({ mutationFn: (body: ShotRequest) => api.shots.update(shot.id, body), onSuccess: onSaved })
  const move = useMutation({ mutationFn: (direction: 'EARLIER' | 'LATER') => api.shots.move(shot.id, direction), onSuccess: onSaved })
  const remove = useMutation({ mutationFn: () => api.shots.remove(shot.id), onSuccess: onSaved })

  if (editing) {
    return (
      <li className="p-4">
        <ShotForm
          shot={shot}
          submitLabel="Save the shot"
          onCancel={() => setEditing(false)}
          save={(body) => api.shots.update(shot.id, body)}
          onSaved={(list) => {
            onSaved(list)
            setEditing(false)
          }}
          onRemove={() => remove.mutate()}
        />
      </li>
    )
  }
  return (
    <li className={`flex gap-3 p-4 ${shot.done ? 'bg-ground' : ''}`}>
      <div className="flex flex-col items-center gap-1">
        <span className="font-marker text-2xl leading-none text-cue-ink">{shot.number}</span>
        {canEdit && (
          <input
            type="checkbox"
            aria-label={`Shot ${shot.number} done`}
            checked={shot.done}
            disabled={update.isPending}
            onChange={(e) => update.mutate({ ...requestOf(shot), done: e.target.checked })}
            className="mt-1 size-4 accent-[var(--color-go-mid)]"
          />
        )}
      </div>
      <div className="min-w-0 flex-1 space-y-1.5">
        <p className={`font-semibold ${shot.done ? 'text-muted line-through' : ''}`}>{shot.description}</p>
        <p className="flex flex-wrap gap-x-3 gap-y-1 text-sm text-muted">
          {shot.size && <span>{shotSizeLabels[shot.size]}</span>}
          {shot.cameraBearing != null && <span>{facingText(shot.cameraBearing)}</span>}
          {shot.plannedTime && <span>{shot.plannedTime.slice(0, 5)}</span>}
          {shot.venueName && shot.locationId && (
            <Link to={`/locations/${shot.locationId}`} className="underline-offset-2 hover:underline">
              {shot.venueName}
            </Link>
          )}
        </p>
        <SunLine shot={shot} />
        <ErrorAlert error={update.error ?? move.error ?? remove.error} />
      </div>
      {canEdit && (
        <div className="flex flex-col items-end gap-1">
          <div className="flex gap-1">
            <button
              type="button"
              aria-label={`Move shot ${shot.number} earlier`}
              disabled={first || move.isPending}
              onClick={() => move.mutate('EARLIER')}
              className="rounded p-1 text-muted hover:bg-ground hover:text-ink disabled:opacity-30"
            >
              <ArrowUp aria-hidden className="size-4" />
            </button>
            <button
              type="button"
              aria-label={`Move shot ${shot.number} later`}
              disabled={last || move.isPending}
              onClick={() => move.mutate('LATER')}
              className="rounded p-1 text-muted hover:bg-ground hover:text-ink disabled:opacity-30"
            >
              <ArrowDown aria-hidden className="size-4" />
            </button>
          </div>
          <Button variant="ghost" onClick={() => setEditing(true)} aria-label={`Edit shot ${shot.number}`}>
            Edit
          </Button>
        </div>
      )}
    </li>
  )
}

function SunLine({ shot }: { shot: Shot }) {
  if (!shot.sun) {
    return shot.sunMissing ? <p className="text-sm text-subtle">{missingSun[shot.sunMissing]}</p> : null
  }
  const { light, golden, text } = shot.sun
  const Icon = light === 'SUN_DOWN' || golden ? Sunset : Sun
  return (
    <p className="flex flex-wrap items-center gap-2 text-sm text-graphite">
      <Icon aria-hidden className="size-4 shrink-0 text-cue-ink" />
      {light && <Badge tone={light === 'BACKLIT' ? 'red' : light === 'FRONT_LIT' ? 'green' : 'cue'}>{lightLabels[light]}</Badge>}
      {golden && <Badge tone="cue">Golden hour</Badge>}
      <span>{text}</span>
    </p>
  )
}

function requestOf(shot: Shot): ShotRequest {
  return {
    description: shot.description,
    size: shot.size,
    cameraBearing: shot.cameraBearing,
    plannedTime: shot.plannedTime ? shot.plannedTime.slice(0, 5) : null,
    done: shot.done,
    locationId: shot.locationId && !shot.venueConfirmed ? shot.locationId : null,
  }
}

function ShotForm({
  shot,
  submitLabel,
  save,
  onCancel,
  onSaved,
  onRemove,
}: {
  shot?: Shot
  submitLabel: string
  save: (body: ShotRequest) => Promise<Shot[]>
  onCancel: () => void
  onSaved: (list: Shot[]) => void
  onRemove?: () => void
}) {
  const [description, setDescription] = useState(shot?.description ?? '')
  const [size, setSize] = useState<ShotSize | ''>(shot?.size ?? '')
  const [bearing, setBearing] = useState(shot?.cameraBearing?.toString() ?? '')
  const [time, setTime] = useState(shot?.plannedTime?.slice(0, 5) ?? '')
  const mutation = useMutation({
    mutationFn: () =>
      save({
        description: description.trim(),
        size: size || null,
        cameraBearing: bearing === '' ? null : Number(bearing),
        plannedTime: time || null,
        done: shot?.done ?? false,
        locationId: shot && shot.locationId && !shot.venueConfirmed ? shot.locationId : null,
      }),
    onSuccess: onSaved,
  })
  const errors = fieldErrors(mutation.error)
  const knownFacing = bearing === '' || facings.some((facing) => String(facing.bearing) === bearing)

  function submit(e: FormEvent) {
    e.preventDefault()
    mutation.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-lg border border-line bg-paper p-4" noValidate>
      <ErrorAlert error={mutation.error} />
      <TextField
        label="What the shot is"
        required
        maxLength={500}
        placeholder="Wide over the rooftop as Anna arrives"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        error={errors.description}
        autoFocus
      />
      <div className="grid gap-3 sm:grid-cols-3">
        <SelectField label="Size" value={size} onChange={(e) => setSize(e.target.value as ShotSize | '')}>
          <option value="">Not set</option>
          {shotSizes.map((value) => (
            <option key={value} value={value}>
              {shotSizeLabels[value]}
            </option>
          ))}
        </SelectField>
        <SelectField label="Camera faces" value={bearing} onChange={(e) => setBearing(e.target.value)} error={errors.cameraBearing}>
          <option value="">Not set</option>
          {!knownFacing && <option value={bearing}>{bearing}°</option>}
          {facings.map((facing) => (
            <option key={facing.bearing} value={facing.bearing}>
              {facing.label}
            </option>
          ))}
        </SelectField>
        <TextField label="Time" type="time" value={time} onChange={(e) => setTime(e.target.value)} error={errors.plannedTime} />
      </div>
      <div className="flex flex-wrap justify-between gap-2">
        {onRemove ? (
          <Button variant="ghost" onClick={onRemove}>
            Remove the shot
          </Button>
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onCancel} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" busy={mutation.isPending} disabled={!description.trim()}>
            {submitLabel}
          </Button>
        </div>
      </div>
    </form>
  )
}
