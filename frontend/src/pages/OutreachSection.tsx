import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { GenerateOutreachRequest, Location, OutreachDraft, OutreachStatus, OutreachTone, Page, UpdateOutreachRequest } from '../api/types'
import { useSession } from '../auth/context'
import { linkButton } from '../components/buttonStyles'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { Mail, Sparkles } from 'lucide-react'
import { EmptyState, Section } from '../components/surfaces'
import { Badge, Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { emailText, looksLikeEmail, mailtoLink } from '../lib/email'
import { blankToNull } from '../lib/text'
import { outreachStatusLabels } from '../lib/status'
import { useCanEdit } from '../components/projectRole'

const tones: Record<OutreachTone, { label: string; hint: string }> = {
  PROFESSIONAL: { label: 'Professional', hint: 'Polite and complete' },
  FRIENDLY: { label: 'Friendly', hint: 'Warm, for a local business' },
  CONCISE: { label: 'Concise', hint: 'Short and to the point' },
}

const statuses = outreachStatusLabels

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

const selectClass =
  'rounded-md border border-line bg-white px-2 py-1.5 text-sm text-ink focus:border-ink focus:ring-1 focus:ring-cue/25 focus:outline-none disabled:opacity-50'

/** Emails to the venue's owner: written by the AI, edited by the user, sent from their own email app. */
export function OutreachSection({ location }: { location: Location }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const listKey = queryKeys.outreachList(location.id)
  const [page, setPage] = usePageParam()
  const drafts = useQuery({
    queryKey: queryKeys.outreachPage(location.id, page),
    queryFn: () => api.outreach.list(location.id, page),
    placeholderData: previousPageOf<Page<OutreachDraft>>(listKey),
  })
  useStayInRange(drafts.data, setPage)
  const [composing, setComposing] = useState(false)

  const generate = useMutation({
    mutationFn: (body: GenerateOutreachRequest) => api.outreach.generate(location.id, body),
    onSuccess: (draft) => {
      // Newest first: the new draft heads the first page, and every page after it has shifted by one.
      queryClient.setQueryData<Page<OutreachDraft>>(queryKeys.outreachPage(location.id, 0), (first) =>
        first && { ...first, items: [draft, ...first.items].slice(0, first.size), totalItems: first.totalItems + 1 },
      )
      queryClient.invalidateQueries({ queryKey: listKey })
      setPage(0)
      setComposing(false)
    },
  })

  // Whoever the last email went to is the likeliest recipient of the next one; before any, the venue's contact.
  const latest = drafts.data?.items[0]

  return (
    <Section
      titleId="outreach-heading"
      title="Outreach"
      eyebrow="Ask to film here"
      icon={Mail}
      description="Emails to the venue's owner. CineScout never sends them: you do, from your own email."
      actions={
        canEdit &&
        !composing && (
          <Button
            onClick={() => {
              generate.reset()
              setComposing(true)
            }}
          >
            <Sparkles aria-hidden className="size-4" />
            Write an email
          </Button>
        )
      }
    >

      {composing && (
        <GenerateForm
          initial={{
            recipientName: latest?.recipientName ?? location.contactName ?? '',
            recipientEmail: latest?.recipientEmail ?? location.contactEmail ?? '',
          }}
          busy={generate.isPending}
          error={generate.error}
          onSubmit={(body) => generate.mutate(body)}
          onCancel={() => setComposing(false)}
        />
      )}

      {drafts.isPending ? (
        <Spinner label="Loading emails" />
      ) : drafts.isError ? (
        <ErrorAlert error={drafts.error} onRetry={() => drafts.refetch()} />
      ) : drafts.data.items.length === 0 ? (
        !composing && (
          <EmptyState icon={Mail}>
            No emails yet. The AI drafts a request to film here from the venue and the scene's needs, for you to check and send.
          </EmptyState>
        )
      ) : (
        <>
          <ul aria-label="Emails" className="space-y-3">
            {drafts.data.items.map((draft) => (
              <li key={draft.id}>
                <DraftCard draft={draft} />
              </li>
            ))}
          </ul>
          <Pager data={drafts.data} onChange={setPage} label="Email pages" />
        </>
      )}
    </Section>
  )
}

function GenerateForm({
  initial,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  initial: { recipientName: string; recipientEmail: string }
  busy: boolean
  error: unknown
  onSubmit: (body: GenerateOutreachRequest) => void
  onCancel: () => void
}) {
  const [tone, setTone] = useState<OutreachTone>('PROFESSIONAL')
  const [values, setValues] = useState({ ...initial, additionalContext: '' })
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })
  const server = fieldErrors(error)
  const email = values.recipientEmail.trim()
  const emailValid = email === '' || looksLikeEmail(email)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!emailValid) return
    onSubmit({
      tone,
      recipientName: blankToNull(values.recipientName),
      recipientEmail: blankToNull(email),
      additionalContext: blankToNull(values.additionalContext),
    })
  }

  return (
    <form onSubmit={submit} aria-label="Write an email" className="space-y-4 board-card rounded-lg bg-white p-5" noValidate>
      <ErrorAlert error={error} />
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-graphite">Tone</legend>
        <div className="flex flex-wrap gap-2">
          {Object.entries(tones).map(([value, { label, hint }]) => (
            <label
              key={value}
              className="flex cursor-pointer items-center gap-2 rounded-md border border-line px-3 py-2 text-sm has-checked:border-ink has-checked:bg-cue-wash"
            >
              <input type="radio" name="tone" value={value} checked={tone === value} onChange={() => setTone(value as OutreachTone)} className="accent-[#c2410c]" />
              <span>
                {label} <span className="text-subtle">· {hint}</span>
              </span>
            </label>
          ))}
        </div>
      </fieldset>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Recipient name" maxLength={200} value={values.recipientName} onChange={set('recipientName')} error={server.recipientName} />
        <TextField
          label="Recipient email"
          type="email"
          maxLength={254}
          value={values.recipientEmail}
          onChange={set('recipientEmail')}
          error={emailValid ? server.recipientEmail : 'Enter an email address like owner@example.com.'}
        />
      </div>
      <TextArea
        label="Anything to mention"
        maxLength={2000}
        rows={3}
        placeholder="We can shoot on a weekday and keep the crew under 20."
        hint="Optional. The AI sees the venue, the scene's requirements, your name and the shoot dates. It never sees the script or your notes."
        value={values.additionalContext}
        onChange={set('additionalContext')}
        error={server.additionalContext}
      />
      {busy && <Spinner label="Writing the email. This can take up to a minute." />}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" busy={busy} disabled={!emailValid}>
          Draft email
        </Button>
      </div>
    </form>
  )
}

