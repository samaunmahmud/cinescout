import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import { ParseStatusBadge } from '../components/ParseStatusBadge'
import { linkButton } from '../components/buttonStyles'
import { ErrorAlert, Spinner } from '../components/ui'
import { formatShootWindow, sceneLabel } from '../lib/format'

/** A project's scenes in script order. */
export function ScenesSection({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const scenes = useQuery({ queryKey: queryKeys.sceneList(projectId), queryFn: () => api.scenes.list(projectId) })

  return (
    <section aria-labelledby="scenes-heading" className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h2 id="scenes-heading" className="text-lg font-semibold">
          Scenes
        </h2>
        <Link to={`/projects/${projectId}/scenes/new`} className={linkButton()}>
          Add scene
        </Link>
      </div>

      {scenes.isPending ? (
        <Spinner label="Loading scenes" />
      ) : scenes.isError ? (
        <ErrorAlert error={scenes.error} onRetry={() => scenes.refetch()} />
      ) : scenes.data.length === 0 ? (
        <p className="rounded-lg border border-dashed border-stone-800 px-6 py-10 text-center text-stone-400">
          No scenes yet. Add one with its script, and CineScout works out what kind of location it needs.
        </p>
      ) : (
        <ul className="divide-y divide-stone-800 overflow-hidden rounded-lg border border-stone-800">
          {scenes.data.map((scene) => {
            const shootWindow = formatShootWindow(scene.shootDateStart, scene.shootDateEnd)
            return (
              <li key={scene.id}>
                <Link
                  to={`/scenes/${scene.id}`}
                  className="flex flex-wrap items-center justify-between gap-3 bg-stone-900/60 px-5 py-4 hover:bg-stone-900 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-amber-400"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{sceneLabel(scene)}</span>
                    <span className="block text-sm text-stone-400">
                      {scene.requirements?.settingType ?? shootWindow ?? 'Not scheduled'}
                      {scene.requirements && shootWindow && ` · ${shootWindow}`}
                    </span>
                  </span>
                  <ParseStatusBadge status={scene.parseStatus} />
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
