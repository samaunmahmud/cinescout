import { useQuery } from '@tanstack/react-query'
import { Activity as ActivityIcon, CalendarDays, Clapperboard, Lock, Mail, MapPinned, Sparkles } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Dashboard } from '../api/types'
import { useSession } from '../auth/context'
import { Avatar } from '../components/Avatar'
import { StudioLight } from '../components/StudioLight'
import { FitScore } from '../components/locationParts'
import { VenuePicture } from '../components/VenuePicture'
import { activityLine } from '../lib/activity'
import { posterBackdrop } from '../lib/poster'

const day = new Intl.DateTimeFormat(undefined, { day: 'numeric' })
const month = new Intl.DateTimeFormat(undefined, { month: 'short' })
const weekday = new Intl.DateTimeFormat(undefined, { weekday: 'short' })
const when = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

/** A shoot date as a calendar leaf: "THU / 23 / NOV". The date is a plain day, read as local. */
function leaf(isoDate: string) {
  const date = new Date(`${isoDate}T12:00:00`)
  return { weekday: weekday.format(date), day: day.format(date), month: month.format(date) }
}

/**
 * The top of the productions page: the greeting and headline numbers over a lit set, then what is shooting next,
 * the venues waiting to be looked at, and what the crews have been doing. `actions` are the page's buttons.
 */
export function DashboardHero({ greeting, actions }: { greeting: string; actions: ReactNode }) {
  const { api } = useSession()
  const dashboard = useQuery({ queryKey: queryKeys.dashboard, queryFn: () => api.dashboard() })
  const totals = dashboard.data?.totals
  const tiles = [
    { label: 'Productions', value: totals?.productions, icon: Clapperboard },
    { label: 'Scenes', value: totals?.scenes, icon: CalendarDays },
    { label: 'Venues locked', value: totals?.lockedScenes, icon: Lock },
    { label: 'Venues in play', value: totals?.venuesInPlay, icon: MapPinned },
    { label: 'To follow up', value: totals?.followUps, icon: Mail, alert: (totals?.followUps ?? 0) > 0 },
  ]
  return (
    <header className="hero rounded-3xl px-6 pt-10 pb-6 sm:px-10" style={{ background: posterBackdrop('your productions') }}>
      <StudioLight />
      <div className="flex flex-wrap items-end justify-between gap-6">
        <div className="max-w-2xl space-y-3">
          <p className="text-sm font-semibold tracking-[0.14em] text-cue uppercase">{greeting}</p>
          <h1 className="font-display text-4xl leading-[1.02] font-bold sm:text-6xl">Your productions</h1>
          <p className="text-[17px] leading-relaxed text-fog">Every production, with its scenes, its venues and its letters to their owners.</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">{actions}</div>
      </div>
      <dl className="mt-10 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {tiles.map(({ label, value, icon: Icon, alert }) => (
          <div key={label} className={`rounded-2xl px-4 py-4 max-sm:last:odd:col-span-2 ring-1 backdrop-blur ${alert ? 'bg-brand/25 ring-cue/40' : 'bg-white/[0.07] ring-white/12'}`}>
            <dt className="flex items-center gap-1.5 text-xs font-medium text-fog">
              <Icon aria-hidden className="size-3.5 text-cue" />
              {label}
            </dt>
            <dd className="mt-1 font-display text-4xl leading-none font-bold">{value ?? '–'}</dd>
          </div>
        ))}
      </dl>
    </header>
  )
}

/** Up next, fresh finds and the latest activity, under the hero. Nothing at all while there is nothing to show. */
export function DashboardPanels() {
  const { api } = useSession()
  const dashboard = useQuery({ queryKey: queryKeys.dashboard, queryFn: () => api.dashboard() })
  if (!dashboard.data) return null
  const { upcoming, freshFinds, activity } = dashboard.data
  if (upcoming.length === 0 && freshFinds.length === 0 && activity.length === 0) return null
  return (
    <div className="space-y-8">
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
        <UpNext upcoming={upcoming} />
        <Happening activity={activity} />
      </div>
      {freshFinds.length > 0 && <FreshFinds finds={freshFinds} />}
    </div>
  )
}

function Panel({ title, icon: Icon, children, id }: { title: string; icon: typeof Sparkles; children: ReactNode; id: string }) {
  return (
    <section aria-labelledby={id} className="flex flex-col overflow-hidden rounded-2xl border border-line bg-paper shadow-[var(--shadow-card)]">
      <h2 id={id} className="flex items-center gap-2 border-b border-line-soft px-5 py-4 text-sm font-semibold">
        <span className="flex size-7 items-center justify-center rounded-lg bg-brand text-white">
          <Icon aria-hidden className="size-4" />
        </span>
        {title}
      </h2>
      {children}
    </section>
  )
}

