import type { Alert, AlertCover } from '../api/types'
import { formatDate } from './format'

/** An alert in words, and where it leads. */
export interface AlertWords {
  title: string
  detail: string[]
  to: string
}

const text = (value: unknown, fallback = '') => (typeof value === 'string' && value ? value : fallback)
const number = (value: unknown) => (typeof value === 'number' ? value : null)

/** "Rain likely at Skyline Rooftop on 12 Oct 2026", with the numbers against the thresholds and the cover sets. */
export function alertWords(alert: Alert): AlertWords {
  const p = alert.payload
  const venue = text(p.venue, 'a venue')
  if (alert.kind === 'FOLLOW_UP') {
    const sent = text(p.sentAt)
    return {
      title: `Follow up with ${venue}`,
      detail: [`“${text(p.subject, 'Your email')}”${sent ? ` sent ${formatDate(sent.slice(0, 10))}` : ''}, no reply yet.`],
      to: alert.locationId ? `/locations/${alert.locationId}?tab=outreach` : `/projects/${alert.projectId}?tab=outreach`,
    }
  }

  const reasons = Array.isArray(p.reasons) ? p.reasons : []
  const rain = reasons.includes('RAIN')
  const wind = reasons.includes('WIND')
  const what = rain && wind ? 'Rain and strong wind' : rain ? 'Rain likely' : 'Strong wind'
  const day = text(p.day)
  const detail: string[] = []
  const facts = [
    rain && number(p.rainChance) != null && `${number(p.rainChance)}% chance of rain (alert from ${number(p.rainThreshold)}%)`,
    wind &&
      number(p.windKmh) != null &&
      `wind ${number(p.windKmh)} km/h${number(p.gustKmh) != null ? `, gusts ${number(p.gustKmh)}` : ''} (alert from ${number(p.windThreshold)} km/h)`,
  ].filter(Boolean)
  if (facts.length > 0) {
    const line = facts.join(', ')
    detail.push(`${line.charAt(0).toUpperCase()}${line.slice(1)}.`)
  }
  const covers = (Array.isArray(p.covers) ? p.covers : []) as AlertCover[]
  detail.push(
    covers.length > 0
      ? `Switch to the cover set? ${covers.map((cover) => (cover.trigger ? `${cover.name} (${cover.trigger})` : cover.name)).join(', ')}.`
      : `${text(p.scene, 'The scene')} has no cover set.`,
  )
  return {
    title: `${what} at ${venue}${day ? ` on ${formatDate(day)}` : ''}`,
    detail,
    to: alert.sceneId ? `/scenes/${alert.sceneId}` : `/projects/${alert.projectId}?tab=schedule`,
  }
}
