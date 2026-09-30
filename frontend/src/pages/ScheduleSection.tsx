import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { CalendarClock, CalendarDays, CalendarOff, MapPin as PinIcon, Printer, TriangleAlert } from 'lucide-react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { ScheduledScene } from '../api/types'
import { useSession } from '../auth/context'
import { linkButton } from '../components/buttonStyles'
import { EmptyState, Section, Slate } from '../components/surfaces'
import { Button, ErrorAlert, Spinner, TextField } from '../components/ui'
import { dayConditions, formatDate, formatDay, scheduleSummary } from '../lib/format'

/**
 * The shoot laid out by day: which scenes start when, and where each is shot. What is missing stands out: a
 * dated scene without a confirmed location, and the scenes without a date. Refetched on every visit, as
 * dates and confirmations change on other pages.
 */
export function ScheduleSection({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const schedule = useQuery({
    queryKey: queryKeys.projectSchedule(projectId),
    queryFn: () => api.projects.schedule(projectId),
    refetchOnMount: 'always',
  })

  return (
    <Section
      titleId="schedule-heading"
      title="Schedule"
      eyebrow="The shoot, day by day"
      icon={CalendarDays}
      description={schedule.data && scheduleSummary(schedule.data)}
      actions={
        schedule.data &&
        schedule.data.days.length > 0 && (
          <Link to={`/projects/${projectId}/call-sheet`} className={linkButton('secondary')}>
            <Printer aria-hidden className="size-4" />
            Call sheet
          </Link>
        )
      }
    >
      {schedule.isPending ? (
        <Spinner label="Loading schedule" />
      ) : schedule.isError ? (
        <ErrorAlert error={schedule.error} onRetry={() => schedule.refetch()} />
      ) : schedule.data.days.length === 0 && schedule.data.unscheduled.length === 0 ? (
        <EmptyState icon={CalendarDays}>No scenes yet. Add scenes with their shoot dates and the schedule builds itself.</EmptyState>
      ) : (
        <div className="space-y-6">
          {schedule.data.days.map((day) => (
            <Day key={day.date} titleId={`day-${day.date}`} title={formatDay(day.date)} scenes={day.scenes} dated projectId={projectId} />
          ))}
          {schedule.data.unscheduled.length > 0 && (
            <Day titleId="day-unscheduled" title="Not scheduled yet" scenes={schedule.data.unscheduled} dated={false} projectId={projectId} />
          )}
        </div>
      )}
    </Section>
  )
}

function Day({
  titleId,
  title,
  scenes,
  dated,
  projectId,
}: {
  titleId: string
  title: string
  scenes: ScheduledScene[]
  dated: boolean
  projectId: string
}) {
  const Icon = dated ? CalendarDays : CalendarOff
  return (
    <section aria-labelledby={titleId} className="overflow-hidden rounded-xl border border-white/[0.07] bg-gradient-to-b from-frame/90 to-reel/90 shadow-lg shadow-black/30">
      <h3 id={titleId} className={`flex items-center gap-2 border-b border-white/[0.07] px-4 py-3 font-semibold ${dated ? 'text-amber-200' : 'text-stone-400'}`}>
        <Icon aria-hidden className="size-4" />
        {title}
      </h3>
      <ul className="divide-y divide-white/[0.05]">
        {scenes.map((scene) => (
          <li key={scene.id}>
            <SceneRow scene={scene} projectId={projectId} dated={dated} />
          </li>
        ))}
      </ul>
    </section>
  )
}

/**
 * One scene of the schedule. Its dates can be set right here, which is how an unscheduled scene gets onto a day
 * and a scheduled one is moved: saving refetches the schedule, and the scene turns up under its new day.
 */
function SceneRow({ scene, projectId, dated }: { scene: ScheduledScene; projectId: string; dated: boolean }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const save = useMutation({
    mutationFn: () => api.scenes.reschedule(scene.id, { shootDateStart: start || null, shootDateEnd: end || null }),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.scene(updated.id), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(projectId) })
      setEditing(false)
      return queryClient.invalidateQueries({ queryKey: queryKeys.projectSchedule(projectId) })
    },
  })
  const backwards = start !== '' && end !== '' && end < start

  function edit() {
    setStart(scene.shootDateStart ?? '')
    setEnd(scene.shootDateEnd ?? '')
    save.reset()
    setEditing(true)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!backwards) save.mutate()
  }

  return (
    <div className="space-y-3 px-4 py-3">
      <div className="flex flex-wrap items-center gap-4">
        <Slate number={scene.sceneNumber} />
        <div className="min-w-0 flex-1 space-y-1">
          <Link to={`/scenes/${scene.id}`} className="block truncate text-lg font-semibold text-stone-100 hover:text-amber-200">
            {scene.title}
          </Link>
          <p className="flex flex-wrap gap-x-3 text-sm text-stone-400">
            {scene.settingType && <span className="text-stone-300">{scene.settingType}</span>}
            {scene.timeOfDay && <span>{scene.timeOfDay}</span>}
            {scene.shootDateStart && scene.shootDateEnd && scene.shootDateEnd !== scene.shootDateStart && (
              <span>until {formatDate(scene.shootDateEnd)}</span>
            )}
            {!scene.shootDateStart && scene.shootDateEnd && <span>last day; no first day set</span>}
          </p>
        </div>
        <Venues scene={scene} urgent={dated} />
        {!editing && (
          <Button variant="ghost" aria-label={`${dated ? 'Change the dates of' : 'Schedule'} ${scene.title}`} onClick={edit}>
            <CalendarClock aria-hidden className="size-4" />
            {dated ? 'Dates' : 'Schedule'}
          </Button>
        )}
      </div>
      {editing && (
        <form onSubmit={submit} aria-label={`Shoot dates of ${scene.title}`} className="flex flex-wrap items-end gap-3 rounded-lg bg-black/30 p-3 ring-1 ring-amber-300/10" noValidate>
          <ErrorAlert error={save.error} />
          <TextField label="First shoot day" type="date" value={start} onChange={(e) => setStart(e.target.value)} />
          <TextField
            label="Last shoot day"
            type="date"
            value={end}
            onChange={(e) => setEnd(e.target.value)}
            error={backwards ? 'The last shoot day cannot be before the first.' : undefined}
          />
          <div className="flex gap-2">
            <Button type="submit" busy={save.isPending} disabled={backwards}>
              Save dates
            </Button>
            <Button variant="ghost" disabled={save.isPending} onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}

/** Where the scene is shot, or what stands between it and a confirmed location. */
function Venues({ scene, urgent }: { scene: ScheduledScene; urgent: boolean }) {
  if (scene.venues.length > 0) {
    return (
      <ul aria-label={`Confirmed for ${scene.title}`} className="w-full space-y-1 sm:w-72">
        {scene.venues.map((venue) => (
          <li key={venue.id} className="flex items-start gap-1.5 text-sm">
            <PinIcon aria-hidden className="mt-0.5 size-3.5 shrink-0 text-emerald-400" />
            <span className="min-w-0">
              <Link to={`/locations/${venue.id}`} className="font-semibold text-emerald-200 hover:underline">
                {venue.name}
              </Link>
              {venue.address && <span className="block truncate text-stone-400">{venue.address}</span>}
              {dayConditions(venue.day) && <span className="block text-xs text-amber-200/70">{dayConditions(venue.day)}</span>}
            </span>
          </li>
        ))}
      </ul>
    )
  }
  const next =
    scene.candidates === 0 ? 'Not scouted yet' : scene.candidates === 1 ? '1 candidate, none confirmed' : `${scene.candidates} candidates, none confirmed`
  return (
    <p className={`flex w-full items-center gap-1.5 text-sm sm:w-72 ${urgent ? 'text-amber-300' : 'text-stone-400'}`}>
      {urgent && <TriangleAlert aria-hidden className="size-3.5 shrink-0" />}
      {next}
    </p>
  )
}
