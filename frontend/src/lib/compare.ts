import type { Location } from '../api/types'

/** How many venues fit side by side. */
export const MAX_COMPARED = 4

const inTheRunning = new Set(['SHORTLISTED', 'CONTACTED', 'CONFIRMED'])

/**
 * Which of a scene's venues (given best fit first) to put side by side: the ones the user has picked out
 * (shortlisted, contacted or confirmed) when there are at least two, otherwise the best-fitting ones that
 * have not been rejected. `shortlist` says which of the two it was.
 */
export function venuesToCompare(locations: Location[]): { venues: Location[]; shortlist: boolean } {
  const picked = locations.filter((location) => inTheRunning.has(location.status))
  if (picked.length >= 2) return { venues: picked.slice(0, MAX_COMPARED), shortlist: true }
  return { venues: locations.filter((location) => location.status !== 'REJECTED').slice(0, MAX_COMPARED), shortlist: false }
}
