import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Page, Project, ProjectStatus } from '../api/types'
import { useSession } from '../auth/context'
import { Clapperboard, Film, MapPin, Plus } from 'lucide-react'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
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

  const [page, setPage] = usePageParam()

  const projects = useQuery({
    queryKey: queryKeys.projectPage(status, page),
    queryFn: () => api.projects.list(status, page),
    placeholderData: previousPageOf<Page<Project>>(['projects', 'list', status]),
  })
  useStayInRange(projects.data, setPage)

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
          <h1 className="gold-leaf font-display text-7xl leading-none">Projects</h1>
          <p className="font-serif text-lg text-stone-400 italic">Every production, with its scenes, its venues and its letters to their owners.</p>
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
          <h2 id="new-project" className="gold-leaf mb-4 font-display text-4xl leading-none">
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

      <div role="tablist" aria-label="Project status" className="flex gap-1 border-b border-amber-300/15">
        {(['ACTIVE', 'ARCHIVED'] as const).map((s) => (
          <button
            key={s}
            type="button"
            role="tab"
            aria-selected={status === s}
            onClick={() => setParams(s === 'ACTIVE' ? {} : { status: s })}
            className={`-mb-px border-b-2 px-4 py-2.5 font-display text-xl leading-none tracking-wider transition ${
              status === s ? 'border-amber-300 text-amber-100 [text-shadow:0_0_18px_rgb(236_208_120/0.55)]' : 'border-transparent text-stone-500 hover:text-stone-200'
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
      ) : projects.data.items.length === 0 ? (
        <EmptyState icon={Film}>{status === 'ACTIVE' ? 'No projects yet. Create one to start scouting.' : 'No archived projects.'}</EmptyState>
      ) : (
        <>
          {/* One-sheets in a cinema lobby: each production is its own poster. */}
          <ul className="grid gap-7 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {projects.data.items.map((project) => (
              <li key={project.id}>
                <Link
                  to={`/projects/${project.id}`}
                  className="group relative flex aspect-[2/3] flex-col overflow-hidden rounded-md bg-black shadow-2xl shadow-black/70 ring-1 ring-amber-300/20 transition duration-300 hover:-translate-y-1.5 hover:shadow-[0_24px_60px_-18px_rgb(223_184_73/0.45)] hover:ring-amber-300/60 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-amber-300"
                >
                  <div aria-hidden className="absolute inset-0 transition duration-500 group-hover:scale-105" style={{ background: posterGradient(project.title) }} />
                  <div aria-hidden className="absolute inset-0 bg-[radial-gradient(ellipse_at_50%_18%,rgb(255_243_196/0.22),transparent_55%)]" />
                  <div aria-hidden className="absolute inset-0 bg-gradient-to-t from-black via-black/55 to-transparent" />
                  <div aria-hidden className="absolute inset-2 rounded-sm ring-1 ring-white/10" />
                  <p aria-hidden className="relative mt-5 text-center text-[10px] font-semibold tracking-[0.4em] text-amber-100/70 uppercase">
                    A CineScout production
                  </p>
                  <div className="relative mt-auto space-y-3 p-5 text-center">
                    <h2 className="font-display text-5xl leading-[0.92] break-words text-stone-50 drop-shadow-[0_2px_12px_rgb(0_0_0/0.8)] transition group-hover:text-amber-200">
                      {project.title}
                    </h2>
                    {project.description && <p className="line-clamp-2 font-serif text-sm text-stone-200/90 italic">{project.description}</p>}
                    <div aria-hidden className="deco-rule text-[9px]">◆</div>
                    <p className="flex items-center justify-center gap-1.5 text-xs tracking-wider text-stone-300 uppercase">
                      <MapPin aria-hidden className="size-3.5 text-amber-300" />
                      {project.locationArea ?? 'No location area set'}
                    </p>
                    <PosterProgress project={project} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <Pager data={projects.data} onChange={setPage} label="Project pages" />
        </>
      )}
    </div>
  )
}

/** The foot of a poster: how many scenes, and how many of them have their location locked. */
function PosterProgress({ project }: { project: Project }) {
  const { sceneCount, confirmedSceneCount } = project
  if (sceneCount === 0) return <p className="billing text-[10px] text-stone-400">No scenes yet</p>
  return (
    <div className="space-y-1.5">
      <div
        role="progressbar"
        aria-label="Scenes with a confirmed location"
        aria-valuemin={0}
        aria-valuemax={sceneCount}
        aria-valuenow={confirmedSceneCount}
        className="h-1 overflow-hidden rounded-full bg-white/15"
      >
        <div className="h-full rounded-full bg-gradient-to-r from-amber-500 to-amber-200" style={{ width: `${(confirmedSceneCount / sceneCount) * 100}%` }} />
      </div>
      <p className="billing text-[10px] text-stone-300">
        {sceneCount === 1 ? '1 scene' : `${sceneCount} scenes`} · {confirmedSceneCount} locked
      </p>
    </div>
  )
}
