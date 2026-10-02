import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { CalendarClock, CalendarDays, CalendarOff, MapPin as PinIcon, Printer, SunMedium, TriangleAlert, Users } from 'lucide-react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Schedule, ScheduledScene } from '../api/types'
import { dayOutOfDays } from '../lib/dayOutOfDays'
import { useSession } from '../auth/context'
import { linkButton } from '../components/buttonStyles'
import { EmptyState, Section, Slate } from '../components/surfaces'
import { Button, ErrorAlert, Spinner, TextField } from '../components/ui'
import { batchLogisticsSummary, dayConditions, formatDate, formatDay, scheduleSummary } from '../lib/format'

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
  const queryClient = useQueryClient()
  const conditions = useMutation({
    mutationFn: () => api.projects.refreshLogistics(projectId),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.projectSchedule(projectId) }),
  })
  // Worth offering while a dated scene's confirmed venue has nothing to say about its day yet.
  const missingConditions =
    schedule.data?.days.some((day) => day.scenes.some((scene) => scene.venues.some((venue) => venue.day === null))) ?? false

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
          <>
            {missingConditions && (
              <Button variant="ghost" busy={conditions.isPending} onClick={() => conditions.mutate()}>
                {!conditions.isPending && <SunMedium aria-hidden className="size-4" />}
                Get light and weather
              </Button>
            )}
            <Link to={`/projects/${projectId}/call-sheet`} className={linkButton('secondary')}>
              <Printer aria-hidden className="size-4" />
              Call sheet
            </Link>
          </>
        )
      }
    >
      {conditions.isPending ? (
        <Spinner label="Working out the light and weather at each confirmed venue. This takes a few seconds a venue." />
      ) : conditions.isError ? (
        <ErrorAlert error={conditions.error} />
      ) : (
        conditions.data && (
          <p role="status" className="rounded-lg border border-go-mid bg-go-wash px-4 py-3 text-sm text-go-ink">
            {batchLogisticsSummary(conditions.data)}
          </p>
        )
      )}
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
          <CastDays schedule={schedule.data} />
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
    <section aria-labelledby={titleId} className="overflow-hidden board-card rounded-lg bg-white">
      <h3 id={titleId} className={`flex items-center gap-2 border-b border-line px-4 py-3 font-semibold ${dated ? 'text-cue-ink' : 'text-muted'}`}>
        <Icon aria-hidden className="size-4" />
        {title}
      </h3>
      <ul className="divide-y divide-line-soft">
        {scenes.map((scene) => (
          <li key={scene.id}>
            <SceneRow scene={scene} projectId={projectId} dated={dated} />
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Day out of days: who of the cast works on which shoot day, for planning their calls. */
function CastDays({ schedule }: { schedule: Schedule }) {
  const report = dayOutOfDays(schedule)
  if (report.cast.length === 0 || report.days.length === 0) return null
  return (
    <section aria-labelledby="cast-days" className="space-y-3">
      <h3 id="cast-days" className="flex items-center gap-2 font-display text-2xl leading-none text-ink">
        <Users aria-hidden className="size-5 text-cue-ink" />
        Day out of days
      </h3>
      <div className="overflow-x-auto board-card rounded-lg bg-white">
        <table className="w-full border-collapse text-sm">
          <caption className="sr-only">Which of the cast works on which shoot day</caption>
          <thead>
            <tr className="border-b border-line text-left text-[11px] tracking-wider text-subtle uppercase">
              <th scope="col" className="px-4 py-2 font-semibold">Cast</th>
              {report.days.map((day) => (
                <th key={day} scope="col" className="px-3 py-2 text-center font-semibold">
                  {shortDay(day)}
                </th>
              ))}
              <th scope="col" className="px-4 py-2 text-right font-semibold">Days</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line-soft">
            {report.cast.map(({ name, days }) => (
              <tr key={name}>
                <th scope="row" className="px-4 py-2 text-left font-semibold text-ink">
                  {name}
                </th>
                {report.days.map((day) => (
                  <td key={day} className="px-3 py-2 text-center">
                    {days.has(day) ? (
                      <span className="inline-flex size-6 items-center justify-center rounded bg-cue-wash text-xs font-bold text-cue-ink ring-1 ring-cue">
                        W<span className="sr-only">orks</span>
                      </span>
                    ) : (
                      <span className="text-subtle">·</span>
                    )}
                  </td>
                ))}
                <td className="px-4 py-2 text-right text-graphite">{days.size}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

const shortDayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' })

/** "Mon 12 Oct", for a column heading. */
function shortDay(isoDate: string): string {
  return shortDayFormat.format(new Date(`${isoDate}T00:00:00Z`))
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
          <Link to={`/scenes/${scene.id}`} className="block truncate text-lg font-semibold text-ink hover:text-cue-deep">
            {scene.title}
          </Link>
          <p className="flex flex-wrap gap-x-3 text-sm text-muted">
            {scene.settingType && <span className="text-graphite">{scene.settingType}</span>}
            {scene.timeOfDay && <span>{scene.timeOfDay}</span>}
            {scene.characters.length > 0 && <span className="text-graphite">{scene.characters.join(', ')}</span>}
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
        <form onSubmit={submit} aria-label={`Shoot dates of ${scene.title}`} className="flex flex-wrap items-end gap-3 rounded-lg bg-ground p-3 ring-1 ring-line" noValidate>
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
            <PinIcon aria-hidden className="mt-0.5 size-3.5 shrink-0 text-go-ink" />
            <span className="min-w-0">
              <Link to={`/locations/${venue.id}`} className="font-semibold text-go-ink hover:underline">
                {venue.name}
              </Link>
              {venue.address && <span className="block truncate text-muted">{venue.address}</span>}
              {dayConditions(venue.day) && <span className="block text-xs text-cue-ink">{dayConditions(venue.day)}</span>}
              {venue.day?.warnings.map((warning) => (
                <span key={warning} className="flex items-start gap-1 text-xs text-cue-ink">
                  <TriangleAlert aria-hidden className="mt-0.5 size-3 shrink-0" />
                  {warning}
                </span>
              ))}
            </span>
          </li>
        ))}
      </ul>
    )
  }
  const next =
    scene.candidates === 0 ? 'Not scouted yet' : scene.candidates === 1 ? '1 candidate, none confirmed' : `${scene.candidates} candidates, none confirmed`
  return (
    <p className={`flex w-full items-center gap-1.5 text-sm sm:w-72 ${urgent ? 'text-cue-ink' : 'text-muted'}`}>
      {urgent && <TriangleAlert aria-hidden className="size-3.5 shrink-0" />}
      {next}
    </p>
  )
}
