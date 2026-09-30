import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Project, UpdateProjectRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { CalendarDays, ChevronLeft, Clapperboard, Film, MapPin, MapPinned } from 'lucide-react'
import { Eyebrow, Tabs, type TabItem } from '../components/surfaces'
import { Badge, Button, ErrorAlert, Spinner } from '../components/ui'
import { posterGradient } from '../lib/poster'
import { blankToNull } from '../lib/text'
import { ProjectForm, type ProjectFormValues } from './ProjectForm'
import { NotFoundPage } from './NotFoundPage'
import { ProjectLocationsSection } from './ProjectLocationsSection'
import { ScenesSection } from './ScenesSection'
import { ScheduleSection } from './ScheduleSection'

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

type TabKey = 'scenes' | 'locations' | 'schedule'

const tabs: TabItem<TabKey>[] = [
  { key: 'scenes', label: 'Scenes', icon: Film },
  { key: 'locations', label: 'Locations', icon: MapPinned },
  { key: 'schedule', label: 'Schedule', icon: CalendarDays },
]

function ProjectDetails({ project }: { project: Project }) {
  const { api } = useSession()
  const [params, setParams] = useSearchParams()
  const tab = tabs.find((item) => item.key === params.get('tab'))?.key ?? 'scenes'
  // Each tab has its own list, so the page number and filter of the other do not carry over.
  const selectTab = (key: TabKey) => setParams(key === 'scenes' ? {} : { tab: key })
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
    <div className="space-y-8">
      <Link to="/projects" className="inline-flex items-center gap-1 text-sm text-stone-400 hover:text-stone-200">
        <ChevronLeft aria-hidden className="size-4" />
        Projects
      </Link>

      {editing ? (
        <section aria-labelledby="edit-project" className="rounded-xl border border-white/[0.07] bg-frame/80 p-6">
          <h1 id="edit-project" className="mb-4 font-display text-4xl leading-none">
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
        <header className="relative space-y-4 overflow-hidden rounded-2xl border border-white/[0.07] p-6 sm:p-8">
          <div aria-hidden className="absolute inset-0 -z-10 opacity-70" style={{ background: posterGradient(project.title) }} />
          <div aria-hidden className="absolute inset-0 -z-10 bg-gradient-to-r from-ink via-ink/85 to-ink/40" />
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-2">
              <Eyebrow icon={Clapperboard}>Production</Eyebrow>
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="font-display text-6xl leading-none text-stone-50">{project.title}</h1>
                {archived && <Badge>Archived</Badge>}
              </div>
              <p className="flex items-center gap-1.5 text-stone-300">
                <MapPin aria-hidden className="size-4 text-amber-400" />
                {project.locationArea ?? 'No location area set. Scouting needs one.'}
              </p>
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
          {project.description && <p className="max-w-prose whitespace-pre-line text-stone-300/90">{project.description}</p>}
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

      {!editing && (
        <Tabs label="Project sections" items={tabs} selected={tab} onSelect={selectTab}>
          {tab === 'scenes' && <ScenesSection projectId={project.id} />}
          {tab === 'locations' && <ProjectLocationsSection projectId={project.id} />}
          {tab === 'schedule' && <ScheduleSection projectId={project.id} />}
        </Tabs>
      )}
    </div>
  )
}
