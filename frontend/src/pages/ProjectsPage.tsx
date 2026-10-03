import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Page, Project, ProjectStatus } from '../api/types'
import { useSession } from '../auth/context'
import { BookMarked, Clapperboard, Film, MapPin, Plus } from 'lucide-react'
import { linkButton } from '../components/buttonStyles'
import { Pager } from '../components/Pager'
import { previousPageOf, usePageParam, useStayInRange } from '../components/paging'
import { Card, EmptyState } from '../components/surfaces'
import { Stamp, TapeLabel } from '../components/stickers'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { posterColours, posterTilt } from '../lib/poster'
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
  const [creating, setCreating] = useState(false)
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
        <div className="space-y-3">
          <TapeLabel tilt={-2}>{greeting()}, {user.displayName.split(' ')[0]}</TapeLabel>
          <h1 className="font-display text-5xl leading-none font-extrabold sm:text-[3.5rem]">Your productions</h1>
          <p className="text-lg text-muted">Every production, with its scenes, its venues and its letters to their owners.</p>
        </div>
        {!creating && (
          <div className="flex flex-wrap items-center gap-3">
            <Link to="/library" className={linkButton('ghost')}>
              <BookMarked aria-hidden className="size-4" />
              My locations
            </Link>
            {(projects.data?.items.length ?? 0) > 0 && <SampleProjectButton />}
            <Button onClick={() => setCreating(true)} className="py-3">
              <Plus aria-hidden className="size-4" />
              New project
            </Button>
          </div>
        )}
      </header>

      {creating && (
        <Card className="p-6">
          <section aria-labelledby="new-project">
            <h2 id="new-project" className="mb-4 font-display text-3xl leading-none font-extrabold">
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

      <div role="tablist" aria-label="Project status" className="inline-flex gap-1 rounded-full border-2 border-ink bg-white p-1">
        {(['ACTIVE', 'ARCHIVED'] as const).map((s) => (
          <button
            key={s}
            type="button"
            role="tab"
            aria-selected={status === s}
            onClick={() => setParams(s === 'ACTIVE' ? {} : { status: s })}
            className={`rounded-full px-4 py-1.5 text-sm font-bold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink ${
              status === s ? 'bg-ink text-white' : 'text-graphite hover:bg-ground'
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
          {/* Each production is a card pinned to the board, its name on a strip of tape. */}
          <ul className="grid gap-x-7 gap-y-10 pt-3 sm:grid-cols-2 lg:grid-cols-3">
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
  const colours = posterColours(project.title)
  return (
    <Link
      to={`/projects/${project.id}`}
      style={{ transform: `rotate(${posterTilt(project.title)}deg)` }}
      className="group board-card relative flex h-full flex-col rounded-lg bg-white transition duration-200 hover:!rotate-0 hover:-translate-y-1 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ink"
    >
      <TapeLabel tilt={-3} announce className="absolute -top-4 left-5 z-10 max-w-[75%] overflow-hidden text-ellipsis">
        {project.locationArea ?? 'No area yet'}
      </TapeLabel>
      <div className={`relative flex h-44 flex-col justify-end overflow-hidden rounded-t-md px-5 pt-8 pb-4 pl-9 ${colours.panel} ${colours.text}`}>
        {project.posterImageUrl && (
          // A still of one of its locations, printed in the card's colour.
          <VenuePicture
            src={project.posterImageUrl}
            className="absolute inset-0 h-full w-full opacity-35 mix-blend-luminosity grayscale transition duration-500 group-hover:opacity-50"
          />
        )}
        <div aria-hidden className="absolute inset-y-0 left-2 flex w-3 flex-col justify-around opacity-50">
          {Array.from({ length: 7 }, (_, i) => (
            <span key={i} className={`h-2.5 rounded-[2px] ${colours.holes}`} />
          ))}
        </div>
        <h2 className="relative font-display text-[2.1rem] leading-[0.95] font-extrabold break-words">{project.title}</h2>
        {project.followUpCount > 0 && (
          // Emails gone unanswered too long: stamped across the poster's corner.
          <Stamp tone="stop" announce className="absolute top-3 right-3 rotate-6 text-xs">
            {project.followUpCount === 1 ? '1 to follow up' : `${project.followUpCount} to follow up`}
          </Stamp>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-4 p-5">
        {project.description ? (
          <p className="line-clamp-2 text-[15px] leading-relaxed text-graphite">{project.description}</p>
        ) : (
          <p className="flex items-center gap-1.5 text-[15px] text-muted">
            <MapPin aria-hidden className="size-4" />
            {project.locationArea ?? 'No location area set'}
          </p>
        )}
        <div className="mt-auto">
          <PosterProgress project={project} />
        </div>
      </div>
    </Link>
  )
}

/** The foot of a card: how many scenes, and how many of them have their location locked, a cell per scene. */
function PosterProgress({ project }: { project: Project }) {
  const { sceneCount, confirmedSceneCount } = project
  if (sceneCount === 0) return <p className="font-script text-sm text-muted">No scenes yet</p>
  // A cell per scene reads well up to a reel's worth; past that, a bar.
  const cells = sceneCount <= 16
  return (
    <div className="space-y-2">
      <p className="text-sm font-bold">
        {sceneCount === 1 ? '1 scene' : `${sceneCount} scenes`} · {confirmedSceneCount} locked
      </p>
      <div
        role="progressbar"
        aria-label="Scenes with a confirmed location"
        aria-valuemin={0}
        aria-valuemax={sceneCount}
        aria-valuenow={confirmedSceneCount}
        className={cells ? 'flex gap-1' : 'h-2.5 overflow-hidden rounded-[3px] bg-line-soft'}
      >
        {cells ? (
          Array.from({ length: sceneCount }, (_, i) => (
            <span key={i} className={`h-2.5 flex-1 rounded-[2px] ${i < confirmedSceneCount ? 'bg-go-mid' : 'bg-line-soft'}`} />
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
    <section aria-labelledby="first-reel" className="relative rounded-lg border-2 border-dashed border-line bg-white/60 px-6 py-12 text-center sm:px-12">
      <div className="relative space-y-3">
        <TapeLabel tilt={-2}>No projects yet</TapeLabel>
        <h2 id="first-reel" className="font-display text-5xl leading-none font-extrabold">
          Your first production
        </h2>
        <p className="mx-auto max-w-xl text-lg text-muted">From the page to the right location, in three acts.</p>
      </div>
      <ol className="relative mt-10 grid gap-6 text-left sm:grid-cols-3">
        {acts.map(({ act, title, text }, i) => (
          <li key={act} className="board-card space-y-2 rounded-lg bg-white p-5" style={{ transform: `rotate(${[-0.8, 0.5, -0.3][i]}deg)` }}>
            <p className="font-script text-[13px] font-bold tracking-[0.1em] text-cue-ink uppercase">{act}</p>
            <h3 className="font-display text-2xl leading-none font-extrabold">{title}</h3>
            <p className="text-sm leading-relaxed text-graphite">{text}</p>
          </li>
        ))}
      </ol>
      {!creating && (
        <div className="relative mt-10 flex flex-wrap items-center justify-center gap-3">
          <Button onClick={onStart} className="py-3">
            <Plus aria-hidden className="size-4" />
            Create your first project
          </Button>
          <SampleProjectButton />
        </div>
      )}
    </section>
  )
}
