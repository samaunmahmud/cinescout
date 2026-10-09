import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MessagesSquare, Reply } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { Location, Member, VenueComment } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { MentionTextArea } from '../components/MentionTextArea'
import { Pager } from '../components/Pager'
import { useCanEdit, useRole } from '../components/projectRole'
import { EmptyState, Section } from '../components/surfaces'
import { Badge, Button, ErrorAlert, Spinner } from '../components/ui'
import { formatDate } from '../lib/format'
import { mentionedIn, splitMentions } from '../lib/mentions'

/**
 * The crew's conversation about a venue, in threads one reply deep. Kept apart from the private notes; a director's
 * comment from their link shows here too, marked as a guest's.
 */
export function CommentsSection({ location, projectId }: { location: Location; projectId: string | undefined }) {
  const { api } = useSession()
  const canEdit = useCanEdit()
  const [page, setPage] = useState(0)
  const comments = useQuery({
    queryKey: queryKeys.commentPage(location.id, page),
    queryFn: () => api.comments.list(location.id, page),
  })
  // The names to offer after an "@": only needed by those who can write.
  const crew = useQuery({
    queryKey: queryKeys.crew(projectId ?? ''),
    queryFn: () => api.projects.crew(projectId!),
    enabled: canEdit && !!projectId,
  })
  const members = crew.data?.members ?? []

  return (
    <Section titleId="comments-heading" title="Comments" eyebrow="Crew talk" icon={MessagesSquare}>
      {comments.isPending ? (
        <Spinner label="Loading comments" />
      ) : comments.isError ? (
        <ErrorAlert error={comments.error} onRetry={() => comments.refetch()} />
      ) : comments.data.items.length === 0 ? (
        <EmptyState icon={MessagesSquare}>
          No comments yet. {canEdit ? 'Start the conversation about this venue below.' : 'The crew’s conversation about this venue shows here.'}
        </EmptyState>
      ) : (
        <>
          <ul aria-label="Comment threads" className="space-y-4">
            {comments.data.items.map((thread) => (
              <li key={thread.id}>
                <Thread thread={thread} locationId={location.id} members={members} />
              </li>
            ))}
          </ul>
          <Pager data={comments.data} onChange={setPage} label="Comment pages" />
        </>
      )}
      {canEdit && <Composer locationId={location.id} members={members} label="Add a comment" submitLabel="Post comment" />}
    </Section>
  )
}

function Thread({ thread, locationId, members }: { thread: VenueComment; locationId: string; members: Member[] }) {
  const canEdit = useCanEdit()
  const [replying, setReplying] = useState(false)
  return (
    <article className="board-card space-y-3 rounded-lg bg-paper p-4">
      <CommentBody comment={thread} members={members} />
      {thread.replies.length > 0 && (
        <ul aria-label={`Replies to ${thread.authorName ?? 'a former member'}`} className="space-y-3 border-l-4 border-line-soft pl-4">
          {thread.replies.map((reply) => (
            <li key={reply.id}>
              <CommentBody comment={reply} members={members} />
            </li>
          ))}
        </ul>
      )}
      {canEdit &&
        (replying ? (
          <div className="border-l-4 border-cue pl-4">
            <Composer
              locationId={locationId}
              parentId={thread.id}
              members={members}
              label={`Reply to ${thread.authorName ?? 'this comment'}`}
              submitLabel="Reply"
              autoFocus
              onDone={() => setReplying(false)}
            />
          </div>
        ) : (
          <div className="flex justify-end">
            <Button variant="ghost" onClick={() => setReplying(true)}>
              <Reply aria-hidden className="size-4" />
              Reply
            </Button>
          </div>
        ))}
    </article>
  )
}

