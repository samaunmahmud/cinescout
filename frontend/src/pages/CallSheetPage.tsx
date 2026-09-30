import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, Printer } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Project, Schedule, ScheduledScene } from '../api/types'
import { useSession } from '../auth/context'
import { Eyebrow } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { formatDate, formatDay } from '../lib/format'
import { NotFoundPage } from './NotFoundPage'

/**
 * The shoot on paper: for each day, which scenes, where, and who to call there. Laid out as a typed sheet
 * on the page, and printed as just that sheet. A scene without a confirmed location says so (TBC) rather
 * than being left off, as the sheet is often what shows the gap.
 */
export function CallSheetPage() {
  const { projectId = '' } = useParams()
  const { api, user } = useSession()
  const project = useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => api.projects.get(projectId) })
  const schedule = useQuery({
    queryKey: queryKeys.projectSchedule(projectId),
    queryFn: () => api.projects.schedule(projectId),
    enabled: project.isSuccess,
    refetchOnMount: 'always',
  })

  if (project.isPending) return <Spinner label="Loading project" />
  if (project.isError) {
    if (isNotFound(project.error)) return <NotFoundPage />
    return <ErrorAlert error={project.error} onRetry={() => project.refetch()} />
  }

  return (
    <div className="space-y-6">
      <div className="no-print flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-3">
          <Link to={`/projects/${projectId}?tab=schedule`} className="inline-flex items-center gap-1 text-sm text-stone-400 hover:text-stone-200">
            <ChevronLeft aria-hidden className="size-4" />
            {project.data.title}
          </Link>
          <Eyebrow icon={Printer}>For the crew</Eyebrow>
          <h1 className="gold-leaf font-display text-6xl leading-none">Call sheet</h1>
        </div>
        <Button onClick={() => window.print()} disabled={!schedule.data}>
          <Printer aria-hidden className="size-4" />
          Print
        </Button>
      </div>

      {schedule.isPending ? (
        <Spinner label="Loading schedule" />
      ) : schedule.isError ? (
        <ErrorAlert error={schedule.error} onRetry={() => schedule.refetch()} />
      ) : (
        <Sheet project={project.data} schedule={schedule.data} preparedBy={user.displayName} />
      )}
    </div>
  )
}

function Sheet({ project, schedule, preparedBy }: { project: Project; schedule: Schedule; preparedBy: string }) {
  return (
    <article
      aria-label="Call sheet"
      className="print-sheet mx-auto max-w-4xl space-y-8 rounded-sm bg-paper px-6 py-8 font-script text-[13px] leading-relaxed text-stone-900 shadow-2xl shadow-black/70 ring-1 ring-black/20 sm:px-12 sm:py-12"
    >
      <header className="space-y-2 border-b-2 border-stone-900 pb-4 text-center">
        <p className="text-xs tracking-[0.4em] uppercase">Call sheet</p>
        <h2 className="font-display text-5xl leading-none tracking-wide text-stone-950">{project.title}</h2>
        <p>
          {project.locationArea && `${project.locationArea} · `}Prepared by {preparedBy}
        </p>
      </header>

      {schedule.days.length === 0 && <p className="text-center">No scene has a shoot date yet. Give the scenes their dates and they appear here.</p>}

      {schedule.days.map((day, index) => (
        <section key={day.date} aria-labelledby={`sheet-${day.date}`} className="space-y-2">
          <h3 id={`sheet-${day.date}`} className="flex flex-wrap items-baseline justify-between gap-2 bg-stone-900 px-3 py-1.5 font-bold text-paper uppercase">
            <span>{formatDay(day.date)}</span>
            <span className="text-xs font-normal tracking-widest">
              Day {index + 1} of {schedule.days.length}
            </span>
          </h3>
          <Scenes scenes={day.scenes} />
        </section>
      ))}

      {schedule.unscheduled.length > 0 && (
        <section aria-labelledby="sheet-unscheduled" className="space-y-2">
          <h3 id="sheet-unscheduled" className="border-y border-stone-900 px-3 py-1.5 font-bold uppercase">
            To be scheduled
          </h3>
          <Scenes scenes={schedule.unscheduled} />
        </section>
      )}

      <footer className="border-t border-stone-400 pt-3 text-center text-xs text-stone-600">
        TBC: no location is confirmed for the scene yet. Made with CineScout.
      </footer>
    </article>
  )
}

function Scenes({ scenes }: { scenes: ScheduledScene[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[36rem] table-fixed border-collapse text-left align-top">
        <thead>
          <tr className="border-b border-stone-900 text-[11px] tracking-widest uppercase">
            <th scope="col" className="w-14 px-3 py-1.5 font-bold">Sc.</th>
            <th scope="col" className="px-3 py-1.5 font-bold">Set</th>
            <th scope="col" className="w-24 px-3 py-1.5 font-bold">D/N</th>
            <th scope="col" className="w-[30%] px-3 py-1.5 font-bold">Location</th>
            <th scope="col" className="w-[22%] px-3 py-1.5 font-bold">Contact</th>
          </tr>
        </thead>
        <tbody>
          {scenes.map((scene) => (
            <tr key={scene.id} className="border-b border-stone-300 align-top">
              <td className="px-3 py-2 font-bold">{scene.sceneNumber ?? '—'}</td>
              <td className="px-3 py-2">
                <span className="font-bold">{scene.title}</span>
                {scene.shootDateStart && scene.shootDateEnd && scene.shootDateEnd !== scene.shootDateStart && (
                  <span className="block text-stone-600">until {formatDate(scene.shootDateEnd)}</span>
                )}
              </td>
              <td className="px-3 py-2 uppercase">{scene.timeOfDay ?? '—'}</td>
              <td className="px-3 py-2">
                {scene.venues.length === 0 ? (
                  <span className="font-bold">TBC</span>
                ) : (
                  scene.venues.map((venue) => (
                    <span key={venue.id} className="block">
                      <span className="font-bold">{venue.name}</span>
                      {venue.address && <span className="block">{venue.address}</span>}
                    </span>
                  ))
                )}
              </td>
              <td className="px-3 py-2">
                {scene.venues.map((venue) => (
                  <span key={venue.id} className="block">
                    {venue.contactName ?? (venue.contactPhone ? '' : '—')}
                    {venue.contactPhone && <span className="block">{venue.contactPhone}</span>}
                  </span>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
