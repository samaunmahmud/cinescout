import type { LocationStatus } from '../api/types'

/** A candidate location's statuses, in the order the work moves through them. */
export const statusLabels: Record<LocationStatus, string> = {
  SUGGESTED: 'Suggested',
  SHORTLISTED: 'Shortlisted',
  REJECTED: 'Rejected',
  CONTACTED: 'Contacted',
  CONFIRMED: 'Confirmed',
}

export const locationStatuses = Object.keys(statusLabels) as LocationStatus[]

export function isLocationStatus(value: string | null): value is LocationStatus {
  return value !== null && value in statusLabels
}
