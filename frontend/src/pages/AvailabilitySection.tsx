import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarCheck, X } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Availability, AvailabilityRequest, AvailabilityState, Location } from '../api/types'
import { useSession } from '../auth/context'
import { useCanEdit } from '../components/projectRole'
import { EmptyState, Section } from '../components/surfaces'
import { Badge, Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { availabilityLabels, availabilityStates, canExpire, dayCount } from '../lib/availability'
import { formatDate, formatDay } from '../lib/format'
import { blankToNull } from '../lib/text'

const MAX_DAYS = 62

/**
 * The venue's days: pencilled, held (perhaps until a date), confirmed or unavailable. The schedule warns when a
 * confirmed venue is unavailable on a shoot day, or a hold lapses before it.
 */
export function AvailabilitySection({ location, shootStart, shootEnd }: { location: Location; shootStart: string | null; shootEnd: string | null }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const key = queryKeys.availability(location.id)
  const days = useQuery({ queryKey: key, queryFn: () => api.availability.list(location.id) })
  const [adding, setAdding] = useState(false)

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: key })
    queryClient.invalidateQueries({ queryKey: ['projects', 'schedule'] })
  }
  const set = useMutation({
    mutationFn: (body: AvailabilityRequest) => api.availability.set(location.id, body),
    onSuccess: () => {
      refresh()
      setAdding(false)
    },
  })
  const clear = useMutation({ mutationFn: (day: string) => api.availability.clear(location.id, day), onSuccess: refresh })

  return (
    <Section
      titleId="availability-heading"
      title="Holds and dates"
      eyebrow="Booking the days"
      icon={CalendarCheck}
      actions={
        canEdit &&
        !adding && (
          <Button
            variant="secondary"
            onClick={() => {
              set.reset()
              setAdding(true)
            }}
          >
            Set a day
          </Button>
        )
      }
    >
      {adding && (
        <DayForm
          initialFrom={shootStart ?? shootEnd ?? ''}
          initialTo={shootStart && shootEnd && shootEnd !== shootStart ? shootEnd : ''}
          busy={set.isPending}
          error={set.error}
          onSubmit={(body) => set.mutate(body)}
          onCancel={() => setAdding(false)}
        />
      )}
      <ErrorAlert error={clear.error} />
      {days.isPending ? (
        <Spinner label="Loading the venue’s days" />
      ) : days.isError ? (
        <ErrorAlert error={days.error} onRetry={() => days.refetch()} />
      ) : days.data.items.length === 0 ? (
        !adding && (
          <EmptyState icon={CalendarCheck}>
            Nothing recorded yet. Pencil the shoot days in when you first ask, and mark them held or confirmed as the venue agrees.
          </EmptyState>
        )
      ) : (
        <>
          <ul aria-label="Days at this venue" className="divide-y divide-line-soft board-card rounded-lg bg-white">
            {days.data.items.map((day) => (
              <li key={day.id}>
                <DayRow day={day} canEdit={canEdit} busy={clear.isPending && clear.variables === day.day} onClear={() => clear.mutate(day.day)} />
              </li>
            ))}
          </ul>
          {days.data.totalItems > days.data.items.length && (
            <p className="text-sm text-muted">Showing the first {days.data.items.length} of {days.data.totalItems} days.</p>
          )}
        </>
      )}
    </Section>
  )
}

function DayRow({ day, canEdit, busy, onClear }: { day: Availability; canEdit: boolean; busy: boolean; onClear: () => void }) {
  const state = availabilityLabels[day.state]
  return (
    <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1 px-4 py-3">
      <div className="min-w-0 space-y-1">
        <p className="font-semibold text-ink">{formatDay(day.day)}</p>
        <p className="flex flex-wrap items-center gap-2 text-sm text-muted">
          <Badge tone={state.tone}>{state.label}</Badge>
          {day.holdExpiresOn && <span>until {formatDate(day.holdExpiresOn)}</span>}
          {day.setByName && <span>· by {day.setByName}</span>}
        </p>
        {day.note && <p className="text-sm text-graphite">{day.note}</p>}
      </div>
      {canEdit && (
        <Button variant="ghost" aria-label={`Clear ${formatDay(day.day)}`} busy={busy} onClick={onClear}>
          {!busy && <X aria-hidden className="size-4" />}
          Clear
        </Button>
      )}
    </div>
  )
}

function DayForm({
  initialFrom,
  initialTo,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  initialFrom: string
  initialTo: string
  busy: boolean
  error: unknown
  onSubmit: (body: AvailabilityRequest) => void
  onCancel: () => void
}) {
  const [from, setFrom] = useState(initialFrom)
  const [to, setTo] = useState(initialTo)
  const [state, setState] = useState<AvailabilityState>('PENCILLED')
  const [expires, setExpires] = useState('')
  const [note, setNote] = useState('')
  const server = fieldErrors(error)
  const count = from && to ? dayCount(from, to) : 1
  const rangeError = from && to && count === 0 ? 'The last day cannot be before the first.' : count > MAX_DAYS ? `At most ${MAX_DAYS} days at once.` : undefined
  const valid = from !== '' && !rangeError

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!valid) return
    onSubmit({
      from,
      to: to || null,
      state,
      holdExpiresOn: canExpire(state) && expires ? expires : null,
      note: blankToNull(note),
    })
  }

  return (
    <form onSubmit={submit} aria-label="Set a day" className="space-y-4 board-card rounded-lg bg-white p-5" noValidate>
      <ErrorAlert error={error} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Day" type="date" required value={from} onChange={(e) => setFrom(e.target.value)} error={server.from} />
        <TextField
          label="Last day"
          type="date"
          hint="Optional, for a run of days."
          value={to}
          onChange={(e) => setTo(e.target.value)}
          error={rangeError ?? server.to}
        />
      </div>
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-graphite">State</legend>
        <div className="flex flex-wrap gap-2">
          {availabilityStates.map((value) => (
            <label
              key={value}
              className="flex cursor-pointer items-center gap-2 rounded-md border border-line px-3 py-2 text-sm has-checked:border-ink has-checked:bg-cue-wash"
            >
              <input type="radio" name="availability-state" value={value} checked={state === value} onChange={() => setState(value)} className="accent-[#c2410c]" />
              {availabilityLabels[value].label}
            </label>
          ))}
        </div>
      </fieldset>
      {canExpire(state) && (
        <TextField
          label="Lapses on"
          type="date"
          hint="Optional: the day the venue lets the hold go unless you renew it."
          value={expires}
          onChange={(e) => setExpires(e.target.value)}
          error={server.holdExpiresOn}
        />
      )}
      <TextArea label="Note" maxLength={500} rows={2} placeholder="Held by Jo, call back Friday" value={note} onChange={(e) => setNote(e.target.value)} error={server.note} />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" busy={busy} disabled={!valid}>
          {count > 1 ? `Set ${count} days` : 'Set day'}
        </Button>
      </div>
    </form>
  )
}
