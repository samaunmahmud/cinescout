export type FitBand = 'good' | 'fair' | 'poor'

/** How a fit score (0-100) reads at a glance; the score circles and the map pins share these bands. */
export function fitBand(score: number): FitBand {
  return score >= 75 ? 'good' : score >= 50 ? 'fair' : 'poor'
}
