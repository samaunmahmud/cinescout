import type { BatchParseResult, DayConditions, ProjectProgress, Schedule, ScoutingResult } from '../api/types'
import { temperatureRange } from './logisticsFormat'

// Shoot dates are plain calendar dates (yyyy-mm-dd) with no time zone, so they are formatted in UTC:
// formatting them in the viewer's zone could show the day before.
const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })

export function formatDate(isoDate: string): string {
  return dateFormat.format(new Date(`${isoDate}T00:00:00Z`))
}

const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })

/** "Monday 12 October 2026", for the heading of a shoot day. */
export function formatDay(isoDate: string): string {
  return dayFormat.format(new Date(`${isoDate}T00:00:00Z`))
}

/** "12 Oct 2026", "12 Oct 2026 – 14 Oct 2026", "From 12 Oct 2026", "Until ...", or null when unscheduled. */
export function formatShootWindow(start: string | null, end: string | null): string | null {
  if (start && end) return start === end ? formatDate(start) : `${formatDate(start)} – ${formatDate(end)}`
  if (start) return `From ${formatDate(start)}`
  if (end) return `Until ${formatDate(end)}`
  return null
}

/** "Scene 12: The Diner", or just the title for an unnumbered scene. */
export function sceneLabel(scene: { sceneNumber: number | null; title: string }): string {
  return scene.sceneNumber == null ? scene.title : `Scene ${scene.sceneNumber}: ${scene.title}`
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** One sentence on what a scouting run did, for the banner after it. */
export function scoutingSummary({ added, alreadySaved, unassessed, notVenues, unsuitable }: ScoutingResult): string {
  const parts = [added.length > 0 ? `Found ${plural(added.length, 'new venue', 'new venues')}.` : 'No new venues found.']
  if (alreadySaved > 0) {
    parts.push(`${plural(alreadySaved, 'venue was', 'venues were')} already saved and left as ${alreadySaved === 1 ? 'it was' : 'they were'}.`)
  }
  if (unassessed > 0) parts.push(`${plural(unassessed, 'venue', 'venues')} could not be assessed and ${unassessed === 1 ? 'was' : 'were'} left out.`)
  if (notVenues > 0) {
    parts.push(`Skipped ${plural(notVenues, 'page that listed', 'pages that listed')} several venues rather than one.`)
  }
  if (unsuitable > 0) {
    parts.push(`Left out ${plural(unsuitable, 'venue', 'venues')} that could not work for this scene, such as ones in another area.`)
  }
  return parts.join(' ')
}

/** "3 of 12 scenes have a confirmed location. 7 have candidates." */
export function progressSummary({ scenes, scenesConfirmed, scenesWithLocations }: ProjectProgress): string {
  if (scenes === 0) return 'This project has no scenes yet.'
  const confirmed = `${scenesConfirmed} of ${plural(scenes, 'scene', 'scenes')} ${scenesConfirmed === 1 ? 'has' : 'have'} a confirmed location.`
  return `${confirmed} ${scenesWithLocations} ${scenesWithLocations === 1 ? 'has' : 'have'} candidates.`
}

/** One sentence on what analysing a project's scenes did, for the banner after it. */
export function batchParseSummary({ parsed, failed, remaining }: BatchParseResult): string {
  if (parsed + failed === 0 && remaining === 0) return 'Every scene has been analysed already.'
  const parts = [`Analysed ${plural(parsed, 'scene', 'scenes')}.`]
  if (failed > 0) parts.push(`${plural(failed, 'scene', 'scenes')} could not be analysed; open ${failed === 1 ? 'it' : 'them'} to try again.`)
  if (remaining > 0) parts.push(`${plural(remaining, 'scene is', 'scenes are')} still waiting: analyse again to go on.`)
  return parts.join(' ')
}

/** "3 shoot days. 2 scenes still need a confirmed location. 4 scenes have no shoot date." */
export function scheduleSummary({ days, unscheduled }: Schedule): string {
  const scheduled = days.flatMap((day) => day.scenes)
  if (scheduled.length + unscheduled.length === 0) return 'This project has no scenes yet.'
  const homeless = scheduled.filter((scene) => scene.venues.length === 0).length
  const parts = [days.length === 0 ? 'No scene has a shoot date yet.' : `${plural(days.length, 'shoot day', 'shoot days')}.`]
  if (homeless > 0) parts.push(`${plural(homeless, 'dated scene still needs', 'dated scenes still need')} a confirmed location.`)
  else if (scheduled.length > 0) parts.push('Every dated scene has a confirmed location.')
  if (unscheduled.length > 0 && days.length > 0) parts.push(`${plural(unscheduled.length, 'scene has', 'scenes have')} no shoot date.`)
  return parts.join(' ')
}

/** "Sun 07:04–18:20 · Clear sky, 12–23 °C", or null when nothing is known. */
export function dayConditions(day: DayConditions | null): string | null {
  if (!day) return null
  const sun = day.sunrise && day.sunset ? `Sun ${day.sunrise}–${day.sunset}` : null
  const weather = [day.weather, temperatureRange(day.temperatureMinC, day.temperatureMaxC)].filter(Boolean).join(', ')
  return [sun, weather || null].filter(Boolean).join(' · ') || null
}
