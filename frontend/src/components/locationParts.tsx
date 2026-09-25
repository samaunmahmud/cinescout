import { useId } from 'react'
import type { BookingFriction, Location, LocationStatus } from '../api/types'
import type { useUpdateLocation } from './locationHooks'
import { Badge } from './ui'

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

/** A status dropdown that saves on change, keeping the notes as they are. */
export function StatusSelect({ location, update }: { location: Location; update: ReturnType<typeof useUpdateLocation> }) {
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

export function FitScore({ score }: { score: number }) {
  const tone = score >= 75 ? 'text-emerald-300 border-emerald-800' : score >= 50 ? 'text-amber-300 border-amber-800' : 'text-red-300 border-red-900'
  return (
    <span
      className={`flex size-12 shrink-0 items-center justify-center rounded-full border-2 text-base font-bold ${tone}`}
      aria-label={`Fit ${score} out of 100`}
      title="How well the venue suits the scene, out of 100"
    >
      {score}
    </span>
  )
}

/** The booking friction, or "Added by hand" for a venue the AI has not assessed. */
export function LocationBadges({ location }: { location: Location }) {
  return (
    <>
      {location.bookingFriction && <Badge tone={friction[location.bookingFriction].tone}>{friction[location.bookingFriction].text}</Badge>}
      {location.fitScore == null && <Badge>Added by hand</Badge>}
    </>
  )
}
