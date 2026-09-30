import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { CalendarDays, ChevronRight, FileText, Film, Plus, Search, Sparkles } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import type { Page, Scene } from '../api/types'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { ParseStatusBadge } from '../components/ParseStatusBadge'
import { linkButton } from '../components/buttonStyles'
import { EmptyState, Section, Slate } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { batchParseSummary, formatShootWindow, sceneLabel } from '../lib/format'

/** A project's scenes in script order, each as a slate. */
export function ScenesSection({ projectId }: { projectId: string }) {
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
          {waiting && (
            <Button variant="secondary" busy={analyse.isPending} onClick={() => analyse.mutate()}>
              {!analyse.isPending && <Sparkles aria-hidden className="size-4" />}
              Analyse scenes
            </Button>
          )}
          <Link to={`/projects/${projectId}/scenes/import`} className={linkButton('ghost')}>
            <FileText aria-hidden className="size-4" />
            Import script
          </Link>
          <Link to={`/projects/${projectId}/scenes/new`} className={linkButton()}>
            <Plus aria-hidden className="size-4" />
            Add scene
          </Link>
        </>
      }
    >
      {analyse.isPending ? (
        <Spinner label="Reading the scenes that have not been analysed yet. This can take a minute." />
      ) : analyse.isError ? (
        <ErrorAlert error={analyse.error} />
      ) : (
        analyse.data && (
          <p role="status" className="rounded-lg border border-emerald-900/70 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-200">
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
            {scenes.data.items.map((scene) => {
              const shootWindow = formatShootWindow(scene.shootDateStart, scene.shootDateEnd)
              return (
                <li key={scene.id}>
                  <Link
                    to={`/scenes/${scene.id}`}
                    className="group flex items-center gap-4 rounded-xl border border-white/[0.07] bg-reel/80 p-3 pr-5 transition hover:border-amber-400/40 hover:bg-frame focus-visible:outline-2 focus-visible:outline-amber-400"
                  >
                    <Slate number={scene.sceneNumber} />
                    <span className="min-w-0 flex-1 space-y-1">
                      <span className="block truncate text-lg font-semibold text-stone-100 group-hover:text-amber-200">{sceneLabel(scene)}</span>
                      <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-stone-400">
                        {scene.requirements?.settingType && <span className="text-stone-300">{scene.requirements.settingType}</span>}
                        <span className="inline-flex items-center gap-1">
                          <CalendarDays aria-hidden className="size-3.5" />
                          {shootWindow ?? 'Not scheduled'}
                        </span>
                      </span>
                    </span>
                    <ParseStatusBadge status={scene.parseStatus} />
                    <ChevronRight aria-hidden className="size-5 text-stone-600 transition group-hover:translate-x-0.5 group-hover:text-amber-400" />
                  </Link>
                </li>
              )
            })}
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
        <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-stone-500" />
        <input
          type="search"
          value={text}
          maxLength={100}
          onChange={(e) => setText(e.target.value)}
          placeholder="Search titles, scripts and settings"
          className="w-full rounded-lg border border-white/10 bg-ink/70 py-2 pr-3 pl-9 text-sm text-stone-100 shadow-inner shadow-black/40 placeholder:text-stone-500 focus:border-amber-400/80 focus:ring-2 focus:ring-amber-400/30 focus:outline-none"
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
