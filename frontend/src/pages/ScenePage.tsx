import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Location, Scene, SceneRequest } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { ParseStatusBadge } from '../components/ParseStatusBadge'
import { CalendarDays, ChevronLeft, Clapperboard, ScrollText } from 'lucide-react'
import { Eyebrow, Slate } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { formatShootWindow, sceneLabel } from '../lib/format'
import { CoverSetsSection } from './CoverSetsSection'
import { LocationsSection } from './LocationsSection'
import { ShotListSection } from './ShotListSection'
import { NotFoundPage } from './NotFoundPage'
import { RequirementsPanel } from './RequirementsPanel'
import { SceneForm } from './SceneForm'
import { usePageTitle } from '../lib/usePageTitle'
import { AvatarStack } from '../components/Avatar'
import { VenuePicture } from '../components/VenuePicture'
import { posterBackdrop } from '../lib/poster'
import { ProjectRoleProvider } from '../components/ProjectRoleProvider'

export function ScenePage() {
  const { sceneId = '' } = useParams()
  const { api } = useSession()
  const scene = useQuery({ queryKey: queryKeys.scene(sceneId), queryFn: () => api.scenes.get(sceneId) })
  usePageTitle(scene.data && sceneLabel(scene.data))

  if (scene.isPending) return <Spinner label="Loading scene" />
  if (scene.isError) {
    if (isNotFound(scene.error)) return <NotFoundPage />
    return <ErrorAlert error={scene.error} onRetry={() => scene.refetch()} />
  }
  return <SceneDetails scene={scene.data} />
}

