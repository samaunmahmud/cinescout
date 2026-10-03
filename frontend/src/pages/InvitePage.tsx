import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Ticket } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { ProjectRole } from '../api/types'
import { useSession } from '../auth/context'
import { linkButton } from '../components/buttonStyles'
import { TapeLabel } from '../components/stickers'
import { Card, Eyebrow } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { formatDate } from '../lib/format'
import { usePageTitle } from '../lib/usePageTitle'

const roleWords: Record<ProjectRole, string> = {
  OWNER: 'the owner',
  EDITOR: 'an editor: you can scout, edit and write to venues',
  VIEWER: 'a viewer: you can see everything but not change it',
}

/** Where an invite link lands: what it offers, and a button to join. A visitor without a session logs in first. */
export function InvitePage() {
  const { token = '' } = useParams()
  const { api, user } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  usePageTitle('Invitation')
  const invite = useQuery({ queryKey: queryKeys.invite(token), queryFn: () => api.invites.preview(token), retry: false })
  const accept = useMutation({
    mutationFn: () => api.invites.accept(token),
    onSuccess: (accepted) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.projects })
      navigate(`/projects/${accepted.projectId}`, { replace: true })
    },
  })

  if (invite.isPending) return <Spinner label="Opening the invitation" />
  return (
    <div className="mx-auto max-w-lg pt-6">
      <Card className="relative space-y-5 px-6 pt-10 pb-6">
        <TapeLabel tilt={-2} className="absolute -top-4 left-1/2 -translate-x-1/2">
          Crew call
        </TapeLabel>
        <Eyebrow icon={Ticket}>Invitation</Eyebrow>
        {invite.isError ? (
          isNotFound(invite.error) ? (
            <>
              <h1 className="font-display text-3xl leading-none">This link does not work</h1>
              <p className="text-muted">It may have been withdrawn, or mistyped. Ask the project’s owner for a new one.</p>
            </>
          ) : (
            <ErrorAlert error={invite.error} onRetry={() => invite.refetch()} />
          )
        ) : (
          <>
            <h1 className="font-display text-4xl leading-none">{invite.data.projectTitle}</h1>
            <p className="text-[15px] text-graphite">
              {invite.data.invitedBy ?? 'The owner'} invited <strong>{invite.data.email}</strong> to join as {roleWords[invite.data.role]}.
            </p>
            {invite.data.state === 'OPEN' ? (
              <>
                {user.email.toLowerCase() !== invite.data.email.toLowerCase() && (
                  <p className="rounded-lg border-2 border-cue bg-cue-wash px-3 py-2 text-sm text-cue-deep">
                    You are logged in as {user.email}. Log in with {invite.data.email} to accept.
                  </p>
                )}
                <ErrorAlert error={accept.error} />
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <span className="text-xs text-muted">Open until {formatDate(invite.data.expiresAt.slice(0, 10))}</span>
                  <Button busy={accept.isPending} onClick={() => accept.mutate()}>
                    Join the crew
                  </Button>
                </div>
              </>
            ) : invite.data.state === 'ACCEPTED' ? (
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-muted">This invitation has been used.</p>
                <Link to={`/projects/${invite.data.projectId}`} className={linkButton('secondary')}>
                  Open the project
                </Link>
              </div>
            ) : (
              <p className="text-muted">This invitation has expired. Ask the project’s owner for a new one.</p>
            )}
          </>
        )}
      </Card>
    </div>
  )
}
