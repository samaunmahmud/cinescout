import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { CalendarClock, CalendarDays, ChevronRight, Columns3, FileText, Film, Plus, Search, Sparkles, Trash2 } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import type { Page, Scene } from '../api/types'
import { ActionMenu } from '../components/ActionMenu'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { ParseStatusBadge } from '../components/ParseStatusBadge'
import { linkButton } from '../components/buttonStyles'
import { EmptyState, Section, Slate } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { batchParseSummary, formatShootWindow, sceneLabel } from '../lib/format'
import { useCanEdit } from '../components/projectRole'

/** A project's scenes in script order, each as a slate. */
export function ScenesSection({ projectId }: { projectId: string }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const [page, setPage] = usePageParam()
  const [params, setParams] = useSearchParams()
  const search = params.get('q')?.trim() ?? ''
  const scenes = useQuery({
    queryKey: queryKeys.scenePage(projectId, page, search),
    queryFn: () => api.scenes.list(projectId, page, search),
    placeholderData: previousPageOf<Page<Scene>>(queryKeys.sceneList(projectId)),
  })
  useStayInRange(scenes.data, setPage)

  const queryClient = useQueryClient()
  const analyse = useMutation({
    mutationFn: () => api.scenes.parseAll(projectId),
    // Also after a failure: some scenes may have been analysed (or marked failed) before it.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(projectId) })
      queryClient.invalidateQueries({ queryKey: ['scenes', 'detail'] })
    },
  })
  // Offered while a scene in view is waiting, or the last run said more are (they may be on another page).
  const waiting = (scenes.data?.items.some((scene) => scene.parseStatus === 'PENDING') ?? false) || (analyse.data?.remaining ?? 0) > 0

  return (
    <Section
      titleId="scenes-heading"
      title="Scenes"
      eyebrow="The shooting script"
      icon={Film}
      actions={
        <>
          {canEdit && waiting && (
            <Button variant="secondary" busy={analyse.isPending} onClick={() => analyse.mutate()}>
              {!analyse.isPending && <Sparkles aria-hidden className="size-4" />}
              Analyse scenes
            </Button>
          )}
          {canEdit && (
            <>
              <Link to={`/projects/${projectId}/scenes/import`} className={linkButton('ghost')}>
                <FileText aria-hidden className="size-4" />
                Import script
              </Link>
              <Link to={`/projects/${projectId}/scenes/new`} className={linkButton()}>
                <Plus aria-hidden className="size-4" />
                Add scene
              </Link>
            </>
          )}
        </>
      }
    >
      {analyse.isPending ? (
        <Spinner label="Reading the scenes that have not been analysed yet. This can take a minute." />
      ) : analyse.isError ? (
        <ErrorAlert error={analyse.error} />
      ) : (
        analyse.data && (
          <p role="status" className="rounded-lg border border-go-mid bg-go-wash px-4 py-3 text-sm text-go-ink">
            {batchParseSummary(analyse.data)}
          </p>
        )
      )}

      {/* Worth a search box once there is a list to search, and while a search is what emptied it. */}
      {(search !== '' || (scenes.data?.totalItems ?? 0) > 1) && (
        <SceneSearch
          key={search}
          current={search}
          onSearch={(next) =>
            setParams((current) => {
              const updated = new URLSearchParams(current)
              if (next) updated.set('q', next)
              else updated.delete('q')
              updated.delete('page')
              return updated
            })
          }
        />
      )}

      {scenes.isPending ? (
        <Spinner label="Loading scenes" />
      ) : scenes.isError ? (
        <ErrorAlert error={scenes.error} onRetry={() => scenes.refetch()} />
      ) : scenes.data.items.length === 0 && search !== '' ? (
        <EmptyState icon={Search}>No scene mentions “{search}”.</EmptyState>
      ) : scenes.data.items.length === 0 ? (
        <EmptyState icon={Film}>No scenes yet. Add one with its script, or import a whole screenplay, and CineScout works out what kind of location each scene needs.</EmptyState>
      ) : (
        <>
          <ul className="space-y-3">
            {scenes.data.items.map((scene) => (
              <li key={scene.id}>
                <SceneRow scene={scene} projectId={projectId} />
              </li>
            ))}
          </ul>
          <Pager data={scenes.data} onChange={setPage} label="Scene pages" />
        </>
      )}
    </Section>
  )
}