function DraftCard({ draft }: { draft: OutreachDraft }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const listKey = queryKeys.outreachList(draft.locationId)
  const statusId = useId()
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  const update = useMutation({
    mutationFn: (body: UpdateOutreachRequest) => api.outreach.update(draft.id, body),
    onSuccess: (updated) => {
      queryClient.setQueriesData<Page<OutreachDraft>>({ queryKey: listKey }, (page) =>
        page && { ...page, items: page.items.map((d) => (d.id === updated.id ? updated : d)) },
      )
      setEditing(false)
    },
  })

  const remove = useMutation({
    mutationFn: () => api.outreach.remove(draft.id),
    onSuccess: () => {
      queryClient.setQueriesData<Page<OutreachDraft>>({ queryKey: listKey }, (page) =>
        page && { ...page, items: page.items.filter((d) => d.id !== draft.id) },
      )
      queryClient.invalidateQueries({ queryKey: listKey })
    },
  })

  // A full replacement: everything but the changed fields goes back as it was.
  const current: UpdateOutreachRequest = {
    subject: draft.subject,
    body: draft.body,
    tone: draft.tone,
    status: draft.status,
    recipientName: draft.recipientName,
    recipientEmail: draft.recipientEmail,
  }

  if (editing) {
    return (
      <EditDraftForm
        draft={draft}
        busy={update.isPending}
        error={update.error}
        onSubmit={(changes) => update.mutate({ ...current, ...changes })}
        onCancel={() => {
          setEditing(false)
          update.reset()
        }}
      />
    )
  }

  const status = statuses[draft.status]
  const recipient = [draft.recipientName, draft.recipientEmail && `<${draft.recipientEmail}>`].filter(Boolean).join(' ')

  return (
    <article aria-label={draft.subject} className="space-y-3 board-card rounded-lg bg-white p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <h3 className="font-semibold">{draft.subject}</h3>
          <p className="text-sm text-muted">
            {recipient ? `To ${recipient}` : 'No recipient yet'} · {tones[draft.tone].label} · written {dateFormat.format(new Date(draft.createdAt))}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge tone={status.tone}>
            {status.label}
            {draft.sentAt && ` ${dateFormat.format(new Date(draft.sentAt))}`}
          </Badge>
          {canEdit && (
            <>
              <label htmlFor={statusId} className="sr-only">
                Status of “{draft.subject}”
              </label>
              <select
                id={statusId}
                value={draft.status}
                disabled={update.isPending}
                onChange={(e) => update.mutate({ ...current, status: e.target.value as OutreachStatus })}
                className={selectClass}
              >
                <option value="DRAFT">Not sent</option>
                <option value="SENT">Sent</option>
                <option value="REPLIED">Replied</option>
              </select>
            </>
          )}
        </div>
      </div>

      {/* The letter, as it would come out of the typewriter. */}
      <div className="rounded-sm bg-paper px-6 py-6 font-script text-[13px] leading-relaxed whitespace-pre-wrap text-ink shadow-xl ring-1 ring-ink/20 sm:px-8">
        {draft.body}
      </div>

      <ErrorAlert error={update.error} />

      {confirmingDelete ? (
        <ConfirmDelete
          title={`Delete “${draft.subject}”?`}
          confirmLabel="Delete email"
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirmingDelete(false)}
        >
          The email is deleted from CineScout. Anything already sent from your email app is not affected.
        </ConfirmDelete>
      ) : (
        <div className="flex flex-wrap justify-end gap-2">
          {canEdit && (
            <>
              <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
                Delete
              </Button>
              <Button variant="secondary" onClick={() => setEditing(true)}>
                Edit
              </Button>
            </>
          )}
          <Button
            variant="secondary"
            onClick={() =>
              navigator.clipboard.writeText(emailText(draft)).then(
                () => setCopied(true),
                () => setCopied(false),
              )
            }
          >
            {copied ? 'Copied' : 'Copy'}
          </Button>
          <a href={mailtoLink(draft)} className={linkButton('primary')}>
            Open in email app
          </a>
        </div>
      )}
    </article>
  )
}

