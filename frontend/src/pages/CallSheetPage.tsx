import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ChevronLeft, Printer, Share2 } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import { CallSheet } from '../components/CallSheet'
import { Eyebrow } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { NotFoundPage } from './NotFoundPage'
import { usePageTitle } from '../lib/usePageTitle'

/**
 * The shoot on paper: for each day, which scenes, where, and who to call there. Laid out as a typed sheet
 * on the page, and printed as just that sheet. A scene without a confirmed location says so (TBC) rather
 * than being left off, as the sheet is often what shows the gap.
 */
export function CallSheetPage() {
  const { projectId = '' } = useParams()
  const { api, user } = useSession()
  const project = useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => api.projects.get(projectId) })
  usePageTitle(project.data && `Call sheet: ${project.data.title}`)
  const schedule = useQuery({
    queryKey: queryKeys.projectSchedule(projectId),
    queryFn: () => api.projects.schedule(projectId),
    enabled: project.isSuccess,
    refetchOnMount: 'always',
  })

  if (project.isPending) return <Spinner label="Loading project" />
  if (project.isError) {
    if (isNotFound(project.error)) return <NotFoundPage />
    return <ErrorAlert error={project.error} onRetry={() => project.refetch()} />
  }

  return (
    <div className="space-y-6">
      <div className="no-print flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-3">
          <Link to={`/projects/${projectId}?tab=schedule`} className="inline-flex items-center gap-1 text-sm text-stone-400 hover:text-stone-200">
            <ChevronLeft aria-hidden className="size-4" />
            {project.data.title}
          </Link>
          <Eyebrow icon={Printer}>For the crew</Eyebrow>
          <h1 className="gold-leaf font-display text-6xl leading-none">Call sheet</h1>
        </div>
        <Button onClick={() => window.print()} disabled={!schedule.data}>
          <Printer aria-hidden className="size-4" />
          Print
        </Button>
      </div>

      {schedule.isPending ? (
        <Spinner label="Loading schedule" />
      ) : schedule.isError ? (
        <ErrorAlert error={schedule.error} onRetry={() => schedule.refetch()} />
      ) : (
        <>
          <SharePanel projectId={projectId} />
          <CallSheet title={project.data.title} locationArea={project.data.locationArea} preparedBy={user.displayName} schedule={schedule.data} />
        </>
      )}
    </div>
  )
}

/** Sharing the sheet with the crew: a link that works without an account, and the way to cut it off. */
function SharePanel({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [copied, setCopied] = useState(false)
  const link = useQuery({
    queryKey: queryKeys.callSheetLink(projectId),
    queryFn: () => api.projects.callSheetLink(projectId).catch((e) => (isNotFound(e) ? null : Promise.reject(e))),
  })
  const share = useMutation({
    mutationFn: () => api.projects.shareCallSheet(projectId),
    onSuccess: (created) => queryClient.setQueryData(queryKeys.callSheetLink(projectId), created),
  })
  const stop = useMutation({
    mutationFn: () => api.projects.stopSharingCallSheet(projectId),
    onSuccess: () => queryClient.setQueryData(queryKeys.callSheetLink(projectId), null),
  })
  const url = link.data ? `${window.location.origin}/call-sheet/${link.data.token}` : null

  async function copy() {
    if (!url) return
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <section aria-labelledby="share-heading" className="no-print gilt mx-auto max-w-4xl space-y-3 rounded-xl border border-white/[0.07] bg-reel/80 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <h2 id="share-heading" className="flex items-center gap-2 font-display text-2xl leading-none text-stone-50">
            <Share2 aria-hidden className="size-4 text-amber-300" />
            Share with the crew
          </h2>
          <p className="text-sm text-stone-400">
            {url
              ? 'Anyone with this link can read the call sheet, venues and contacts included, without an account.'
              : 'Make a link to this call sheet that works without an account. You can stop it at any time.'}
          </p>
        </div>
        {!link.isPending && !url && (
          <Button variant="secondary" busy={share.isPending} onClick={() => share.mutate()}>
            Make a link
          </Button>
        )}
      </div>
      <ErrorAlert error={link.error ?? share.error ?? stop.error} />
      {url && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            readOnly
            aria-label="Call sheet link"
            value={url}
            onFocus={(e) => e.target.select()}
            className="min-w-0 flex-1 rounded-md border border-white/10 bg-black/50 px-3 py-2 font-mono text-xs text-stone-200"
          />
          <Button variant="secondary" onClick={copy}>
            {copied ? 'Copied' : 'Copy link'}
          </Button>
          <Button variant="ghost" busy={stop.isPending} onClick={() => stop.mutate()}>
            Stop sharing
          </Button>
        </div>
      )}
    </section>
  )
}