/** Searches on submit, not per keystroke; clearing the box and submitting shows every scene again. */
function SceneSearch({ current, onSearch }: { current: string; onSearch: (search: string) => void }) {
  const [text, setText] = useState(current)

  function submit(e: FormEvent) {
    e.preventDefault()
    onSearch(text.trim())
  }

  return (
    <form role="search" onSubmit={submit} className="flex flex-wrap items-center gap-2">
      <label className="relative min-w-0 flex-1 sm:max-w-sm">
        <span className="sr-only">Search scenes</span>
        <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-subtle" />
        <input
          type="search"
          value={text}
          maxLength={100}
          onChange={(e) => setText(e.target.value)}
          placeholder="Search titles, scripts and settings"
          className="w-full rounded-lg border border-line bg-paper py-2 pr-3 pl-9 text-sm text-ink placeholder:text-subtle focus:border-ink focus:ring-2 focus:ring-cue/25 focus:outline-none"
        />
      </label>
      <Button type="submit" variant="secondary">
        Search
      </Button>
      {current !== '' && (
        <Button variant="ghost" onClick={() => onSearch('')}>
          Show all
        </Button>
      )}
    </form>
  )
}

/** One scene as a slate that opens it, with a ⋯ menu of quick actions beside it (outside the link). */
function SceneRow({ scene, projectId }: { scene: Scene; projectId: string }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const shootWindow = formatShootWindow(scene.shootDateStart, scene.shootDateEnd)
  const analyse = useMutation({
    mutationFn: () => api.scenes.parse(scene.id),
    onSuccess: (parsed) => {
      queryClient.setQueryData(queryKeys.scene(parsed.id), parsed)
      return queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(projectId) })
    },
  })
  const remove = useMutation({
    mutationFn: () => api.scenes.remove(scene.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.scene(scene.id) })
      return queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(projectId) })
    },
  })
  const label = sceneLabel(scene)

  return (
    <div className="space-y-2">
      <div className="relative">
        <Link
          to={`/scenes/${scene.id}`}
          className="group grid grid-cols-[auto_minmax(0,1fr)] items-center gap-x-4 gap-y-2 board-card rounded-lg bg-paper p-3 pr-14 transition hover:border-ink hover:bg-ground focus-visible:outline-2 focus-visible:outline-ink sm:flex sm:pr-16"
        >
          <Slate number={scene.sceneNumber} />
          <span className="min-w-0 flex-1 space-y-1">
            <span className="line-clamp-2 text-lg leading-snug font-semibold text-ink group-hover:text-cue-ink sm:line-clamp-1">{label}</span>
            <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted">
              {scene.requirements?.settingType && <span className="text-graphite">{scene.requirements.settingType}</span>}
              <span className="inline-flex items-center gap-1">
                <CalendarDays aria-hidden className="size-3.5" />
                {shootWindow ?? 'Not scheduled'}
              </span>
            </span>
          </span>
          {/* On a phone the badge sits under the title instead of squeezing it. */}
          <span className="col-start-2 sm:col-auto">
            <ParseStatusBadge status={scene.parseStatus} />
          </span>
          <ChevronRight aria-hidden className="hidden size-5 text-subtle transition group-hover:translate-x-0.5 group-hover:text-cue-ink sm:block" />
        </Link>
        <ActionMenu
          label={`Quick actions for ${label}`}
          className="!absolute top-2.5 right-2.5 sm:top-1/2 sm:right-3 sm:-translate-y-1/2"
          actions={[
            { label: 'Open scene', icon: ChevronRight, to: `/scenes/${scene.id}` },
            { label: 'Analyse with AI', icon: Sparkles, onSelect: () => analyse.mutate(), hidden: !canEdit || scene.parseStatus === 'PARSED' },
            { label: 'Add a venue by hand', icon: Plus, to: `/scenes/${scene.id}/locations/new`, hidden: !canEdit },
            { label: 'Compare venues', icon: Columns3, to: `/scenes/${scene.id}/compare` },
            { label: 'Set shoot dates', icon: CalendarClock, to: `/projects/${projectId}?tab=schedule` },
            { label: 'Delete scene…', icon: Trash2, danger: true, onSelect: () => setConfirmingDelete(true), hidden: !canEdit },
          ]}
        />
      </div>
      {analyse.isPending && <Spinner label={`Analysing ${label}`} />}
      <ErrorAlert error={analyse.error} />
      {confirmingDelete && (
        <ConfirmDelete
          title={`Delete “${scene.title}”?`}
          confirmLabel="Delete scene"
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirmingDelete(false)}
        >
          This also deletes the locations scouted for it and their outreach drafts. It cannot be undone.
        </ConfirmDelete>
      )}
    </div>
  )
}

