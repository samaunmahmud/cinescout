import { useId } from 'react'
import type { BookingFriction, Location, LocationStatus } from '../api/types'
import { fitBand, frictionLabel, type FitBand } from '../lib/fit'
import { statusLabels } from '../lib/status'
import type { useUpdateLocation } from './locationHooks'
import { Badge } from './ui'

const frictionTones: Record<BookingFriction, 'green' | 'amber' | 'red'> = { PUBLIC: 'green', COMMERCIAL: 'amber', PRIVATE: 'red' }

/** A status dropdown that saves on change, keeping the notes as they are. */
export function StatusSelect({
  location,
  update,
}: {
  location: Pick<Location, 'name' | 'status' | 'notes'>
  update: ReturnType<typeof useUpdateLocation>
}) {
  const id = useId()
  return (
    <>
      <label htmlFor={id} className="sr-only">
        Status of {location.name}
      </label>
      <select
        id={id}
        value={location.status}
        disabled={update.isPending}
        onChange={(e) => update.mutate({ status: e.target.value as LocationStatus, notes: location.notes })}
        className="rounded-md border border-stone-700 bg-stone-900 px-2 py-1.5 text-sm text-stone-100 focus:border-amber-400 focus:ring-1 focus:ring-amber-400 focus:outline-none disabled:opacity-50"
      >
        {Object.entries(statusLabels).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
    </>
  )
}

const fitColours: Record<FitBand, { text: string; stroke: string }> = {
  good: { text: 'text-emerald-300', stroke: 'stroke-emerald-400' },
  fair: { text: 'text-amber-300', stroke: 'stroke-amber-400' },
  poor: { text: 'text-red-300', stroke: 'stroke-red-400' },
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
        <circle cx="24" cy="24" r={r} fill="none" strokeWidth="4" className="stroke-white/[0.08]" />
        <circle
          cx="24"
          cy="24"
          r={r}
          fill="none"
          strokeWidth="4"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - Math.max(0, Math.min(100, score)) / 100)}
          className={colour.stroke}
        />
      </svg>
      <span aria-hidden className={`font-display leading-none ${colour.text} ${size === 'lg' ? 'text-4xl' : 'text-xl'}`}>
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
