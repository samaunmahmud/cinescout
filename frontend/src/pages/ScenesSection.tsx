import { useQuery } from '@tanstack/react-query'
import { CalendarDays, ChevronRight, Film, Plus } from 'lucide-react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import { ParseStatusBadge } from '../components/ParseStatusBadge'
import { linkButton } from '../components/buttonStyles'
import { EmptyState, Section, Slate } from '../components/surfaces'
import { ErrorAlert, Spinner } from '../components/ui'
import { formatShootWindow, sceneLabel } from '../lib/format'

/** A project's scenes in script order, each as a slate. */
export function ScenesSection({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const scenes = useQuery({ queryKey: queryKeys.sceneList(projectId), queryFn: () => api.scenes.list(projectId) })

  return (
    <Section
      titleId="scenes-heading"
      title="Scenes"
      eyebrow="The shooting script"
      icon={Film}
      actions={
        <Link to={`/projects/${projectId}/scenes/new`} className={linkButton()}>
          <Plus aria-hidden className="size-4" />
          Add scene
        </Link>
      }
    >
      {scenes.isPending ? (
        <Spinner label="Loading scenes" />
      ) : scenes.isError ? (
        <ErrorAlert error={scenes.error} onRetry={() => scenes.refetch()} />
      ) : scenes.data.length === 0 ? (
        <EmptyState icon={Film}>No scenes yet. Add one with its script, and CineScout works out what kind of location it needs.</EmptyState>
      ) : (
        <ul className="space-y-3">
          {scenes.data.map((scene) => {
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
      )}
    </Section>
  )
}
