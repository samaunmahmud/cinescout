import type { ProjectProgress, ScoutingResult } from '../api/types'

// Shoot dates are plain calendar dates (yyyy-mm-dd) with no time zone, so they are formatted in UTC:
// formatting them in the viewer's zone could show the day before.
const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })

export function formatDate(isoDate: string): string {
  return dateFormat.format(new Date(`${isoDate}T00:00:00Z`))
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
