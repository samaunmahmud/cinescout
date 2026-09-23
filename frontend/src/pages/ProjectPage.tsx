import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Project, UpdateProjectRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { Badge, Button, ErrorAlert, Spinner } from '../components/ui'
import { blankToNull } from '../lib/text'
import { ProjectForm, type ProjectFormValues } from './ProjectForm'
import { NotFoundPage } from './NotFoundPage'
import { ScenesSection } from './ScenesSection'

export function ProjectPage() {
  const { projectId = '' } = useParams()
  const { api } = useSession()
  const project = useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => api.projects.get(projectId) })

  if (project.isPending) return <Spinner label="Loading project" />
  if (project.isError) {
    if (isNotFound(project.error)) return <NotFoundPage />
    return <ErrorAlert error={project.error} onRetry={() => project.refetch()} />
  }
  return <ProjectDetails project={project.data} />
}

function ProjectDetails({ project }: { project: Project }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const update = useMutation({
    mutationFn: (body: UpdateProjectRequest) => api.projects.update(project.id, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.project(project.id), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.projects, refetchType: 'none' })
      setEditing(false)
    },
  })

  const remove = useMutation({
    mutationFn: () => api.projects.remove(project.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.project(project.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.projects })
      navigate('/projects', { replace: true })
    },
  })

  const archived = project.status === 'ARCHIVED'
  const toggleArchived = () =>
    update.mutate({
      title: project.title,
      description: project.description,
      locationArea: project.locationArea,
      status: archived ? 'ACTIVE' : 'ARCHIVED',
    })

  const save = (values: ProjectFormValues) =>
    update.mutate({
      title: values.title.trim(),
      description: blankToNull(values.description),
      locationArea: blankToNull(values.locationArea),
      status: project.status,
    })

  return (
    <div className="space-y-6">
      <Link to="/projects" className="text-sm text-stone-400 hover:text-stone-200">
        ← Projects
      </Link>

      {editing ? (
        <section aria-labelledby="edit-project" className="rounded-lg border border-stone-800 bg-stone-900/60 p-6">
          <h1 id="edit-project" className="mb-4 text-lg font-semibold">
            Edit project
          </h1>
          <ProjectForm
            initial={{ title: project.title, description: project.description ?? '', locationArea: project.locationArea ?? '' }}
            submitLabel="Save changes"
            busy={update.isPending}
            error={update.error}
            onSubmit={save}
            onCancel={() => {
              setEditing(false)
              update.reset()
            }}
          />
        </section>
      ) : (
        <header className="space-y-3">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-1">
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-2xl font-semibold">{project.title}</h1>
                {archived && <Badge>Archived</Badge>}
              </div>
              <p className="text-stone-400">{project.locationArea ?? 'No location area set. Scouting needs one.'}</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => setEditing(true)}>
                Edit
              </Button>
              <Button variant="secondary" onClick={toggleArchived} busy={update.isPending}>
                {archived ? 'Restore' : 'Archive'}
              </Button>
              <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
                Delete
              </Button>
            </div>
          </div>
          {project.description && <p className="max-w-prose whitespace-pre-line text-stone-300">{project.description}</p>}
          <ErrorAlert error={update.error} />
        </header>
      )}

      {confirmingDelete && (
        <ConfirmDelete
          title={`Delete “${project.title}”?`}
          confirmLabel="Delete project"
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirmingDelete(false)}
        >
          This also deletes its scenes, scouted locations and outreach drafts. It cannot be undone.
        </ConfirmDelete>
      )}

      {!editing && <ScenesSection projectId={project.id} />}
    </div>
  )
}
