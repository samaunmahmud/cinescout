import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Umbrella } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { formatDate } from '../lib/format'
import { useSession } from '../auth/context'
import { Button, ErrorAlert, TextField } from './ui'

/**
 * Weather plan B for a backup venue: pencil it for the day and have the AI draft the email asking its owner to hold it.
 * With `day` given (a weather alert) it is one click; without, it asks for the day first, starting at `defaultDay`.
 */
export function PlanB({
  locationId,
  venueName,
  day,
  reason,
  defaultDay,
}: {
  locationId: string
  venueName: string
  day?: string
  reason?: string
  defaultDay?: string | null
}) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [asking, setAsking] = useState(false)
  const [chosenDay, setChosenDay] = useState(defaultDay ?? '')
  const [why, setWhy] = useState('Rain forecast')
  const ask = useMutation({
    mutationFn: (body: { day: string; reason: string | null }) => api.planB(locationId, body),
    onSuccess: () => {
      setAsking(false)
      queryClient.invalidateQueries({
        queryKey: queryKeys.availability(locationId),
      })
      queryClient.invalidateQueries({ queryKey: ['outreach'] })
    },
  })

  if (ask.data) {
    const { draft, draftProblem } = ask.data
    return (
      <p role="status" className="text-sm text-go-ink">
        {venueName} pencilled for {formatDate(ask.variables?.day ?? '')}.{' '}
        {draft ? (
          <Link to={`/locations/${locationId}?tab=outreach`} className="font-semibold underline">
            Read the email to send
          </Link>
        ) : (
          <span className="text-stop-ink">{draftProblem}</span>
        )}
      </p>
    )
  }

  if (day) {
    return (
      <div className="space-y-1">
        <Button variant="secondary" busy={ask.isPending} onClick={() => ask.mutate({ day, reason: reason ?? null })}>
          {!ask.isPending && <Umbrella aria-hidden className="size-4" />}
          Plan B: ask {venueName}
        </Button>
        <ErrorAlert error={ask.error} />
      </div>
    )
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    ask.mutate({ day: chosenDay, reason: why.trim() || null })
  }

  return asking ? (
    <form onSubmit={submit} aria-label={`Plan B with ${venueName}`} className="space-y-3 rounded-lg bg-ground p-3 ring-1 ring-line">
      <p className="text-sm text-graphite">
        Pencils {venueName} for the day and has the AI draft an email asking its owner to hold it as a weather backup.
      </p>
      <ErrorAlert error={ask.error} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="Day" type="date" required value={chosenDay} onChange={(e) => setChosenDay(e.target.value)} />
        <TextField label="Why" maxLength={200} value={why} onChange={(e) => setWhy(e.target.value)} />
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={() => setAsking(false)} disabled={ask.isPending}>
          Cancel
        </Button>
        <Button type="submit" busy={ask.isPending} disabled={!chosenDay}>
          Pencil it and draft the email
        </Button>
      </div>
    </form>
  ) : (
    <Button variant="ghost" onClick={() => setAsking(true)} aria-label={`Plan B with ${venueName}`}>
      <Umbrella aria-hidden className="size-4" />
      Plan B
    </Button>
  )
}
