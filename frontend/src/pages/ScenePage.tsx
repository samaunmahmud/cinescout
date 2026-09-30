import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Scene, SceneRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { ParseStatusBadge } from '../components/ParseStatusBadge'
import { CalendarDays, ChevronLeft, Clapperboard, ScrollText } from 'lucide-react'
import { Eyebrow, Slate } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { formatShootWindow, sceneLabel } from '../lib/format'
import { LocationsSection } from './LocationsSection'
import { NotFoundPage } from './NotFoundPage'
import { RequirementsPanel } from './RequirementsPanel'
import { SceneForm } from './SceneForm'
import { usePageTitle } from '../lib/usePageTitle'

export function ScenePage() {
  const { sceneId = '' } = useParams()
  const { api } = useSession()
  const scene = useQuery({ queryKey: queryKeys.scene(sceneId), queryFn: () => api.scenes.get(sceneId) })
  usePageTitle(scene.data && sceneLabel(scene.data))

  if (scene.isPending) return <Spinner label="Loading scene" />
  if (scene.isError) {
    if (isNotFound(scene.error)) return <NotFoundPage />
    return <ErrorAlert error={scene.error} onRetry={() => scene.refetch()} />
  }
  return <SceneDetails scene={scene.data} />
}

function SceneDetails({ scene }: { scene: Scene }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  // For the breadcrumb and the scouting area; the page works without it.
  const project = useQuery({ queryKey: queryKeys.project(scene.projectId), queryFn: () => api.projects.get(scene.projectId) })
  const projectPath = `/projects/${scene.projectId}`

  const update = useMutation({
    mutationFn: (body: SceneRequest) => api.scenes.update(scene.id, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.scene(scene.id), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(scene.projectId) })
      setEditing(false)
    },
  })

  const remove = useMutation({
    mutationFn: () => api.scenes.remove(scene.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.scene(scene.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(scene.projectId) })
      navigate(projectPath, { replace: true })
    },
  })

  const shootWindow = formatShootWindow(scene.shootDateStart, scene.shootDateEnd)

  return (
    <div className="space-y-8">
      <Link to={projectPath} className="inline-flex items-center gap-1 text-sm text-stone-400 hover:text-stone-200">
        <ChevronLeft aria-hidden className="size-4" />
        {project.data?.title ?? 'Project'}
      </Link>

      {editing ? (
        <section aria-labelledby="edit-scene" className="rounded-xl border border-white/[0.07] bg-frame/80 p-6">
          <h1 id="edit-scene" className="mb-4 gold-leaf font-display text-4xl leading-none">
            Edit scene
          </h1>
          <SceneForm
            initial={{
              sceneNumber: scene.sceneNumber?.toString() ?? '',
              title: scene.title,
              sourceText: scene.sourceText,
              shootDateStart: scene.shootDateStart ?? '',
              shootDateEnd: scene.shootDateEnd ?? '',
            }}
            submitLabel="Save changes"
            busy={update.isPending}
            error={update.error}
            warning={(values) =>
              scene.requirements && values.sourceText !== scene.sourceText
                ? 'Changing the script discards the requirements extracted from it. Analyse the scene again after saving.'
                : null
            }
            onSubmit={(body) => update.mutate(body)}
            onCancel={() => {
              setEditing(false)
              update.reset()
            }}
          />
        </section>
      ) : (
        // The scene's slate, as it would be held up before the take.
        <header className="letterbox gilt relative flex animate-fade-in flex-wrap items-start justify-between gap-6 overflow-hidden rounded-xl bg-gradient-to-br from-frame to-black p-6 shadow-2xl shadow-black/70 ring-1 ring-amber-300/20 sm:p-8">
          <div aria-hidden className="pointer-events-none absolute -top-24 left-10 h-64 w-96 bg-[radial-gradient(ellipse_at_top,rgb(255_243_196/0.14),transparent_65%)]" />
          <div className="flex min-w-0 items-start gap-5">
            <Slate number={scene.sceneNumber} className="w-24 scale-110" />
            <div className="min-w-0 space-y-2">
              <Eyebrow icon={Clapperboard}>{project.data?.title ?? 'Scene'}</Eyebrow>
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="gold-leaf font-display text-5xl leading-none">{sceneLabel(scene)}</h1>
                <ParseStatusBadge status={scene.parseStatus} />
              </div>
              <p className="flex items-center gap-1.5 text-stone-400">
                <CalendarDays aria-hidden className="size-4 text-amber-400/80" />
                {shootWindow ?? 'No shoot dates yet'}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setEditing(true)}>
              Edit
            </Button>
            <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
              Delete
            </Button>
          </div>
        </header>
      )}

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

      {!editing && (
        <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="min-w-0 space-y-10">
            <RequirementsPanel scene={scene} />
            <LocationsSection scene={scene} locationArea={project.data?.locationArea} />
          </div>
          <section aria-labelledby="script-heading" className="space-y-3 lg:sticky lg:top-24">
            <Eyebrow icon={ScrollText}>Screenplay</Eyebrow>
            <h2 id="script-heading" className="gold-leaf font-display text-3xl leading-none">
              Script
            </h2>
            {/* A page of the script, as it would come off the printer. */}
            <pre className="max-h-[70vh] overflow-auto rounded-sm bg-paper px-6 py-7 font-script text-[13px] leading-relaxed whitespace-pre-wrap text-stone-900 shadow-2xl shadow-black/60 ring-1 ring-black/20">
              {scene.sourceText}
            </pre>
          </section>
        </div>
      )}
    </div>
  )
}
