import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Copy, Crown, Link2, UserPlus, Users } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { fieldErrors, isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { AddMemberResult, Crew, Invite, Member, Project, ProjectRole } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { Stamp } from '../components/stickers'
import { Card, Eyebrow, Section, Tabs, type TabItem } from '../components/surfaces'
import { Button, ErrorAlert, Spinner, TextField } from '../components/ui'
import { formatDate } from '../lib/format'
import { usePageTitle } from '../lib/usePageTitle'
import { NotFoundPage } from './NotFoundPage'

const roleLabels: Record<ProjectRole, string> = { OWNER: 'Owner', EDITOR: 'Editor', VIEWER: 'Viewer' }
const roleHints: Record<ProjectRole, string> = {
  OWNER: 'Runs the project and its crew',
  EDITOR: 'Scouts, edits and writes to venues',
  VIEWER: 'Reads everything, changes nothing',
}

/** A project's settings, starting with its crew. */
export function ProjectSettingsPage() {
  const { projectId = '' } = useParams()
  const { api } = useSession()
  const project = useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => api.projects.get(projectId) })
  usePageTitle(project.data ? `Crew · ${project.data.title}` : 'Crew')

  if (project.isPending) return <Spinner label="Loading project" />
  if (project.isError) {
    if (isNotFound(project.error)) return <NotFoundPage />
    return <ErrorAlert error={project.error} onRetry={() => project.refetch()} />
  }
  return <Settings project={project.data} />
}

type TabKey = 'members'

const tabs: TabItem<TabKey>[] = [{ key: 'members', label: 'Members', icon: Users }]

function Settings({ project }: { project: Project }) {
  const [params, setParams] = useSearchParams()
  const tab = tabs.find((item) => item.key === params.get('tab'))?.key ?? 'members'
  return (
    <div className="space-y-8">
      <Link
        to={`/projects/${project.id}`}
        className="inline-flex items-center gap-1 font-script text-sm font-bold tracking-[0.06em] text-muted uppercase hover:text-ink"
      >
        <ChevronLeft aria-hidden className="size-4" />
        {project.title}
      </Link>
      <header className="space-y-2">
        <Eyebrow icon={Users}>Project settings</Eyebrow>
        <h1 className="font-display text-5xl leading-none">Crew</h1>
        <p className="text-muted">
          You are this project’s <strong className="text-ink">{roleLabels[project.role].toLowerCase()}</strong>: {roleHints[project.role].toLowerCase()}.
        </p>
      </header>
      <Tabs label="Project settings" items={tabs} selected={tab} onSelect={(key) => setParams(key === 'members' ? {} : { tab: key })}>
        {tab === 'members' && <MembersPanel project={project} />}
      </Tabs>
    </div>
  )
}

function MembersPanel({ project }: { project: Project }) {
  const { api } = useSession()
  const crew = useQuery({ queryKey: queryKeys.crew(project.id), queryFn: () => api.projects.crew(project.id) })
  const owner = project.role === 'OWNER'
  return (
    <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <Section titleId="crew-heading" title="Members" eyebrow="Who is on this project" icon={Users}>
        {crew.isPending ? (
          <Spinner label="Loading the crew" />
        ) : crew.isError ? (
          <ErrorAlert error={crew.error} onRetry={() => crew.refetch()} />
        ) : (
          <ul aria-label="Members" className="space-y-3">
            {crew.data.members.map((member) => (
              <li key={member.userId}>
                <MemberRow project={project} member={member} />
              </li>
            ))}
          </ul>
        )}
      </Section>
      {owner && (
        <div className="space-y-8">
          <InviteForm project={project} />
          {crew.data && crew.data.invites.length > 0 && <OpenInvites project={project} invites={crew.data.invites} />}
        </div>
      )}
    </div>
  )
}

/** A change to the crew; afterwards the crew, the project (whose role may have changed) and the project list are fetched again. */
function useCrewChange<T, V = void>(projectId: string, mutationFn: (variables: V) => Promise<T>, onSuccess?: (result: T) => void) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.crew(projectId) })
      queryClient.invalidateQueries({ queryKey: queryKeys.project(projectId) })
      queryClient.invalidateQueries({ queryKey: queryKeys.projects, refetchType: 'none' })
      onSuccess?.(result)
    },
  })
}

