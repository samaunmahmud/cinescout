// Formatting for the logistics report. Its local times carry the venue's own UTC offset
// ("2026-09-28T06:49:00-04:00"), and a crew needs the venue's clock, not the viewer's: so the clock time is
// read straight from the string and never converted to the browser's zone.

import type { TimeWindow } from '../api/types'

/**
 * "06:49" for a time on `day` (yyyy-mm-dd); "24:00" for the midnight that ends it, and "+1d" on any other time
 * that falls on the next day (a night window running past midnight).
 */
export function localTime(iso: string, day: string): string {
  const date = iso.slice(0, 10)
  const time = iso.slice(11, 16)
  if (date === day) return time
  return date > day && time === '00:00' ? '24:00' : `${time} ${date > day ? '+1d' : '-1d'}`
}

/** "18:07–19:00" */
export function timeWindow(window: TimeWindow, day: string): string {
  return `${localTime(window.start, day)}–${localTime(window.end, day)}`
}

/** "11 h 53 min", "24 h" or "0 min". */
export function formatDaylight(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} min`
  return m === 0 ? `${h} h` : `${h} h ${m} min`
}

/** "240 m" or "1.9 km". */
export function formatDistance(meters: number): string {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1).replace(/\.0$/, '')} km`
}

/** "14–16 °C", "16 °C" or null, rounded to whole degrees. */
export function temperatureRange(min: number | null, max: number | null): string | null {
  if (min == null && max == null) return null
  if (min == null || max == null) return `${Math.round((min ?? max)!)} °C`
  const [lo, hi] = [Math.round(min), Math.round(max)]
  return lo === hi ? `${lo} °C` : `${lo}–${hi} °C`
}
