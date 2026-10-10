import { CalendarDays, Clock, Lock, MapPin, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { Location, LocationStatus, Page, Scene } from '../api/types'
import { MapSnapshot } from '../components/MapSnapshot'
import { VenuePicture } from '../components/VenuePicture'
import { clockTime } from '../lib/availability'
import { formatShootWindow } from '../lib/format'
import { stripColour, stripKind, stripLabel } from '../lib/stripboard'

const STEPS: { status: LocationStatus; label: string }[] = [
  { status: 'SUGGESTED', label: 'Found' },
  { status: 'SHORTLISTED', label: 'Shortlist' },
  { status: 'CONTACTED', label: 'Asked' },
  { status: 'CONFIRMED', label: 'Locked' },
]

/**
 * The scene in one card, beside the work on it: where it will be shot (or how far the hunt has got), when, and who
 * is in it. Counts come from the venue list's first page, which the page has loaded already.
 */
export function SceneGlance({ scene, venues }: { scene: Scene; venues: Page<Location> | undefined }) {
  const items = venues?.items ?? []
  const locked = items.find((venue) => venue.status === 'CONFIRMED')
  const counts = new Map(STEPS.map(({ status }) => [status, items.filter((venue) => venue.status === status).length]))
  const passed = items.filter((venue) => venue.status === 'REJECTED').length
  const kind = stripKind({ title: scene.title, settingType: scene.requirements?.settingType ?? null, timeOfDay: scene.requirements?.timeOfDay ?? null })
  const label = stripLabel(kind)
  const when = formatShootWindow(scene.shootDateStart, scene.shootDateEnd)
  const call = clockTime(scene.callTime)
  const wrap = clockTime(scene.wrapTime)

  return (
    <section aria-labelledby="glance-heading" className="overflow-hidden rounded-xl border border-line bg-paper shadow-[var(--shadow-card)]">
      <h2 id="glance-heading" className="sr-only">
        At a glance
      </h2>
      {locked ? (
        <Link to={`/locations/${locked.id}`} className="group relative block h-36 overflow-hidden bg-night">
          {locked.imageUrl ? (
            <VenuePicture src={locked.imageUrl} className="h-full w-full transition-transform duration-500 group-hover:scale-105" />
          ) : locked.latitude !== null && locked.longitude !== null ? (
            <MapSnapshot latitude={locked.latitude} longitude={locked.longitude} />
          ) : null}
          <span className="absolute inset-0 bg-linear-to-t from-black/90 via-black/45 to-black/5" />
          <span className="absolute inset-x-4 bottom-3 text-white">
            <span className="flex items-center gap-1.5 text-[11px] font-semibold tracking-widest text-go uppercase">
              <Lock aria-hidden className="size-3" />
              Location locked
            </span>
            <span className="block truncate font-display text-xl font-bold group-hover:underline">{locked.name}</span>
          </span>
        </Link>
      ) : (
        <div className="space-y-1 border-b border-line bg-ground px-4 py-4">
          <p className="flex items-center gap-1.5 text-[11px] font-semibold tracking-widest text-cue-ink uppercase">
            <MapPin aria-hidden className="size-3" />
            No location locked
          </p>
          <p className="text-sm text-muted">{nextStep(counts, items.length)}</p>
        </div>
      )}

      <div className="space-y-4 p-4">
        <ol aria-label="The hunt so far" className="grid grid-cols-4 gap-1.5">
          {STEPS.map(({ status, label: step }) => {
            const count = counts.get(status) ?? 0
            return (
              <li key={status} className="space-y-1 text-center">
                <span
                  aria-hidden
                  className={`block h-1.5 rounded-full ${count === 0 ? 'bg-line' : status === 'CONFIRMED' ? 'bg-go-mid' : 'bg-brand'}`}
                />
                <span className="block font-display text-lg leading-none font-bold text-ink">{count}</span>
                <span className="block text-[11px] text-muted">{step}</span>
              </li>
            )
          })}
        </ol>
        {passed > 0 && <p className="-mt-2 text-center text-xs text-muted">{passed === 1 ? '1 venue passed on' : `${passed} venues passed on`}</p>}

        <dl className="space-y-2.5 border-t border-line pt-4 text-sm">
          {label && (
            <Row term="Set">
              <span className={`inline-flex items-center gap-1.5 rounded-md border border-line px-2 py-0.5 font-mono text-xs font-semibold text-ink ${stripColour(kind)}`}>
                {label}
              </span>
            </Row>
          )}
          <Row term="Shoot" icon={CalendarDays}>
            {when ?? <span className="text-muted">No dates yet</span>}
          </Row>
          {call && (
            <Row term="Call" icon={Clock}>
              {call}
              {wrap && ` – ${wrap}`}
            </Row>
          )}
          {scene.characters.length > 0 && (
            <Row term="Cast" icon={Users}>
              {scene.characters.join(', ')}
            </Row>
          )}
        </dl>
      </div>
    </section>
  )
}

function Row({ term, icon: Icon, children }: { term: string; icon?: typeof Clock; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[4.5rem_minmax(0,1fr)] items-baseline gap-2">
      <dt className="flex items-center gap-1.5 text-xs text-muted">
        {Icon && <Icon aria-hidden className="size-3.5" />}
        {term}
      </dt>
      <dd className="font-medium break-words text-ink">{children}</dd>
    </div>
  )
}

/** What would move the hunt on, in a line. */
function nextStep(counts: Map<LocationStatus, number>, total: number): string {
  if (total === 0) return 'Scout for venues to start the hunt.'
  if ((counts.get('CONTACTED') ?? 0) > 0) return 'Waiting to hear back from the venues you asked.'
  if ((counts.get('SHORTLISTED') ?? 0) > 0) return 'Write to the shortlisted venues to ask about the day.'
  if ((counts.get('SUGGESTED') ?? 0) > 0) return 'Shortlist the venues worth a visit.'
  return 'Every venue so far has been passed on: scout again.'
}
