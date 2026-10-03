import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, Inbox } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { OutreachDraft } from '../api/types'
import { useSession } from '../auth/context'
import { useCanEdit } from '../components/projectRole'
import { Badge, Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'

const when = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

/**
 * What came back from the venue. With reply tracking set up, the email has its own reply address and replies to it
 * arrive here by themselves; without it (or for a reply by phone), a member pastes the reply in or just marks the
 * email replied. Reply text stays with the crew: nothing sends it to the AI.
 */
export function RepliesPanel({ draft }: { draft: OutreachDraft }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const [recording, setRecording] = useState(false)
  const [copied, setCopied] = useState(false)
  const replied = draft.status === 'REPLIED'
  const replies = useQuery({ queryKey: queryKeys.replies(draft.id), queryFn: () => api.outreach.replies(draft.id), enabled: replied })

  if (draft.status === 'DRAFT' && !draft.replyTo) return null
  return (
    <div className="space-y-3 rounded-lg border-2 border-dashed border-line bg-ground p-4">
      {draft.replyTo && (
        <div className="space-y-1.5">
          <p className="text-sm text-graphite">
            Replies to this address come back to CineScout. “Open in email app” puts it in Cc, so the venue’s reply-all reaches it.
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <code className="min-w-0 truncate rounded bg-white px-2 py-1 font-mono text-xs text-ink ring-1 ring-line">{draft.replyTo}</code>
            <Button
              variant="ghost"
              className="!px-2 !py-1 text-xs"
              aria-label="Copy the reply address"
              onClick={() =>
                navigator.clipboard.writeText(draft.replyTo!).then(
                  () => setCopied(true),
                  () => setCopied(false),
                )
              }
            >
              <Copy aria-hidden className="size-3.5" />
              {copied ? 'Copied' : 'Copy'}
            </Button>
          </div>
        </div>
      )}
      {replied && (
        <section aria-label={`Replies to “${draft.subject}”`} className="space-y-2">
          <h4 className="flex items-center gap-1.5 font-script text-xs font-bold tracking-[0.08em] text-muted uppercase">
            <Inbox aria-hidden className="size-3.5" />
            Replies
          </h4>
          {replies.isPending ? (
            <Spinner label="Loading replies" />
          ) : replies.isError ? (
            <ErrorAlert error={replies.error} onRetry={() => replies.refetch()} />
          ) : replies.data.items.length === 0 ? (
            <p className="text-sm text-muted">Marked replied, with no reply recorded.</p>
          ) : (
            <ul className="space-y-3">
              {replies.data.items.map((reply) => (
                <li key={reply.id} className="space-y-1 rounded-md bg-white p-3 ring-1 ring-line">
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-semibold text-ink">{reply.fromName ?? reply.fromAddress ?? 'The venue'}</span>
                    {reply.fromName && reply.fromAddress && <span className="text-muted">&lt;{reply.fromAddress}&gt;</span>}
                    <span className="text-xs text-subtle">{when.format(new Date(reply.receivedAt))}</span>
                    {reply.source === 'MANUAL' && <Badge>Pasted in{reply.recordedBy ? ` by ${reply.recordedBy}` : ''}</Badge>}
                  </div>
                  {reply.subject && <p className="text-sm font-semibold text-graphite">{reply.subject}</p>}
                  {reply.text && <p className="text-[15px] whitespace-pre-wrap text-graphite">{reply.text}</p>}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
      {canEdit && draft.status !== 'DRAFT' &&
        (recording ? (
          <RecordReplyForm draft={draft} onDone={() => setRecording(false)} />
        ) : (
          <div className="flex justify-end">
            <Button variant="ghost" onClick={() => setRecording(true)}>
              {replied ? 'Paste another reply' : 'Paste a reply or mark replied'}
            </Button>
          </div>
        ))}
    </div>
  )
}

function RecordReplyForm({ draft, onDone }: { draft: OutreachDraft; onDone: () => void }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [from, setFrom] = useState({ name: draft.recipientName ?? '', address: draft.recipientEmail ?? '' })
  const [text, setText] = useState('')
  const record = useMutation({
    mutationFn: (withText: boolean) =>
      api.outreach.recordReply(
        draft.id,
        withText ? { fromName: from.name.trim() || null, fromAddress: from.address.trim() || null, text: text.trim() || null } : {},
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.replies(draft.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.outreachList(draft.locationId) })
      queryClient.invalidateQueries({ queryKey: ['outreach', 'project'] })
      onDone()
    },
  })
  const errors = fieldErrors(record.error)

  function submit(e: FormEvent) {
    e.preventDefault()
    record.mutate(true)
  }

  return (
    <form onSubmit={submit} className="space-y-3" noValidate>
      <ErrorAlert error={Object.keys(errors).length > 0 ? null : record.error} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="From (name)" maxLength={200} value={from.name} onChange={(e) => setFrom({ ...from, name: e.target.value })} />
        <TextField
          label="From (email)"
          type="email"
          maxLength={320}
          value={from.address}
          onChange={(e) => setFrom({ ...from, address: e.target.value })}
          error={errors.fromAddress}
        />
      </div>
      <TextArea label="Their reply" rows={4} maxLength={20480} value={text} onChange={(e) => setText(e.target.value)} hint="Stays with the crew; it is never sent to the AI." />
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        {draft.status !== 'REPLIED' && (
          <Button variant="secondary" busy={record.isPending && record.variables === false} onClick={() => record.mutate(false)}>
            Just mark replied
          </Button>
        )}
        <Button type="submit" busy={record.isPending && record.variables === true} disabled={!text.trim()}>
          Save the reply
        </Button>
      </div>
    </form>
  )
}