const selectClass =
  'rounded-lg border-2 border-ink bg-white px-2 py-1.5 text-sm font-semibold text-ink focus:ring-4 focus:ring-cue/25 focus:outline-none disabled:opacity-50'

function MemberRow({ project, member }: { project: Project; member: Member }) {
  const { api, user } = useSession()
  const navigate = useNavigate()
  const roleId = useId()
  const self = member.userId === user.id
  const owner = project.role === 'OWNER'
  const [confirming, setConfirming] = useState<'remove' | 'transfer' | null>(null)
  const setRole = useCrewChange(project.id, (role: ProjectRole) => api.projects.changeRole(project.id, member.userId, role))
  const remove = useCrewChange(
    project.id,
    () => api.projects.removeMember(project.id, member.userId),
    () => (self ? navigate('/projects', { replace: true }) : setConfirming(null)),
  )
  const transfer = useCrewChange(project.id, () => api.projects.transfer(project.id, member.userId), () => setConfirming(null))

  return (
    <Card className="space-y-3 p-4">
      <div className="flex flex-wrap items-center gap-4">
        <span aria-hidden className="flex size-11 shrink-0 items-center justify-center rounded-full bg-go-soft font-display text-lg text-go-ink">
          {member.displayName.slice(0, 1).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1">
          <p className="font-display text-xl leading-tight">
            {member.displayName}
            {self && <span className="ml-2 font-script text-xs text-muted uppercase">(you)</span>}
          </p>
          <p className="truncate font-script text-sm text-muted">{member.email}</p>
          <p className="text-xs text-subtle">Joined {formatDate(member.joinedAt.slice(0, 10))}</p>
        </div>
        {owner && member.role !== 'OWNER' ? (
          <>
            <label htmlFor={roleId} className="sr-only">
              Role of {member.displayName}
            </label>
            <select
              id={roleId}
              value={member.role}
              disabled={setRole.isPending}
              onChange={(e) => setRole.mutate(e.target.value as ProjectRole)}
              className={selectClass}
            >
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </>
        ) : (
          <Stamp announce tone={member.role === 'OWNER' ? 'ink' : member.role === 'EDITOR' ? 'cue' : 'go'} className="text-sm">
            {roleLabels[member.role]}
          </Stamp>
        )}
      </div>
      <ErrorAlert error={setRole.error} />
      {confirming === 'remove' ? (
        <ConfirmDelete
          title={self ? `Leave “${project.title}”?` : `Remove ${member.displayName}?`}
          confirmLabel={self ? 'Leave project' : 'Remove from crew'}
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirming(null)}
        >
          {self ? 'You lose access to it until the owner adds you again.' : 'They lose access to the project at once.'}
        </ConfirmDelete>
      ) : confirming === 'transfer' ? (
        <ConfirmDelete
          title={`Make ${member.displayName} the owner?`}
          confirmLabel="Hand ownership over"
          busy={transfer.isPending}
          error={transfer.error}
          onConfirm={() => transfer.mutate()}
          onCancel={() => setConfirming(null)}
        >
          You stay on as an editor; only they can then archive or delete the project and manage its crew.
        </ConfirmDelete>
      ) : (
        (owner ? member.role !== 'OWNER' : self) && (
          <div className="flex flex-wrap justify-end gap-2">
            {owner && (
              <Button variant="ghost" onClick={() => setConfirming('transfer')}>
                <Crown aria-hidden className="size-4" />
                Make owner
              </Button>
            )}
            <Button variant="ghost" onClick={() => setConfirming('remove')}>
              {self ? 'Leave project' : 'Remove'}
            </Button>
          </div>
        )
      )}
    </Card>
  )
}

