import { useQuery } from '@tanstack/react-query'
import { Mail } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { OutreachFilter, Page, ProjectOutreach } from '../api/types'
import { useSession } from '../auth/context'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { EmptyState, Section } from '../components/surfaces'
import { Badge, ErrorAlert, Spinner } from '../components/ui'
import { sceneLabel } from '../lib/format'
import { isOutreachStatus, outreachStatusLabels, outreachStatuses } from '../lib/status'

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

/**
 * Every outreach email of the project in one list: who was written to about which venue, and who has
 * answered. Read-only; an email is written, edited and marked as sent on its venue's page, which each row
 * links to. Refetched on every visit, as drafts change there.
 */
export function ProjectOutreachSection({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const [params, setParams] = useSearchParams()
  const [page, setPage] = usePageParam()
  const rawStatus = params.get('status')
  const status: OutreachFilter | null = rawStatus === 'FOLLOW_UP' || isOutreachStatus(rawStatus) ? rawStatus : null

  const drafts = useQuery({
    queryKey: queryKeys.projectOutreachPage(projectId, status, page),
    queryFn: () => api.outreach.listForProject(projectId, status, page),
    placeholderData: previousPageOf<Page<ProjectOutreach>>(queryKeys.projectOutreachList(projectId)),
    refetchOnMount: 'always',
  })
  useStayInRange(drafts.data, setPage)

  const selectStatus = (next: OutreachFilter | null) =>
    setParams((current) => {
      const updated = new URLSearchParams(current)
      if (next) updated.set('status', next)
      else updated.delete('status')
      updated.delete('page')
      return updated
    })

  const options: { status: OutreachFilter | null; label: string }[] = [
    { status: null, label: 'All' },
    ...outreachStatuses.map((value) => ({ status: value, label: value === 'DRAFT' ? 'Not sent' : outreachStatusLabels[value].label })),
    { status: 'FOLLOW_UP', label: 'Follow up' },
  ]

  return (
    <Section
      titleId="project-outreach-heading"
      title="Outreach"
      eyebrow="Every email to a venue"
      icon={Mail}
      description="Who has been written to, and who has answered. Emails are written on each venue’s page."
    >
      <div role="group" aria-label="Filter by status" className="flex flex-wrap gap-2">
        {options.map((option) => {
          const active = option.status === status
          return (
            <button
              key={option.label}
              type="button"
              aria-pressed={active}
              onClick={() => selectStatus(option.status)}
              className={`rounded-full px-3 py-1.5 text-sm font-semibold ring-1 transition ring-inset focus-visible:outline-2 focus-visible:outline-ink ${
                active ? 'bg-cue-wash text-cue-ink ring-cue' : 'bg-ground text-graphite ring-line hover:bg-ground'
              }`}
            >
              {option.label}
            </button>
          )
        })}
      </div>

      {drafts.isPending ? (
        <Spinner label="Loading outreach" />
      ) : drafts.isError ? (
        <ErrorAlert error={drafts.error} onRetry={() => drafts.refetch()} />
      ) : drafts.data.items.length === 0 ? (
        <EmptyState icon={Mail}>
          {status === 'FOLLOW_UP'
            ? 'Nothing to chase. An email shows up here when it has gone unanswered for longer than the project allows.'
            : status
            ? 'No emails in this state.'
            : 'No emails yet. Open a venue and have CineScout draft the email to its owner; it shows up here with all the others.'}
        </EmptyState>
      ) : (
        <>
          <ul aria-label="Outreach emails" className="space-y-2">
            {drafts.data.items.map((draft) => (
              <li key={draft.id}>
                <OutreachRow draft={draft} />
              </li>
            ))}
          </ul>
          <Pager data={drafts.data} onChange={setPage} label="Outreach pages" />
        </>
      )}
    </Section>
  )
}

function OutreachRow({ draft }: { draft: ProjectOutreach }) {
  const status = outreachStatusLabels[draft.status]
  const recipient = draft.recipientName ?? draft.recipientEmail
  return (
    <article
      aria-label={draft.subject}
      className="flex flex-wrap items-start justify-between gap-x-6 gap-y-2 board-card rounded-lg bg-paper p-4"
    >
      <div className="min-w-0 flex-1 space-y-1">
        <h3 className="font-semibold">
          <Link to={`/locations/${draft.locationId}?tab=outreach`} className="text-lg text-ink hover:text-cue-deep">
            {draft.subject}
          </Link>
        </h3>
        <p className="text-sm text-graphite">
          {draft.locationName}
          <span className="text-subtle"> · </span>
          <Link to={`/scenes/${draft.sceneId}`} className="text-muted hover:text-cue-deep">
            {sceneLabel({ sceneNumber: draft.sceneNumber, title: draft.sceneTitle })}
          </Link>
        </p>
        <p className="text-sm text-muted">
          {recipient ? `To ${recipient}` : 'No recipient yet'}
          {draft.recipientName && draft.recipientEmail && ` (${draft.recipientEmail})`}
        </p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1 text-sm text-muted">
        <Badge tone={status.tone}>{status.label}</Badge>
        {draft.followUpFlaggedAt && <Badge tone="red">Follow up</Badge>}
        {draft.followUpOfId && <span className="font-script text-xs">Follow-up</span>}
        <span>{draft.sentAt ? `Sent ${dateFormat.format(new Date(draft.sentAt))}` : `Written ${dateFormat.format(new Date(draft.createdAt))}`}</span>
      </div>
    </article>
  )
}
