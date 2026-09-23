import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { SceneRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ErrorAlert, Spinner } from '../components/ui'
import { NotFoundPage } from './NotFoundPage'
import { SceneForm } from './SceneForm'

export function NewScenePage() {
  const { projectId = '' } = useParams()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const project = useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => api.projects.get(projectId) })

  const create = useMutation({
    mutationFn: (body: SceneRequest) => api.scenes.create(projectId, body),
    onSuccess: (scene) => {
      queryClient.setQueryData(queryKeys.scene(scene.id), scene)
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(projectId) })
      navigate(`/scenes/${scene.id}`, { replace: true })
    },
  })

  if (project.isPending) return <Spinner label="Loading project" />
  if (project.isError) {
    if (isNotFound(project.error)) return <NotFoundPage />
    return <ErrorAlert error={project.error} onRetry={() => project.refetch()} />
  }

  return (
    <div className="space-y-6">
      <Link to={`/projects/${projectId}`} className="text-sm text-stone-400 hover:text-stone-200">
        ← {project.data.title}
      </Link>
      <section aria-labelledby="new-scene" className="rounded-lg border border-stone-800 bg-stone-900/60 p-6">
        <h1 id="new-scene" className="mb-4 text-xl font-semibold">
          New scene
        </h1>
        <SceneForm
          submitLabel="Add scene"
          busy={create.isPending}
          error={create.error}
          onSubmit={(body) => create.mutate(body)}
          onCancel={() => navigate(`/projects/${projectId}`)}
        />
      </section>
    </div>
  )
}