function UpNext({ upcoming }: { upcoming: Dashboard['upcoming'] }) {
  return (
    <Panel title="Up next on the schedule" icon={CalendarDays} id="up-next">
      {upcoming.length === 0 ? (
        <p className="px-5 py-8 text-sm text-muted">No shoot days ahead. Give your scenes dates and they line up here.</p>
      ) : (
        <ol className="divide-y divide-line-soft">
          {upcoming.map((shoot) => {
            const date = leaf(shoot.date)
            return (
              <li key={shoot.sceneId}>
                <Link to={`/scenes/${shoot.sceneId}`} className="group flex items-center gap-4 px-5 py-3.5 transition hover:bg-ground">
                  <span className="flex w-14 shrink-0 flex-col items-center overflow-hidden rounded-xl bg-night text-white shadow-[var(--shadow-card)]">
                    <span className="w-full bg-brand py-0.5 text-center text-[10px] font-semibold tracking-[0.12em] uppercase">{date.weekday}</span>
                    <span className="pt-1 font-display text-xl leading-none font-bold">{date.day}</span>
                    <span className="pb-1.5 text-[11px] text-fog">{date.month}</span>
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-semibold text-ink group-hover:text-cue-ink">
                      {shoot.sceneNumber != null ? `${shoot.sceneNumber}. ` : ''}
                      {shoot.sceneTitle}
                    </span>
                    <span className="block truncate text-sm text-muted">
                      {shoot.projectTitle}
                      {shoot.callTime ? ` · call ${shoot.callTime.slice(0, 5)}` : ''}
                      {shoot.venueName ? ` · ${shoot.venueName}` : ' · no venue locked yet'}
                    </span>
                  </span>
                  {shoot.venueImageUrl && (
                    <span aria-hidden className="hidden h-12 w-20 shrink-0 overflow-hidden rounded-lg sm:block">
                      <VenuePicture src={shoot.venueImageUrl} className="h-full w-full transition duration-500 group-hover:scale-110" />
                    </span>
                  )}
                </Link>
              </li>
            )
          })}
        </ol>
      )}
    </Panel>
  )
}

function Happening({ activity }: { activity: Dashboard['activity'] }) {
  return (
    <Panel title="What the crews have been doing" icon={ActivityIcon} id="happening">
      {activity.length === 0 ? (
        <p className="px-5 py-8 text-sm text-muted">Nothing yet. Scouting runs, status changes, emails and comments show up here.</p>
      ) : (
        <ol className="divide-y divide-line-soft">
          {activity.map(({ line, projectTitle }) => (
            <li key={line.id} className="flex items-start gap-3 px-5 py-3">
              <Avatar name={line.actorName ?? 'Guest'} size="sm" className="mt-0.5" />
              <div className="min-w-0 flex-1">
                <p className="text-sm text-graphite">
                  {activityLine(line).map((part, index) =>
                    typeof part === 'string' ? (
                      <span key={index}>{part}</span>
                    ) : (
                      <Link key={index} to={part.to} className="font-semibold text-ink hover:text-cue-ink">
                        {part.text}
                      </Link>
                    ),
                  )}
                </p>
                <p className="mt-0.5 text-xs text-muted">
                  {projectTitle} · <time dateTime={line.createdAt}>{when.format(new Date(line.createdAt))}</time>
                </p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </Panel>
  )
}

function FreshFinds({ finds }: { finds: Dashboard['freshFinds'] }) {
  return (
    <section aria-labelledby="fresh-finds" className="space-y-4">
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="flex items-center gap-1.5 text-xs font-semibold tracking-[0.08em] text-cue-ink uppercase">
            <Sparkles aria-hidden className="size-3.5" />
            Scouted, not yet looked at
          </p>
          <h2 id="fresh-finds" className="mt-1 font-display text-2xl leading-tight font-semibold">
            Fresh finds
          </h2>
        </div>
      </div>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {finds.map((find) => (
          <li key={find.locationId}>
            <Link
              to={`/locations/${find.locationId}`}
              className="group flex h-full flex-col overflow-hidden rounded-2xl border border-line bg-paper shadow-[var(--shadow-card)] transition duration-300 hover:-translate-y-1 hover:shadow-[var(--shadow-lift)]"
            >
              <div className="relative h-36 overflow-hidden bg-night" style={find.imageUrl ? undefined : { background: posterBackdrop(find.name) }}>
                {find.imageUrl && <VenuePicture src={find.imageUrl} className="h-full w-full transition duration-700 group-hover:scale-110" />}
                {find.fitScore != null && (
                  <span className="absolute right-3 bottom-3 rounded-full bg-paper p-0.5 shadow-[var(--shadow-lift)]">
                    <FitScore score={find.fitScore} />
                  </span>
                )}
              </div>
              <div className="flex flex-1 flex-col gap-1 p-4">
                <span className="font-semibold text-ink group-hover:text-cue-ink">{find.name}</span>
                <span className="truncate text-sm text-muted">{find.address ?? find.sceneTitle}</span>
                <span className="mt-auto pt-2 text-xs text-subtle">
                  {find.projectTitle} · {find.sceneTitle}
                </span>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
