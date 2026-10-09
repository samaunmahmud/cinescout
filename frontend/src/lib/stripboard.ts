import type { ScheduledScene } from '../api/types'
import { clockTime } from './availability'

export type StripPlace = 'INT' | 'EXT' | 'INT/EXT'
export type StripLight = 'DAY' | 'NIGHT'

export interface StripKind {
  place: StripPlace | null
  light: StripLight | null
}

const HEADING = /^\s*(?:\d+[A-Z]?\s+)?(INT\.?\s*\/\s*EXT|EXT\.?\s*\/\s*INT|I\s*\/\s*E|INT|EXT|EST)\b/i
const NIGHT = /\b(night|evening|dusk|midnight|twilight|pre-?dawn)\b/i
const DAY = /\b(day|morning|dawn|sunrise|noon|afternoon|sunset|daylight|golden hour)\b/i

/**
 * Whether a scene is inside or out, by day or night, as a stripboard colours it. The heading ("EXT. ROOFTOP - DAWN")
 * says it best; failing that, the AI's setting and time of day. Null where nothing says.
 */
export function stripKind(scene: Pick<ScheduledScene, 'title' | 'settingType' | 'timeOfDay'>): StripKind {
  const heading = HEADING.exec(scene.title)?.[1].toUpperCase().replace(/[\s.]/g, '')
  const place: StripPlace | null =
    heading === undefined ? placeFromSetting(scene.settingType)
    : heading === 'INT' ? 'INT'
    : heading === 'EXT' || heading === 'EST' ? 'EXT'
    : 'INT/EXT'
  // The heading's last part is its time ("- NIGHT"); the AI's reading comes first when there is one.
  const headingTime = heading === undefined ? null : scene.title.split(/\s[-–—]\s/).slice(1).pop() ?? null
  return { place, light: lightOf(scene.timeOfDay) ?? lightOf(headingTime) }
}

function placeFromSetting(setting: string | null): StripPlace | null {
  if (!setting) return null
  if (/\b(exterior|outdoors?|outside|open[- ]air)\b/i.test(setting)) return 'EXT'
  if (/\b(interior|indoors?|inside)\b/i.test(setting)) return 'INT'
  return null
}

function lightOf(text: string | null): StripLight | null {
  if (!text) return null
  if (NIGHT.test(text)) return 'NIGHT'
  if (DAY.test(text)) return 'DAY'
  return null
}

/** The strip's background, as a class: the board's colours, grey when it cannot tell. */
export function stripColour({ place, light }: StripKind): string {
  if (!place || !light) return 'bg-strip-unknown'
  const outside = place !== 'INT'
  if (light === 'DAY') return outside ? 'bg-strip-ext-day' : 'bg-strip-int-day'
  return outside ? 'bg-strip-ext-night' : 'bg-strip-int-night'
}

/** "EXT. NIGHT", "INT.", "DAY", or null, for the strip's label. */
export function stripLabel({ place, light }: StripKind): string | null {
  const parts = [place && `${place}.`, light].filter(Boolean)
  return parts.length > 0 ? parts.join(' ') : null
}

/**
 * The new dates for a scene moved onto a day of the board (null: off the board). A scene shot over several days
 * keeps its length; its call and wrap stay as they were.
 */
export function movedTo(scene: ScheduledScene, day: string | null) {
  const times = { callTime: clockTime(scene.callTime), wrapTime: clockTime(scene.wrapTime) }
  if (day === null) return { shootDateStart: null, shootDateEnd: null, ...times }
  const length = scene.shootDateStart && scene.shootDateEnd ? daysBetween(scene.shootDateStart, scene.shootDateEnd) : null
  return { shootDateStart: day, shootDateEnd: length === null ? null : addDays(day, Math.max(length, 0)), ...times }
}

/** The day after the given ISO date, for a new day at the end of the board. */
export function nextDay(isoDate: string): string {
  return addDays(isoDate, 1)
}

function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000)
}

function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}