function InviteForm({ project }: { project: Project }) {
  const { api } = useSession()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<ProjectRole>('EDITOR')
  const [result, setResult] = useState<AddMemberResult | null>(null)
  const roleId = useId()
  const add = useCrewChange(project.id, () => api.projects.addMember(project.id, email.trim(), role), (added) => {
    setResult(added)
    setEmail('')
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setResult(null)
    add.mutate()
  }

  return (
    <Card className="space-y-4 p-5">
      <div className="space-y-1">
        <Eyebrow icon={UserPlus}>Bring someone on</Eyebrow>
        <h2 className="font-display text-2xl leading-none">Add to the crew</h2>
        <p className="text-sm text-muted">Someone with an account joins at once. For anyone else you get a link to send them.</p>
      </div>
      <form onSubmit={submit} className="space-y-3" noValidate>
        <ErrorAlert error={fieldErrors(add.error).email ? null : add.error} />
        <TextField label="Email" type="email" autoComplete="off" required value={email} onChange={(e) => setEmail(e.target.value)} error={fieldErrors(add.error).email} />
        <div className="space-y-1">
          <label htmlFor={roleId} className="block font-script text-[13px] font-bold tracking-[0.08em] text-ink uppercase">
            Role
          </label>
          <select id={roleId} value={role} onChange={(e) => setRole(e.target.value as ProjectRole)} className={`${selectClass} w-full py-2`}>
            <option value="EDITOR">Editor: {roleHints.EDITOR.toLowerCase()}</option>
            <option value="VIEWER">Viewer: {roleHints.VIEWER.toLowerCase()}</option>
          </select>
        </div>
        <Button type="submit" busy={add.isPending} disabled={!email.trim()} className="w-full">
          Add to crew
        </Button>
      </form>
      {result?.member && (
        <p role="status" className="rounded-lg border-2 border-go-mid bg-go-wash px-3 py-2 text-sm text-go-ink">
          {result.member.displayName} joined as {roleLabels[result.member.role].toLowerCase()}.
        </p>
      )}
      {result?.invite && <InviteLink invite={result.invite} />}
    </Card>
  )
}

/** The link of an invite just made: shown once, as only its hash is kept. */
function InviteLink({ invite }: { invite: Invite }) {
  const [copied, setCopied] = useState(false)
  const url = `${window.location.origin}/invite/${invite.token}`
  return (
    <div role="status" className="space-y-2 rounded-lg border-2 border-dashed border-cue bg-cue-wash p-3">
      <p className="text-sm text-ink">
        <strong>{invite.email}</strong> has no account yet. Send them this link; it works once, until {formatDate(invite.expiresAt.slice(0, 10))}.
        Copy it now: it is not shown again.
      </p>
      <div className="flex gap-2">
        <input readOnly value={url} aria-label="Invite link" onFocus={(e) => e.target.select()} className="min-w-0 flex-1 rounded-lg border-2 border-line bg-white px-2 py-1.5 font-mono text-xs" />
        <Button
          variant="secondary"
          onClick={() =>
            navigator.clipboard.writeText(url).then(
              () => setCopied(true),
              () => setCopied(false),
            )
          }
        >
          <Copy aria-hidden className="size-4" />
          {copied ? 'Copied' : 'Copy'}
        </Button>
      </div>
    </div>
  )
}

function OpenInvites({ project, invites }: { project: Project; invites: Crew['invites'] }) {
  return (
    <Card className="space-y-3 p-5">
      <div className="space-y-1">
        <Eyebrow icon={Link2}>Waiting to join</Eyebrow>
        <h2 className="font-display text-2xl leading-none">Open invites</h2>
      </div>
      <ul aria-label="Open invites" className="space-y-2">
        {invites.map((invite) => (
          <li key={invite.id}>
            <OpenInvite project={project} invite={invite} />
          </li>
        ))}
      </ul>
    </Card>
  )
}

function OpenInvite({ project, invite }: { project: Project; invite: Invite }) {
  const { api } = useSession()
  const revoke = useCrewChange(project.id, () => api.projects.revokeInvite(project.id, invite.id))
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line-soft pb-2 last:border-0">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold">{invite.email}</p>
        <p className="text-xs text-muted">
          {roleLabels[invite.role]} · until {formatDate(invite.expiresAt.slice(0, 10))}
        </p>
      </div>
      <Button variant="ghost" busy={revoke.isPending} onClick={() => revoke.mutate()} aria-label={`Withdraw the invite to ${invite.email}`}>
        Withdraw
      </Button>
      <ErrorAlert error={revoke.error} />
    </div>
  )
}
