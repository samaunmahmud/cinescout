import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Scene, SceneRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { ParseStatusBadge } from '../components/ParseStatusBadge'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { formatShootWindow, sceneLabel } from '../lib/format'
import { LocationsSection } from './LocationsSection'
import { NotFoundPage } from './NotFoundPage'
import { RequirementsPanel } from './RequirementsPanel'
import { SceneForm } from './SceneForm'

export function ScenePage() {
  const { sceneId = '' } = useParams()
  const { api } = useSession()
  const scene = useQuery({ queryKey: queryKeys.scene(sceneId), queryFn: () => api.scenes.get(sceneId) })

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
      <Link to={projectPath} className="text-sm text-stone-400 hover:text-stone-200">
        ← {project.data?.title ?? 'Project'}
      </Link>

      {editing ? (
        <section aria-labelledby="edit-scene" className="rounded-lg border border-stone-800 bg-stone-900/60 p-6">
          <h1 id="edit-scene" className="mb-4 text-xl font-semibold">
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
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold">{sceneLabel(scene)}</h1>
              <ParseStatusBadge status={scene.parseStatus} />
            </div>
            <p className="text-stone-400">{shootWindow ?? 'No shoot dates yet'}</p>
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
        <>
          <RequirementsPanel scene={scene} />
          <LocationsSection scene={scene} locationArea={project.data?.locationArea} />
          <section aria-labelledby="script-heading" className="space-y-3">
            <h2 id="script-heading" className="text-lg font-semibold">
              Script
            </h2>
            <pre className="max-h-[32rem] overflow-auto rounded-lg border border-stone-800 bg-stone-900/60 p-5 font-mono text-sm whitespace-pre-wrap text-stone-200">
              {scene.sourceText}
            </pre>
          </section>
        </>
      )}
    </div>
  )
}
