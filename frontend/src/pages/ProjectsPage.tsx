import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Page, Project, ProjectStatus } from '../api/types'
import { useSession } from '../auth/context'
import { ArrowUpRight, BookMarked, Clapperboard, Film, Mail, MapPin, Plus } from 'lucide-react'
import { linkButton } from '../components/buttonStyles'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { Card, EmptyState } from '../components/surfaces'
import { AvatarStack } from '../components/Avatar'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { posterBackdrop } from '../lib/poster'
import { sampleProject, sampleScript } from '../lib/sampleScript'
import { blankToNull } from '../lib/text'
import { ProjectForm, type ProjectFormValues } from './ProjectForm'
import { usePageTitle } from '../lib/usePageTitle'
import { VenuePicture } from '../components/VenuePicture'

export function ProjectsPage() {
  const { api, user } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const status: ProjectStatus = params.get('status') === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE'
  // `?new` opens the form at once: the command palette's "New production" lands here.
  const [creating, setCreating] = useState(() => params.has('new'))
  usePageTitle('Projects')

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
    <div className="space-y-10">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-2">
          <p className="text-sm font-medium text-muted">
            {greeting()}, {user.displayName.split(' ')[0]}
          </p>
          <h1 className="font-display text-4xl leading-tight font-bold sm:text-5xl">Your productions</h1>
          <p className="text-[17px] text-muted">Every production, with its scenes, its venues and its letters to their owners.</p>
        </div>
        {!creating && (
          <div className="flex flex-wrap items-center gap-3">
            <Link to="/library" className={linkButton('ghost')}>
              <BookMarked aria-hidden className="size-4" />
              My locations
            </Link>
            {(projects.data?.items.length ?? 0) > 0 && <SampleProjectButton />}
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden className="size-4" />
              New project
            </Button>
          </div>
        )}
      </header>

      {creating && (
        <Card className="p-6">
          <section aria-labelledby="new-project">
            <h2 id="new-project" className="mb-4 font-display text-2xl leading-tight font-semibold">
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

      <div role="tablist" aria-label="Project status" className="inline-flex gap-1 rounded-[10px] bg-line-soft p-1">
        {(['ACTIVE', 'ARCHIVED'] as const).map((s) => (
          <button
            key={s}
            type="button"
            role="tab"
            aria-selected={status === s}
            onClick={() => setParams(s === 'ACTIVE' ? {} : { status: s })}
            className={`rounded-[8px] px-4 py-1.5 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink ${
              status === s ? 'bg-paper text-ink shadow-[var(--shadow-card)]' : 'text-muted hover:text-ink'
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
        status === 'ACTIVE' ? (
          <FirstReel onStart={() => setCreating(true)} creating={creating} />
        ) : (
          <EmptyState icon={Film}>No archived projects.</EmptyState>
        )
      ) : (
        <>
          {/* Each production is a poster: a still of one of its venues, or a lit backdrop, with its title on it. */}
          <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {projects.data.items.map((project) => (
              <li key={project.id}>
                <ProjectCard project={project} />
              </li>
            ))}
          </ul>
          <Pager data={projects.data} onChange={setPage} label="Project pages" />
        </>
      )}
    </div>
  )
}

/** Good morning, afternoon or evening, by the user's clock. */
function greeting(): string {
  const hour = new Date().getHours()
  return hour < 12 ? 'Morning' : hour < 18 ? 'Afternoon' : 'Evening'
}

function ProjectCard({ project }: { project: Project }) {
  return (
    <Link
      to={`/projects/${project.id}`}
      className="group flex h-full flex-col overflow-hidden rounded-2xl border border-line bg-paper shadow-[var(--shadow-card)] transition duration-300 hover:-translate-y-1 hover:shadow-[var(--shadow-lift)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ink"
    >
      <div className="hero flex aspect-[16/10] flex-col justify-between p-4" style={project.posterImageUrl ? undefined : { background: posterBackdrop(project.title) }}>
        {project.posterImageUrl && (
          // A still of one of its locations, slowly pushing in under the pointer.
          <div className="hero-picture [animation:none] transition-transform duration-[2500ms] ease-out group-hover:scale-110">
            <VenuePicture src={project.posterImageUrl} className="h-full w-full" />
          </div>
        )}
        <div className="flex items-start justify-between gap-2">
          <span className="inline-flex max-w-[70%] items-center gap-1.5 truncate rounded-full bg-white/15 px-2.5 py-1 text-xs font-medium text-white ring-1 ring-white/20 backdrop-blur">
            <MapPin aria-hidden className="size-3.5 shrink-0" />
            <span className="truncate">{project.locationArea ?? 'No area yet'}</span>
          </span>
          {project.followUpCount > 0 && (
            // Emails gone unanswered too long.
            <span className="inline-flex items-center gap-1 rounded-full bg-cue px-2.5 py-1 text-xs font-semibold text-night">
              <Mail aria-hidden className="size-3.5" />
              {project.followUpCount === 1 ? '1 to follow up' : `${project.followUpCount} to follow up`}
            </span>
          )}
        </div>
        <div className="flex items-end justify-between gap-3">
          <h2 className="font-display text-[1.7rem] leading-[1.05] font-bold break-words text-white">{project.title}</h2>
          <ArrowUpRight aria-hidden className="size-5 shrink-0 text-white/70 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-white" />
        </div>
      </div>
      <div className="flex flex-1 flex-col gap-4 p-5">
        {project.description ? (
          <p className="line-clamp-2 text-[15px] leading-relaxed text-graphite">{project.description}</p>
        ) : (
          <p className="text-[15px] text-muted">No logline yet.</p>
        )}
        <div className="mt-auto flex items-end justify-between gap-4">
          <div className="min-w-0 flex-1">
            <PosterProgress project={project} />
          </div>
          {(project.crew ?? []).length > 0 && <AvatarStack names={project.crew ?? []} max={3} />}
        </div>
      </div>
    </Link>
  )
}

/** The foot of a card: how many scenes, and how many of them have their location locked, a cell per scene. */
function PosterProgress({ project }: { project: Project }) {
  const { sceneCount, confirmedSceneCount } = project
  if (sceneCount === 0) return <p className="text-sm text-muted">No scenes yet</p>
  // A cell per scene reads well up to a reel's worth; past that, a bar.
  const cells = sceneCount <= 16
  return (
    <div className="space-y-2">
      <p className="text-sm font-semibold">
        {sceneCount === 1 ? '1 scene' : `${sceneCount} scenes`} <span className="font-normal text-muted">· {confirmedSceneCount} locked</span>
      </p>
      <div
        role="progressbar"
        aria-label="Scenes with a confirmed location"
        aria-valuemin={0}
        aria-valuemax={sceneCount}
        aria-valuenow={confirmedSceneCount}
        className={cells ? 'flex gap-1' : 'h-1.5 overflow-hidden rounded-full bg-line-soft'}
      >
        {cells ? (
          Array.from({ length: sceneCount }, (_, i) => (
            <span key={i} className={`h-1.5 flex-1 rounded-full ${i < confirmedSceneCount ? 'bg-go-mid' : 'bg-line-soft'}`} />
          ))
        ) : (
          <div className="h-full bg-go-mid" style={{ width: `${(confirmedSceneCount / sceneCount) * 100}%` }} />
        )}
      </div>
    </div>
  )
}

const acts = [
  { act: 'Act one', title: 'Bring the script', text: 'Create a production, then paste or import its screenplay. It is cut into scenes at the INT. and EXT. headings.' },
  { act: 'Act two', title: 'Find the places', text: 'The AI reads each scene for the location it needs, then scouts real venues in your area and rates how well each fits.' },
  { act: 'Act three', title: 'Lock them in', text: 'Compare the shortlist, check the light and the weather, write to the owners, and print the call sheet.' },
]

/** The empty lobby: what CineScout does, in three acts, and the way in. */
/** Sets up a short sample production, its script cut into scenes, and opens it: the whole flow, no typing. */
function SampleProjectButton() {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const sample = useMutation({
    mutationFn: async () => {
      const project = await api.projects.create(sampleProject)
      await api.scenes.importScript(project.id, sampleScript)
      return project
    },
    onSuccess: (project) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.projects })
      navigate(`/projects/${project.id}`)
    },
  })
  return (
    <>
      <Button variant="secondary" busy={sample.isPending} onClick={() => sample.mutate()}>
        {!sample.isPending && <Clapperboard aria-hidden className="size-4" />}
        Try a sample script
      </Button>
      {sample.isError && (
        <div className="basis-full">
          <ErrorAlert error={sample.error} />
        </div>
      )}
    </>
  )
}

function FirstReel({ onStart, creating }: { onStart: () => void; creating: boolean }) {
  return (
    <section aria-labelledby="first-reel" className="hero rounded-2xl px-6 py-14 text-center sm:px-12" style={{ background: posterBackdrop('first reel') }}>
      <div className="relative space-y-3">
        <p className="text-xs font-semibold tracking-[0.14em] text-cue uppercase">No projects yet</p>
        <h2 id="first-reel" className="font-display text-4xl leading-tight font-bold sm:text-5xl">
          Your first production
        </h2>
        <p className="mx-auto max-w-xl text-lg text-fog">From the page to the right location, in three acts.</p>
      </div>
      <ol className="relative mt-10 grid gap-6 text-left sm:grid-cols-3">
        {acts.map(({ act, title, text }, i) => (
          <li key={act} className="space-y-2 rounded-xl bg-white/10 p-5 ring-1 ring-white/15 backdrop-blur">
            <p className="text-xs font-semibold tracking-[0.12em] text-cue uppercase">
              {String(i + 1).padStart(2, '0')} · {act}
            </p>
            <h3 className="font-display text-xl leading-tight font-semibold text-white">{title}</h3>
            <p className="text-sm leading-relaxed text-fog">{text}</p>
          </li>
        ))}
      </ol>
      {!creating && (
        <div className="relative mt-10 flex flex-wrap items-center justify-center gap-3">
          <Button onClick={onStart} className="!bg-paper !text-ink hover:!bg-fog">
            <Plus aria-hidden className="size-4" />
            Create your first project
          </Button>
          <SampleProjectButton />
        </div>
      )}
    </section>
  )
}
