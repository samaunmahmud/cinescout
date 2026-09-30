import type { LocationStatus, OutreachStatus } from '../api/types'

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

/** How far an outreach email got, as the user reports it. */
export const outreachStatusLabels: Record<OutreachStatus, { label: string; tone: 'neutral' | 'amber' | 'green' }> = {
  DRAFT: { label: 'Draft', tone: 'neutral' },
  SENT: { label: 'Sent', tone: 'amber' },
  REPLIED: { label: 'Replied', tone: 'green' },
}

export const outreachStatuses = Object.keys(outreachStatusLabels) as OutreachStatus[]

export function isOutreachStatus(value: string | null): value is OutreachStatus {
  return value !== null && value in outreachStatusLabels
}
