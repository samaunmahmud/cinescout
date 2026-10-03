import type { DirectorCall, DirectorVerdict } from '../api/types'

/** How each call reads on a stamp, and its ink. */
export const verdictStamps: Record<DirectorVerdict, { word: string; tone: 'go' | 'cue' | 'stop' }> = {
  APPROVE: { word: 'Approved', tone: 'go' },
  MAYBE: { word: 'Maybe', tone: 'cue' },
  NO: { word: 'Passed', tone: 'stop' },
}

/** The calls on each venue, keyed by its id, latest first as the server sends them. */
export function callsByVenue(calls: DirectorCall[]): Map<string, DirectorCall[]> {
  const byVenue = new Map<string, DirectorCall[]>()
  for (const call of calls) byVenue.set(call.locationId, [...(byVenue.get(call.locationId) ?? []), call])
  return byVenue
}
