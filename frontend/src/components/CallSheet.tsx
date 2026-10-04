import type { Schedule, ScheduledScene } from '../api/types'
import { dayConditions, formatDate, formatDay } from '../lib/format'
import { timeWindow } from '../lib/availability'

/**
 * The shoot on paper: for each day, which scenes, where, and who to call there. A scene without a confirmed
 * location says so (TBC) rather than being left off, as the sheet is often what shows the gap. Printed on its
 * own (`print-sheet`), as ink on white.
 */
export function CallSheet({ title, locationArea, preparedBy, schedule }: { title: string; locationArea: string | null; preparedBy: string; schedule: Schedule }) {
  return (
    <article
      aria-label="Call sheet"
      className="print-sheet mx-auto max-w-4xl space-y-8 rounded-sm bg-paper px-6 py-8 font-script text-[13px] leading-relaxed text-ink shadow-2xl ring-1 ring-ink/20 sm:px-12 sm:py-12"
    >
      <header className="space-y-2 border-b-2 border-line pb-4 text-center">
        <p className="text-xs tracking-[0.4em] uppercase">Call sheet</p>
        <h2 className="font-display text-5xl leading-none tracking-wide text-ink">{title}</h2>
        <p>
          {locationArea && `${locationArea} · `}Prepared by {preparedBy}
        </p>
      </header>

      {schedule.days.length === 0 && <p className="text-center">No scene has a shoot date yet. Give the scenes their dates and they appear here.</p>}

      {schedule.days.map((day, index) => (
        <section key={day.date} aria-labelledby={`sheet-${day.date}`} className="space-y-2">
          <h3 id={`sheet-${day.date}`} className="flex flex-wrap items-baseline justify-between gap-2 bg-white px-3 py-1.5 font-bold text-paper uppercase">
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
          <h3 id="sheet-unscheduled" className="border-y border-line px-3 py-1.5 font-bold uppercase">
            To be scheduled
          </h3>
          <Scenes scenes={schedule.unscheduled} />
        </section>
      )}

      <footer className="border-t border-line pt-3 text-center text-xs text-subtle">
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
          <tr className="border-b border-line text-[11px] tracking-widest uppercase">
            <th scope="col" className="w-14 px-3 py-1.5 font-bold">Sc.</th>
            <th scope="col" className="px-3 py-1.5 font-bold">Set</th>
            <th scope="col" className="w-24 px-3 py-1.5 font-bold">D/N</th>
            <th scope="col" className="w-[30%] px-3 py-1.5 font-bold">Location</th>
            <th scope="col" className="w-[22%] px-3 py-1.5 font-bold">Contact</th>
          </tr>
        </thead>
        <tbody>
          {scenes.map((scene) => (
            <tr key={scene.id} className="border-b border-line align-top">
              <td className="px-3 py-2 font-bold">{scene.sceneNumber ?? '—'}</td>
              <td className="px-3 py-2">
                <span className="font-bold">{scene.title}</span>
                {scene.characters.length > 0 && <span className="block">Cast: {scene.characters.join(', ')}</span>}
                {scene.shootDateStart && scene.shootDateEnd && scene.shootDateEnd !== scene.shootDateStart && (
                  <span className="block text-subtle">until {formatDate(scene.shootDateEnd)}</span>
                )}
              </td>
              <td className="px-3 py-2 uppercase">
                {scene.timeOfDay ?? '—'}
                {timeWindow(scene.callTime, scene.wrapTime) && <span className="block normal-case">{timeWindow(scene.callTime, scene.wrapTime)}</span>}
              </td>
              <td className="px-3 py-2">
                {scene.venues.length === 0 ? (
                  <span className="font-bold">TBC</span>
                ) : (
                  scene.venues.map((venue) => (
                    <span key={venue.id} className="block">
                      <span className="font-bold">{venue.name}</span>
                      {venue.address && <span className="block">{venue.address}</span>}
                      {dayConditions(venue.day) && <span className="block text-subtle">{dayConditions(venue.day)}</span>}
                      {venue.day?.sun.map((sun) => sun.text && (
                        <span key={sun.text} className="block text-subtle">
                          {sun.text}
                        </span>
                      ))}
                      {venue.day?.warnings.map((warning) => (
                        <span key={warning} className="block font-bold">
                          ! {warning}
                        </span>
                      ))}
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
