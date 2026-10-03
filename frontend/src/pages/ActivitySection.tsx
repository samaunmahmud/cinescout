import { useQuery } from '@tanstack/react-query'
import { Activity as ActivityIcon, CalendarDays, Clapperboard, Mail, MapPinned, MessagesSquare, Radar, Users, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Activity, ActivityKind } from '../api/types'
import { useSession } from '../auth/context'
import { Pager } from '../components/Pager'
import { EmptyState, Section } from '../components/surfaces'
import { ErrorAlert, Spinner } from '../components/ui'
import { activityLine } from '../lib/activity'

const kinds: { kind: ActivityKind | null; label: string; icon: LucideIcon }[] = [
  { kind: null, label: 'Everything', icon: ActivityIcon },
  { kind: 'VENUE', label: 'Venues', icon: MapPinned },
  { kind: 'SCOUTING', label: 'Scouting', icon: Radar },
  { kind: 'OUTREACH', label: 'Outreach', icon: Mail },
  { kind: 'SCHEDULE', label: 'Schedule', icon: CalendarDays },
  { kind: 'COMMENT', label: 'Comments', icon: MessagesSquare },
  { kind: 'CREW', label: 'Crew', icon: Users },
]

const iconOf = (line: Activity) => (line.verb === 'DIRECTOR_CALLED' ? Clapperboard : (kinds.find((k) => k.kind === line.kind)?.icon ?? ActivityIcon))

const timeFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

/** What happened on the project and who did it, newest first, one kind at a time if wanted. */
export function ActivitySection({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const [kind, setKind] = useState<ActivityKind | null>(null)
  const [page, setPage] = useState(0)
  const activity = useQuery({
    queryKey: queryKeys.activity(projectId, kind, page),
    queryFn: () => api.projects.activity(projectId, kind, page),
    refetchOnMount: 'always',
  })

  function choose(next: ActivityKind | null) {
    setKind(next)
    setPage(0)
  }

  return (
    <Section titleId="activity-heading" title="Activity" eyebrow="The production log" icon={ActivityIcon}>
      <div role="group" aria-label="Show activity of one kind" className="flex flex-wrap gap-2">
        {kinds.map((option) => {
          const active = option.kind === kind
          return (
            <button
              key={option.label}
              type="button"
              aria-pressed={active}
              onClick={() => choose(option.kind)}
              className={`rounded-full px-3 py-1.5 text-sm font-semibold ring-1 transition ring-inset focus-visible:outline-2 focus-visible:outline-ink ${
                active ? 'bg-cue-wash text-cue-ink ring-cue' : 'bg-ground text-graphite ring-line hover:bg-ground'
              }`}
            >
              {option.label}
            </button>
          )
        })}
      </div>

      {activity.isPending ? (
        <Spinner label="Loading the activity" />
      ) : activity.isError ? (
        <ErrorAlert error={activity.error} onRetry={() => activity.refetch()} />
      ) : activity.data.items.length === 0 ? (
        <EmptyState icon={ActivityIcon}>
          {kind ? 'Nothing of this kind has happened yet.' : 'Nothing has happened yet. Status changes, scouting runs, emails, dates, comments and crew changes show up here.'}
        </EmptyState>
      ) : (
        <>
          <ol aria-label="Activity, newest first" className="board-card divide-y divide-line-soft rounded-lg bg-white">
            {activity.data.items.map((line) => {
              const Icon = iconOf(line)
              return (
                <li key={line.id} className="flex items-start gap-3 px-4 py-3">
                  <Icon aria-hidden className="mt-0.5 size-4 shrink-0 text-cue-ink" />
                  <p className="min-w-0 flex-1 text-[15px] text-graphite">
                    {activityLine(line).map((part, index) =>
                      typeof part === 'string' ? (
                        <span key={index}>{part}</span>
                      ) : (
                        <Link key={index} to={part.to} className="font-semibold text-ink underline decoration-cue decoration-2 underline-offset-2 hover:text-cue-deep">
                          {part.text}
                        </Link>
                      ),
                    )}
                  </p>
                  <time dateTime={line.createdAt} className="shrink-0 font-script text-xs text-muted">
                    {timeFormat.format(new Date(line.createdAt))}
                  </time>
                </li>
              )
            })}
          </ol>
          <Pager data={activity.data} onChange={setPage} label="Activity pages" />
        </>
      )}
    </Section>
  )
}
