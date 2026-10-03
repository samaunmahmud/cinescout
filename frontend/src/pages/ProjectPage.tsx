import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Project, UpdateProjectRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { CalendarDays, ChevronLeft, Clapperboard, Film, Mail, MapPin, MapPinned, Users } from 'lucide-react'
import { linkButton } from '../components/buttonStyles'
import { ProjectRoleProvider } from '../components/ProjectRoleProvider'
import { Card, Eyebrow, Tabs, type TabItem } from '../components/surfaces'
import { Stamp } from '../components/stickers'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { blankToNull } from '../lib/text'
import { ProjectForm, type ProjectFormValues } from './ProjectForm'
import { NotFoundPage } from './NotFoundPage'
import { ProjectLocationsSection } from './ProjectLocationsSection'
import { ProjectOutreachSection } from './ProjectOutreachSection'
import { ScenesSection } from './ScenesSection'
import { ScheduleSection } from './ScheduleSection'
import { usePageTitle } from '../lib/usePageTitle'

export function ProjectPage() {
  const { projectId = '' } = useParams()
  const { api } = useSession()
  const project = useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => api.projects.get(projectId) })
  usePageTitle(project.data?.title)

  if (project.isPending) return <Spinner label="Loading project" />
  if (project.isError) {
    if (isNotFound(project.error)) return <NotFoundPage />
    return <ErrorAlert error={project.error} onRetry={() => project.refetch()} />
  }
  return <ProjectDetails project={project.data} />
}

type TabKey = 'scenes' | 'locations' | 'outreach' | 'schedule'

const tabs: TabItem<TabKey>[] = [
  { key: 'scenes', label: 'Scenes', icon: Film },
  { key: 'locations', label: 'Locations', icon: MapPinned },
  { key: 'outreach', label: 'Outreach', icon: Mail },
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

  const owner = project.role === 'OWNER'
  const editor = project.role !== 'VIEWER'

  return (
    <ProjectRoleProvider role={project.role}>
    <div className="space-y-8">
      <Link to="/projects" className="inline-flex items-center gap-1 font-script text-sm font-bold tracking-[0.06em] text-muted uppercase hover:text-ink">
        <ChevronLeft aria-hidden className="size-4" />
        Productions
      </Link>

      {editing ? (
        <Card className="p-6">
          <section aria-labelledby="edit-project">
            <h1 id="edit-project" className="mb-4 font-display text-4xl leading-none font-extrabold">
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
        </Card>
      ) : (
        // The title card: the production's name on an ink panel, the film strip down its edge.
        <header className="relative animate-fade-in overflow-hidden rounded-lg border-2 border-ink bg-ink text-white shadow-[0_5px_0_var(--color-cue)]">
          <div aria-hidden className="absolute inset-y-0 left-2.5 flex w-3 flex-col justify-around opacity-40">
            {Array.from({ length: 9 }, (_, i) => (
              <span key={i} className="h-2.5 rounded-[2px] bg-white" />
            ))}
          </div>
          <div className="space-y-5 py-7 pr-6 pl-11 sm:py-9 sm:pr-10 sm:pl-14">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="space-y-3">
                <Eyebrow onDark icon={Clapperboard}>A CineScout production</Eyebrow>
                <div className="flex flex-wrap items-center gap-3">
                  <h1 className="font-display text-5xl leading-[0.95] font-extrabold sm:text-7xl">{project.title}</h1>
                  {archived && <Stamp tone="ink" announce className="!bg-white text-sm">Archived</Stamp>}
                </div>
                <p className="flex items-center gap-1.5 font-script text-fog">
                  <MapPin aria-hidden className="size-4 text-cue" />
                  {project.locationArea ?? 'No location area set. Scouting needs one.'}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <Link to={`/projects/${project.id}/settings`} className={linkButton('secondary')}>
                  <Users aria-hidden className="size-4" />
                  Crew
                </Link>
                {editor && (
                  <Button variant="secondary" onClick={() => setEditing(true)}>
                    Edit
                  </Button>
                )}
                {owner && (
                  <>
                    <Button variant="secondary" onClick={toggleArchived} busy={update.isPending}>
                      {archived ? 'Restore' : 'Archive'}
                    </Button>
                    <Button variant="ghost" className="!text-fog hover:!bg-ink-soft hover:!text-white" onClick={() => setConfirmingDelete(true)}>
                      Delete
                    </Button>
                  </>
                )}
              </div>
            </div>
            {project.description && <p className="max-w-prose text-lg whitespace-pre-line text-fog">{project.description}</p>}
            <ErrorAlert error={update.error} />
          </div>
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
          {tab === 'outreach' && <ProjectOutreachSection projectId={project.id} />}
          {tab === 'schedule' && <ScheduleSection projectId={project.id} />}
        </Tabs>
      )}
    </div>
    </ProjectRoleProvider>
  )
}