function SceneDetails({ scene }: { scene: Scene }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  // For the breadcrumb and the scouting area; the page works without it.
  const project = useQuery({ queryKey: queryKeys.project(scene.projectId), queryFn: () => api.projects.get(scene.projectId) })
  // The same first page the venue list shows (shared cache): its best picture is the hero's backdrop.
  const venues = useQuery({ queryKey: queryKeys.locationPage(scene.id, 0), queryFn: () => api.locations.list(scene.id, 0) })
  const heroImage = heroPicture(venues.data?.items ?? [])
  const projectPath = `/projects/${scene.projectId}`

  const update = useMutation({
    mutationFn: (body: SceneRequest) => api.scenes.update(scene.id, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.scene(scene.id), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(scene.projectId) })
      setEditing(false)
    },
  })

  const remove = useMutation({
    mutationFn: () => api.scenes.remove(scene.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.scene(scene.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(scene.projectId) })
      navigate(projectPath, { replace: true })
    },
  })

  const shootWindow = formatShootWindow(scene.shootDateStart, scene.shootDateEnd)

  const canEdit = project.data?.role !== 'VIEWER'

  return (
    <ProjectRoleProvider role={project.data?.role}>
    <div className="space-y-8">
      <Link to={projectPath} className="inline-flex items-center gap-1 text-sm font-medium text-muted hover:text-ink">
        <ChevronLeft aria-hidden className="size-4" />
        {project.data?.title ?? 'Project'}
      </Link>

      {editing ? (
        <section aria-labelledby="edit-scene" className="board-card rounded-lg bg-paper p-6">
          <h1 id="edit-scene" className="mb-4 font-display text-3xl leading-tight font-bold">
            Edit scene
          </h1>
          <SceneForm
            initial={{
              sceneNumber: scene.sceneNumber?.toString() ?? '',
              title: scene.title,
              sourceText: scene.sourceText,
              shootDateStart: scene.shootDateStart ?? '',
              shootDateEnd: scene.shootDateEnd ?? '',
            }}
            submitLabel="Save changes"
            busy={update.isPending}
            error={update.error}
            warning={(values) =>
              scene.requirements && values.sourceText !== scene.sourceText
                ? 'Changing the script discards the requirements extracted from it. Analyse the scene again after saving.'
                : null
            }
            onSubmit={(body) => update.mutate(body)}
            onCancel={() => {
              setEditing(false)
              update.reset()
            }}
          />
        </section>
      ) : (
        // The scene's opening shot: its best venue's picture behind a slow push-in, the slate and the title over it.
        <header
          className="hero flex min-h-[20rem] animate-fade-in flex-col justify-between gap-8 rounded-2xl p-6 sm:p-8"
          style={heroImage ? undefined : { background: posterBackdrop(scene.title) }}
        >
          {heroImage && (
            <div className="hero-picture">
              <VenuePicture src={heroImage} className="h-full w-full" />
            </div>
          )}
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex items-center gap-4">
              <Slate number={scene.sceneNumber} className="ring-1 ring-white/15" />
              <Eyebrow onDark icon={Clapperboard}>{project.data?.title ?? 'Scene'}</Eyebrow>
            </div>
            {canEdit && (
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" className="!border-white/20 !bg-white/10 !text-white backdrop-blur hover:!bg-white/20" onClick={() => setEditing(true)}>
                  Edit
                </Button>
                <Button variant="ghost" className="!text-fog hover:!bg-white/10 hover:!text-white" onClick={() => setConfirmingDelete(true)}>
                  Delete
                </Button>
              </div>
            )}
          </div>
          <div className="min-w-0 space-y-4">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-display text-3xl leading-[1.05] font-bold break-words sm:text-5xl">{sceneLabel(scene)}</h1>
              <ParseStatusBadge status={scene.parseStatus} />
            </div>
            <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm text-fog">
              <p className="flex items-center gap-1.5">
                <CalendarDays aria-hidden className="size-4 text-cue" />
                {shootWindow ?? 'No shoot dates yet'}
              </p>
              {scene.characters.length > 0 && (
                <p className="flex items-center gap-2.5">
                  <AvatarStack names={scene.characters.map(titleCase)} max={5} />
                  <span>
                    <span className="sr-only">Speaking parts: </span>
                    {scene.characters.join(', ')}
                  </span>
                </p>
              )}
            </div>
          </div>
        </header>
      )}

      {confirmingDelete && (
        <ConfirmDelete
          title={`Delete “${scene.title}”?`}
          confirmLabel="Delete scene"
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirmingDelete(false)}
        >
          This also deletes the locations scouted for it and their outreach drafts. It cannot be undone.
        </ConfirmDelete>
      )}

      {!editing && (
        <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="min-w-0 space-y-10">
            <RequirementsPanel scene={scene} />
            <LocationsSection scene={scene} locationArea={project.data?.locationArea} />
            <CoverSetsSection scene={scene} />
            <ShotListSection scene={scene} />
          </div>
          <section aria-labelledby="script-heading" className="space-y-3 lg:sticky lg:top-24">
            <Eyebrow icon={ScrollText}>Screenplay</Eyebrow>
            <h2 id="script-heading" className="font-display text-2xl leading-tight font-semibold">
              Script
            </h2>
            {/* A page of the script, set as a screenplay is: monospaced, on white. */}
            <pre className="max-h-[70vh] overflow-auto rounded-xl border border-line bg-paper px-6 py-7 font-mono text-[13px] leading-relaxed whitespace-pre-wrap text-ink shadow-[var(--shadow-card)]">
              {scene.sourceText}
            </pre>
          </section>
        </div>
      )}
    </div>
    </ProjectRoleProvider>
  )
}

/** The picture to open the scene on: its confirmed venue's, else its best-fitting venue's that has one. */
function heroPicture(venues: Location[]): string | null {
  const pictured = venues.filter((venue) => venue.imageUrl)
  return (pictured.find((venue) => venue.status === 'CONFIRMED') ?? pictured.find((venue) => venue.status !== 'REJECTED'))?.imageUrl ?? null
}

/** "JUNE" -> "June": names in a script are in capitals; avatars read better in title case. */
function titleCase(name: string): string {
  return name.toLowerCase().replace(/\b\p{L}/gu, (letter) => letter.toUpperCase())
}
