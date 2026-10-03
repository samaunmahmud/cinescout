import type { ScoutFilters } from '../api/types'
import { parseCoordinates } from './geo'

export const noFilters: ScoutFilters = {
  baseAddress: null,
  baseLatitude: null,
  baseLongitude: null,
  radiusKm: null,
  maxBudget: null,
  excludedTypes: [],
  includePrivate: null,
}

/** The base point as the form shows it: the address, or "lat, lng" for a spot on the map. */
export function baseText(filters: ScoutFilters): string {
  if (filters.baseLatitude != null && filters.baseLongitude != null) return `${filters.baseLatitude}, ${filters.baseLongitude}`
  return filters.baseAddress ?? ''
}

/** What the user typed as the base point: coordinates when it reads as "lat, lng", otherwise an address. */
export function parseBase(text: string): Pick<ScoutFilters, 'baseAddress' | 'baseLatitude' | 'baseLongitude'> {
  const trimmed = text.trim()
  const coordinates = /\d/.test(trimmed) && trimmed.includes(',') ? parseCoordinates(trimmed) : null
  if (coordinates?.ok && coordinates.value) {
    return { baseAddress: null, baseLatitude: coordinates.value.latitude, baseLongitude: coordinates.value.longitude }
  }
  return { baseAddress: trimmed || null, baseLatitude: null, baseLongitude: null }
}

/** Whether any filter is set. */
export function hasFilters(filters: ScoutFilters): boolean {
  return (
    filters.radiusKm != null ||
    filters.maxBudget != null ||
    filters.excludedTypes.length > 0 ||
    filters.includePrivate === false ||
    !!filters.baseAddress ||
    filters.baseLatitude != null
  )
}

/** The filters in a sentence: "Within 3 km of Bedford Ave · up to 1,500 a day · no church, nightclub · no private property". */
export function describeFilters(filters: ScoutFilters): string {
  const base = baseText(filters)
  const parts = [
    filters.radiusKm != null && base ? `Within ${filters.radiusKm} km of ${base}` : base ? `Near ${base}` : null,
    filters.maxBudget != null ? `up to ${filters.maxBudget.toLocaleString('en')} a day` : null,
    filters.excludedTypes.length > 0 ? `no ${filters.excludedTypes.join(', ')}` : null,
    filters.includePrivate === false ? 'no private property' : null,
  ].filter((part): part is string => !!part)
  if (parts.length === 0) return 'No filters: anywhere in the location area.'
  const sentence = parts.join(' · ')
  return sentence.charAt(0).toUpperCase() + sentence.slice(1)
}
