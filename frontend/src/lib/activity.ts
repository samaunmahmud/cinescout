import type { Activity, AvailabilityState, LocationStatus, OutreachStatus, ProjectRole } from '../api/types'
import { availabilityLabels } from './availability'
import { formatDate, formatShootWindow } from './format'
import { outreachStatusLabels, statusLabels } from './status'

/** A piece of an activity line: words, or words that link somewhere. */
export type LinePart = string | { text: string; to: string }

const roleWords: Record<ProjectRole, string> = { OWNER: 'owner', EDITOR: 'an editor', VIEWER: 'a viewer' }

const text = (value: unknown, fallback = '') => (typeof value === 'string' && value ? value : fallback)

/** A log line in words, e.g. ["Grace", " moved ", {Moonlight Diner → venue page}, " from Suggested to Shortlisted"]. */
export function activityLine(line: Activity): LinePart[] {
  const p = line.payload
  const actor = line.actorName ?? 'A former member'
  const venueLink = (id: string | null | undefined, tab = ''): LinePart =>
    id ? { text: text(p.venue, 'a venue'), to: `/locations/${id}${tab}` } : text(p.venue, 'a venue')
  const sceneLink: LinePart = line.targetId ? { text: text(p.scene, 'a scene'), to: `/scenes/${line.targetId}` } : text(p.scene, 'a scene')
  const member = text(p.member, 'someone')

  switch (line.verb) {
    case 'VENUE_STATUS_CHANGED':
      return [
        actor,
        ' moved ',
        venueLink(line.targetId),
        ` from ${statusLabels[p.from as LocationStatus] ?? p.from} to ${statusLabels[p.to as LocationStatus] ?? p.to}`,
      ]
    case 'DIRECTOR_CALLED': {
      const said = p.verdict === 'APPROVE' ? ' approved ' : p.verdict === 'NO' ? ' passed on ' : ' said maybe to '
      return [`${actor} (guest)`, said, venueLink(line.targetId), p.commented ? ', with a comment' : '']
    }
    case 'SCOUTED': {
      const added = Number(p.added ?? 0)
      return [actor, ' scouted ', sceneLink, added === 0 ? ': nothing new found' : `: ${added} new venue${added === 1 ? '' : 's'}`]
    }
    case 'OUTREACH_STATUS_CHANGED':
      return [
        actor,
        ' marked the email to ',
        venueLink(text(p.locationId) || null, '?tab=outreach'),
        ` as ${(outreachStatusLabels[p.to as OutreachStatus]?.label ?? String(p.to)).toLowerCase()}`,
      ]
    case 'MEMBER_JOINED': {
      const role = roleWords[p.role as ProjectRole] ?? 'a member'
      return line.actorName === member || p.byInvite ? [`${member} joined as ${role}`] : [`${actor} added ${member} as ${role}`]
    }
    case 'MEMBER_LEFT':
      return [`${member} left the project`]
    case 'MEMBER_REMOVED':
      return [`${actor} took ${member} off the project`]
    case 'ROLE_CHANGED':
      return [`${actor} made ${member} ${roleWords[p.to as ProjectRole] ?? String(p.to)}`]
    case 'OWNERSHIP_TRANSFERRED':
      return [`${actor} handed the project to ${member}`]
    case 'SHOOT_DATES_CHANGED': {
      const window = formatShootWindow(text(p.toStart) || null, text(p.toEnd) || null)
      return window ? [actor, ' set ', sceneLink, ` to shoot ${window.charAt(0).toLowerCase()}${window.slice(1)}`] : [actor, ' cleared the dates of ', sceneLink]
    }
    case 'AVAILABILITY_CHANGED': {
      const from = text(p.from)
      const to = text(p.to)
      const days = from && to && from !== to ? `${formatDate(from)} to ${formatDate(to)}` : from ? formatDate(from) : 'a day'
      const state = availabilityLabels[p.state as AvailabilityState]
      return state
        ? [actor, ' marked ', venueLink(line.targetId), ` ${state.label.toLowerCase()} for ${days}`]
        : [actor, ' cleared ', venueLink(line.targetId), ` for ${days}`]
    }
    case 'COMMENTED':
      return [actor, p.reply ? ' replied on ' : ' commented on ', venueLink(line.targetId, '?tab=comments')]
  }
}

/** The line as plain text, for its accessible name and for tests. */
export function activityText(line: Activity): string {
  return activityLine(line)
    .map((part) => (typeof part === 'string' ? part : part.text))
    .join('')
}
