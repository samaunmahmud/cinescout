import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Project, UpdateProjectRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { Activity as ActivityIcon, CalendarDays, ChevronLeft, Clapperboard, Film, Mail, MapPin, MapPinned, Users, Wallet } from 'lucide-react'
import { linkButton } from '../components/buttonStyles'
import { ProjectRoleProvider } from '../components/ProjectRoleProvider'
import { Card, Eyebrow, Tabs, type TabItem } from '../components/surfaces'
import { Stamp } from '../components/stickers'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { blankToNull } from '../lib/text'
import { ProjectForm, type ProjectFormValues } from './ProjectForm'
import { NotFoundPage } from './NotFoundPage'
import { ActivitySection } from './ActivitySection'
import { BudgetSection } from './BudgetSection'
import { ProjectLocationsSection } from './ProjectLocationsSection'
import { ProjectOutreachSection } from './ProjectOutreachSection'
import { ScenesSection } from './ScenesSection'
import { ScheduleSection } from './ScheduleSection'
import { usePageTitle } from '../lib/usePageTitle'
import { AvatarStack } from '../components/Avatar'
import { VenuePicture } from '../components/VenuePicture'
import { posterBackdrop } from '../lib/poster'

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

type TabKey = 'scenes' | 'locations' | 'outreach' | 'schedule' | 'budget' | 'activity'

const tabs: TabItem<TabKey>[] = [
  { key: 'scenes', label: 'Scenes', icon: Film },
  { key: 'locations', label: 'Locations', icon: MapPinned },
  { key: 'outreach', label: 'Outreach', icon: Mail },
  { key: 'schedule', label: 'Schedule', icon: CalendarDays },
  { key: 'budget', label: 'Budget', icon: Wallet },
  { key: 'activity', label: 'Activity', icon: ActivityIcon },
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
      <Link to="/projects" className="inline-flex items-center gap-1 text-sm font-medium text-muted hover:text-ink">
        <ChevronLeft aria-hidden className="size-4" />
        Productions
      </Link>

      {editing ? (
        <Card className="p-6">
          <section aria-labelledby="edit-project">
            <h1 id="edit-project" className="mb-4 font-display text-4xl leading-none font-bold">
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
        // The title card: a still of one of its venues behind a slow push-in, or a lit backdrop.
        <header
          className="hero flex min-h-[22rem] animate-fade-in flex-col justify-end rounded-2xl"
          style={project.posterImageUrl ? undefined : { background: posterBackdrop(project.title) }}
        >
          {project.posterImageUrl && (
            <div aria-hidden className="hero-picture">
              <VenuePicture src={project.posterImageUrl} className="h-full w-full" />
            </div>
          )}
          <div className="space-y-5 p-6 sm:p-10">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="space-y-3">
                <Eyebrow onDark icon={Clapperboard}>A CineScout production</Eyebrow>
                <div className="flex flex-wrap items-center gap-3">
                  <h1 className="font-display text-4xl leading-[1] font-bold sm:text-6xl">{project.title}</h1>
                  {archived && <Stamp tone="ink" announce className="!bg-white text-sm">Archived</Stamp>}
                </div>
                <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm text-fog">
                  <p className="flex items-center gap-1.5">
                    <MapPin aria-hidden className="size-4 text-cue" />
                    {project.locationArea ?? 'No location area set. Scouting needs one.'}
                  </p>
                  {(project.crew ?? []).length > 0 && (
                    <p className="flex items-center gap-2.5">
                      <AvatarStack names={project.crew ?? []} max={5} />
                      <span>{(project.crew ?? []).length === 1 ? 'Just you so far' : `${(project.crew ?? []).length} on the crew`}</span>
                    </p>
                  )}
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <Link to={`/projects/${project.id}/settings`} className={`${linkButton('secondary')} !border-white/20 !bg-white/10 !text-white backdrop-blur hover:!bg-white/20`}>
                  <Users aria-hidden className="size-4" />
                  Crew
                </Link>
                {editor && (
                  <Button variant="secondary" className="!border-white/20 !bg-white/10 !text-white backdrop-blur hover:!bg-white/20" onClick={() => setEditing(true)}>
                    Edit
                  </Button>
                )}
                {owner && (
                  <>
                    <Button variant="secondary" className="!border-white/20 !bg-white/10 !text-white backdrop-blur hover:!bg-white/20" onClick={toggleArchived} busy={update.isPending}>
                      {archived ? 'Restore' : 'Archive'}
                    </Button>
                    <Button variant="ghost" className="!text-fog hover:!bg-white/10 hover:!text-white" onClick={() => setConfirmingDelete(true)}>
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
          {tab === 'budget' && <BudgetSection projectId={project.id} />}
          {tab === 'activity' && <ActivitySection projectId={project.id} />}
        </Tabs>
      )}
    </div>
    </ProjectRoleProvider>
  )
}
