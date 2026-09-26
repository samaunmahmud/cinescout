import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { ProjectStatus } from '../api/types'
import { useSession } from '../auth/context'
import { Clapperboard, Film, MapPin, Plus } from 'lucide-react'
import { Card, EmptyState, Eyebrow } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { posterGradient } from '../lib/poster'
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
    <div className="space-y-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-2">
          <Eyebrow icon={Clapperboard}>Your slate</Eyebrow>
          <h1 className="font-display text-6xl leading-none text-stone-50">Projects</h1>
          <p className="text-stone-400">Each project is a production: its scenes, the venues scouted for them, and the outreach.</p>
        </div>
        {!creating && (
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden className="size-4" />
            New project
          </Button>
        )}
      </header>

      {creating && (
        <Card className="p-6">
        <section aria-labelledby="new-project">
          <h2 id="new-project" className="mb-4 font-display text-3xl leading-none">
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
        </Card>
      )}

      <div role="tablist" aria-label="Project status" className="flex gap-1 border-b border-white/[0.07]">
        {(['ACTIVE', 'ARCHIVED'] as const).map((s) => (
          <button
            key={s}
            type="button"
            role="tab"
            aria-selected={status === s}
            onClick={() => setParams(s === 'ACTIVE' ? {} : { status: s })}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
              status === s ? 'border-amber-400 text-stone-50' : 'border-transparent text-stone-400 hover:text-stone-200'
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
        <EmptyState icon={Film}>{status === 'ACTIVE' ? 'No projects yet. Create one to start scouting.' : 'No archived projects.'}</EmptyState>
      ) : (
        <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {projects.data.map((project) => (
            <li key={project.id}>
              <Link
                to={`/projects/${project.id}`}
                className="group block h-full overflow-hidden rounded-xl border border-white/[0.07] bg-reel shadow-lg shadow-black/40 transition hover:-translate-y-0.5 hover:border-amber-400/40 hover:shadow-amber-950/40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-400"
              >
                <div aria-hidden className="relative h-28 overflow-hidden" style={{ background: posterGradient(project.title) }}>
                  <div className="absolute inset-0 bg-gradient-to-t from-reel via-reel/20 to-transparent" />
                  <Film className="absolute top-4 right-4 size-5 text-white/40" />
                </div>
                <div className="-mt-8 space-y-2 p-5 pt-0">
                  <h2 className="relative font-display text-3xl leading-none text-stone-50 transition group-hover:text-amber-300">{project.title}</h2>
                  <p className="flex items-center gap-1.5 text-sm text-stone-400">
                    <MapPin aria-hidden className="size-3.5 text-amber-400/80" />
                    {project.locationArea ?? 'No location area set'}
                  </p>
                  {project.description && <p className="line-clamp-2 text-sm text-stone-300">{project.description}</p>}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