function EditDraftForm({
  draft,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  draft: OutreachDraft
  busy: boolean
  error: unknown
  onSubmit: (changes: Pick<UpdateOutreachRequest, 'subject' | 'body' | 'recipientName' | 'recipientEmail'>) => void
  onCancel: () => void
}) {
  const [values, setValues] = useState({
    subject: draft.subject,
    body: draft.body,
    recipientName: draft.recipientName ?? '',
    recipientEmail: draft.recipientEmail ?? '',
  })
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })
  const server = fieldErrors(error)
  const email = values.recipientEmail.trim()
  const emailValid = email === '' || looksLikeEmail(email)
  const canSubmit = values.subject.trim() !== '' && values.body.trim() !== '' && emailValid

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    onSubmit({
      subject: values.subject.trim(),
      body: values.body,
      recipientName: blankToNull(values.recipientName),
      recipientEmail: blankToNull(email),
    })
  }

  return (
    <form onSubmit={submit} aria-label="Edit email" className="space-y-4 rounded-xl border border-cue bg-white p-5" noValidate>
      <ErrorAlert error={error} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Recipient name" maxLength={200} value={values.recipientName} onChange={set('recipientName')} error={server.recipientName} />
        <TextField
          label="Recipient email"
          type="email"
          maxLength={254}
          value={values.recipientEmail}
          onChange={set('recipientEmail')}
          error={emailValid ? server.recipientEmail : 'Enter an email address like owner@example.com.'}
        />
      </div>
      <TextField label="Subject" required maxLength={300} value={values.subject} onChange={set('subject')} error={server.subject} />
      <TextArea label="Message" required maxLength={10000} rows={14} value={values.body} onChange={set('body')} error={server.body} />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" busy={busy} disabled={!canSubmit}>
          Save email
        </Button>
      </div>
    </form>
  )
}
