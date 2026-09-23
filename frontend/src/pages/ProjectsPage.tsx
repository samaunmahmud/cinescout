import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { ProjectStatus } from '../api/types'
import { useSession } from '../auth/context'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { blankToNull } from '../lib/text'
import { ProjectForm, type ProjectFormValues } from './ProjectForm'

export function ProjectsPage() {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const status: ProjectStatus = params.get('status') === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE'
  const [creating, setCreating] = useState(false)

  const projects = useQuery({ queryKey: queryKeys.projectList(status), queryFn: () => api.projects.list(status) })

  const create = useMutation({
    mutationFn: (values: ProjectFormValues) =>
      api.projects.create({
        title: values.title.trim(),
        description: blankToNull(values.description),
        locationArea: blankToNull(values.locationArea),
      }),
    onSuccess: (project) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.projects })
      queryClient.setQueryData(queryKeys.project(project.id), project)
      navigate(`/projects/${project.id}`)
    },
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">Projects</h1>
        {!creating && <Button onClick={() => setCreating(true)}>New project</Button>}
      </div>

      {creating && (
        <section aria-labelledby="new-project" className="rounded-lg border border-stone-800 bg-stone-900/60 p-6">
          <h2 id="new-project" className="mb-4 text-lg font-semibold">
            New project
          </h2>
          <ProjectForm
            submitLabel="Create project"
            busy={create.isPending}
            error={create.error}
            onSubmit={(values) => create.mutate(values)}
            onCancel={() => {
              setCreating(false)
              create.reset()
            }}
          />
        </section>
      )}

      <div role="tablist" aria-label="Project status" className="flex gap-1 border-b border-stone-800">
        {(['ACTIVE', 'ARCHIVED'] as const).map((s) => (
          <button
            key={s}
            type="button"
            role="tab"
            aria-selected={status === s}
            onClick={() => setParams(s === 'ACTIVE' ? {} : { status: s })}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
              status === s ? 'border-amber-400 text-stone-100' : 'border-transparent text-stone-400 hover:text-stone-200'
            }`}
          >
            {s === 'ACTIVE' ? 'Active' : 'Archived'}
          </button>
        ))}
      </div>

      {projects.isPending ? (
        <Spinner label="Loading projects" />
      ) : projects.isError ? (
        <ErrorAlert error={projects.error} onRetry={() => projects.refetch()} />
      ) : projects.data.length === 0 ? (
        <p className="rounded-lg border border-dashed border-stone-800 px-6 py-12 text-center text-stone-400">
          {status === 'ACTIVE' ? 'No projects yet. Create one to start scouting.' : 'No archived projects.'}
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {projects.data.map((project) => (
            <li key={project.id}>
              <Link
                to={`/projects/${project.id}`}
                className="block h-full rounded-lg border border-stone-800 bg-stone-900/60 p-5 transition-colors hover:border-stone-600 focus-visible:outline-2 focus-visible:outline-amber-400"
              >
                <h2 className="font-semibold">{project.title}</h2>
                <p className="mt-1 text-sm text-stone-400">{project.locationArea ?? 'No location area set'}</p>
                {project.description && <p className="mt-3 line-clamp-2 text-sm text-stone-300">{project.description}</p>}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