function CommentBody({ comment, members }: { comment: VenueComment; members: Member[] }) {
  const { api, user } = useSession()
  const queryClient = useQueryClient()
  const canEdit = useCanEdit()
  const role = useRole()
  const mine = comment.authorId === user.id
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const remove = useMutation({
    mutationFn: () => api.comments.remove(comment.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.commentList(comment.locationId) }),
  })
  const name = comment.authorName ?? 'Former member'

  if (editing) {
    return (
      <Composer
        locationId={comment.locationId}
        editing={comment}
        members={members}
        label="Edit your comment"
        submitLabel="Save"
        autoFocus
        onDone={() => setEditing(false)}
      />
    )
  }
  return (
    <div className="space-y-1.5">
      <div className="flex flex-wrap items-center gap-2">
        <span aria-hidden className="flex size-7 shrink-0 items-center justify-center rounded-full bg-go-soft font-display text-sm text-go-ink">
          {name.slice(0, 1).toUpperCase()}
        </span>
        <span className="font-semibold text-ink">{name}</span>
        {comment.guest && <Badge tone="cue">Guest · director link</Badge>}
        <span className="text-xs text-subtle">
          {formatDate(comment.createdAt.slice(0, 10))}
          {comment.edited && ' · edited'}
        </span>
      </div>
      <p className="text-[15px] whitespace-pre-line text-graphite">
        {splitMentions(
          comment.body,
          comment.mentions.map((mention) => mention.displayName),
        ).map((piece, index) =>
          piece.mention ? (
            <span key={index} className="rounded bg-cue-wash px-1 font-semibold text-cue-deep">
              {piece.text}
            </span>
          ) : (
            piece.text
          ),
        )}
      </p>
      {confirming ? (
        <ConfirmDelete
          title="Delete this comment?"
          confirmLabel="Delete comment"
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirming(false)}
        >
          {comment.parentId ? 'It is gone for everyone.' : 'It is gone for everyone, and so are the replies to it.'}
        </ConfirmDelete>
      ) : (
        (mine || role === 'OWNER') && (
          <div className="flex gap-1">
            {mine && canEdit && (
              <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setEditing(true)}>
                Edit
              </Button>
            )}
            <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setConfirming(true)} aria-label={`Delete the comment by ${name}`}>
              Delete
            </Button>
          </div>
        )
      )}
    </div>
  )
}

/** Writes a new comment, a reply (`parentId`), or a new wording of one (`editing`). */
function Composer({
  locationId,
  parentId,
  editing,
  members,
  label,
  submitLabel,
  autoFocus = false,
  onDone,
}: {
  locationId: string
  parentId?: string
  editing?: VenueComment
  members: Member[]
  label: string
  submitLabel: string
  autoFocus?: boolean
  onDone?: () => void
}) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [body, setBody] = useState(editing?.body ?? '')
  const [picked, setPicked] = useState<{ userId: string; displayName: string }[]>(editing?.mentions ?? [])
  const save = useMutation({
    mutationFn: () => {
      const mentions = mentionedIn(body, picked).map((member) => member.userId)
      return editing
        ? api.comments.edit(editing.id, { body: body.trim(), mentions })
        : api.comments.post(locationId, { body: body.trim(), parentId: parentId ?? null, mentions })
    },
    onSuccess: () => {
      setBody('')
      setPicked([])
      queryClient.invalidateQueries({ queryKey: queryKeys.commentList(locationId) })
      onDone?.()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-3" noValidate>
      <ErrorAlert error={save.error} />
      <MentionTextArea
        label={label}
        value={body}
        onChange={setBody}
        members={members}
        onMention={(member) => setPicked((now) => [...now, member])}
        rows={parentId || editing ? 2 : 3}
        autoFocus={autoFocus}
      />
      <div className="flex justify-end gap-2">
        {onDone && (
          <Button variant="ghost" onClick={onDone}>
            Cancel
          </Button>
        )}
        <Button type="submit" variant={parentId || editing ? 'secondary' : 'primary'} busy={save.isPending} disabled={!body.trim()}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
