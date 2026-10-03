import { useId, useState } from 'react'
import type { BookingFriction, Location, LocationStatus } from '../api/types'
import { fitBand, frictionLabel, type FitBand } from '../lib/fit'
import { statusLabels } from '../lib/status'
import type { useUpdateLocation } from './locationHooks'
import { Badge, Button, TextField } from './ui'
import { useCanEdit } from './projectRole'

const frictionTones: Record<BookingFriction, 'green' | 'cue' | 'red'> = { PUBLIC: 'green', COMMERCIAL: 'cue', PRIVATE: 'red' }

/** Reasons the crew most often pass on a venue; one tap fills them in. */
const rejectionPresets = ['Too small', 'Too loud', 'Too expensive', 'Wrong look', 'Hard to get to', 'Not available'] as const

/**
 * A status dropdown that saves on change, keeping the notes as they are; for a viewer, the status as plain text.
 * Choosing Rejected first asks why (optional): the reasons steer the scene's next scouting runs.
 */
export function StatusSelect({
  location,
  update,
}: {
  location: Pick<Location, 'name' | 'status' | 'notes'>
  update: ReturnType<typeof useUpdateLocation>
}) {
  const id = useId()
  const canEdit = useCanEdit()
  const [rejecting, setRejecting] = useState(false)
  if (!canEdit) return <Badge>{statusLabels[location.status]}</Badge>
  return (
    <span className="relative inline-flex">
      <label htmlFor={id} className="sr-only">
        Status of {location.name}
      </label>
      <select
        id={id}
        value={rejecting ? 'REJECTED' : location.status}
        disabled={update.isPending}
        onChange={(e) => {
          const status = e.target.value as LocationStatus
          if (status === 'REJECTED') setRejecting(true)
          else update.mutate({ status, notes: location.notes })
        }}
        className="rounded-lg border-2 border-ink bg-white px-2 py-1.5 text-sm font-semibold text-ink focus:ring-4 focus:ring-cue/25 focus:outline-none disabled:opacity-50"
      >
        {Object.entries(statusLabels).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      {rejecting && (
        <RejectPanel
          venue={location.name}
          busy={update.isPending}
          onCancel={() => setRejecting(false)}
          onReject={(reason) =>
            update.mutate({ status: 'REJECTED', notes: location.notes, rejectionReason: reason }, { onSuccess: () => setRejecting(false) })
          }
        />
      )}
    </span>
  )
}

/** Why the crew is passing on a venue: a preset or their own words, or nothing at all. */
function RejectPanel({
  venue,
  busy,
  onCancel,
  onReject,
}: {
  venue: string
  busy: boolean
  onCancel: () => void
  onReject: (reason: string | null) => void
}) {
  const titleId = useId()
  const [reason, setReason] = useState('')
  return (
    <div
      role="dialog"
      aria-labelledby={titleId}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
      className="absolute top-full right-0 z-30 mt-2 w-[min(20rem,calc(100vw-2rem))] space-y-3 rounded-lg border-2 border-ink bg-white p-4 text-left shadow-[0_4px_0_var(--color-ink)]"
    >
      <p id={titleId} className="font-display text-lg leading-tight text-ink">
        Why pass on {venue}?
      </p>
      <div role="group" aria-label="Common reasons" className="flex flex-wrap gap-1.5">
        {rejectionPresets.map((preset) => (
          <button
            key={preset}
            type="button"
            aria-pressed={reason === preset}
            onClick={() => setReason(reason === preset ? '' : preset)}
            className={`rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset focus-visible:outline-2 focus-visible:outline-ink ${
              reason === preset ? 'bg-stop-wash text-stop-ink ring-stop' : 'bg-ground text-graphite ring-line hover:ring-ink'
            }`}
          >
            {preset}
          </button>
        ))}
      </div>
      <TextField label="Or in your words" value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} autoFocus />
      <p className="text-xs text-muted">Optional. The scene’s next scouting runs steer away from what you write here.</p>
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
        <Button variant="secondary" busy={busy} onClick={() => onReject(reason.trim() || null)}>
          Reject
        </Button>
      </div>
    </div>
  )
}

const fitColours: Record<FitBand, { stroke: string; label: string; ink: string }> = {
  good: { stroke: 'stroke-go-mid', label: 'Strong fit', ink: 'text-go-ink' },
  fair: { stroke: 'stroke-cue', label: 'Worth a look', ink: 'text-cue-ink' },
  poor: { stroke: 'stroke-subtle', label: 'Long shot', ink: 'text-muted' },
}

/** The band a score falls in, in words and in its colour: "Strong fit", "Worth a look", "Long shot". */
export function FitLabel({ score, className = '' }: { score: number; className?: string }) {
  const colour = fitColours[fitBand(score)]
  return <span className={`font-script text-xs font-bold tracking-[0.06em] uppercase ${colour.ink} ${className}`}>{colour.label}</span>
}

/** The fit score as a dial: the ring fills to the score. */
export function FitScore({ score, size = 'md' }: { score: number; size?: 'md' | 'lg' }) {
  const colour = fitColours[fitBand(score)]
  const r = 20
  const circumference = 2 * Math.PI * r
  return (
    <span
      className={`relative flex shrink-0 items-center justify-center ${size === 'lg' ? 'size-24' : 'size-14'}`}
      aria-label={`Fit ${score} out of 100`}
      title="How well the venue suits the scene, out of 100"
    >
      <svg viewBox="0 0 48 48" aria-hidden className="absolute inset-0 size-full -rotate-90">
        <circle cx="24" cy="24" r={r} fill="white" strokeWidth="5" className="stroke-line-soft" />
        <circle
          cx="24"
          cy="24"
          r={r}
          fill="none"
          strokeWidth="5"
          strokeLinecap="butt"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - Math.max(0, Math.min(100, score)) / 100)}
          className={colour.stroke}
        />
      </svg>
      <span aria-hidden className={`relative font-display leading-none font-extrabold text-ink ${size === 'lg' ? 'text-4xl' : 'text-xl'}`}>
        {score}
      </span>
    </span>
  )
}

/** The booking friction, or "Added by hand" for a venue the AI has not assessed. */
export function LocationBadges({ location }: { location: Pick<Location, 'bookingFriction' | 'fitScore'> }) {
  return (
    <>
      {location.bookingFriction && <Badge tone={frictionTones[location.bookingFriction]}>{frictionLabel(location.bookingFriction)}</Badge>}
      {location.fitScore == null && <Badge>Added by hand</Badge>}
    </>
  )
}
