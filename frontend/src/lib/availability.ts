import type { AvailabilityState } from '../api/types'
import { formatDate } from './format'

/** Each state in words, with the badge tone it is shown in. */
export const availabilityLabels: Record<AvailabilityState, { label: string; tone: 'neutral' | 'cue' | 'green' | 'red' }> = {
  PENCILLED: { label: 'Pencilled', tone: 'neutral' },
  HELD: { label: 'Held', tone: 'cue' },
  CONFIRMED: { label: 'Confirmed', tone: 'green' },
  UNAVAILABLE: { label: 'Unavailable', tone: 'red' },
}

export const availabilityStates = Object.keys(availabilityLabels) as AvailabilityState[]

/** Only a pencil or a hold lapses. */
export function canExpire(state: AvailabilityState): boolean {
  return state === 'PENCILLED' || state === 'HELD'
}

/** "Held until 28 Oct 2026", "Confirmed". */
export function bookingText(booking: { state: AvailabilityState; holdExpiresOn: string | null }): string {
  const label = availabilityLabels[booking.state].label
  return booking.holdExpiresOn && canExpire(booking.state) ? `${label} until ${formatDate(booking.holdExpiresOn)}` : label
}

/** "07:30" from the server's "07:30:00". */
export function clockTime(time: string | null): string | null {
  return time ? time.slice(0, 5) : null
}

/** "07:30–16:00", "Call 07:30", "Wrap 16:00", or null when neither is set. */
export function timeWindow(call: string | null, wrap: string | null): string | null {
  const from = clockTime(call)
  const to = clockTime(wrap)
  if (from && to) return `${from}–${to}`
  if (from) return `Call ${from}`
  if (to) return `Wrap ${to}`
  return null
}

/** Days from `from` to `to` inclusive (1 for the same day), or 0 when `to` is before `from`. */
export function dayCount(from: string, to: string): number {
  const days = Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1
  return Math.max(days, 0)
}
