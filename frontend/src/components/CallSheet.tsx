import type { Moves, Schedule, ScheduledScene } from '../api/types'
import { CompanyMoves } from './CompanyMoves'
import { movesOn } from '../lib/moves'
import { dayConditions, formatDate, formatDay } from '../lib/format'
import { timeWindow } from '../lib/availability'

/**
 * The shoot on paper: for each day, which scenes, where, and who to call there. A scene without a confirmed
 * location says so (TBC) rather than being left off, as the sheet is often what shows the gap. Printed on its
 * own (`print-sheet`), as ink on white.
 */
export function CallSheet({
  title,
  locationArea,
  preparedBy,
  schedule,
  moves,
}: {
  title: string
  locationArea: string | null
  preparedBy: string
  schedule: Schedule
  /** Company moves, when known; a day with several venues lists the drives between them. */
  moves?: Moves
}) {
  const anyTbc = [...schedule.days.flatMap((day) => day.scenes), ...schedule.unscheduled].some((scene) => scene.venues.length === 0)
  const anyMoves = moves?.days.some((day) => day.moves.some((move) => move.status === 'OK')) ?? false
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
          <h3 id={`sheet-${day.date}`} className="flex flex-wrap items-baseline justify-between gap-2 bg-night px-3 py-1.5 font-bold text-paper uppercase [print-color-adjust:exact]">
            <span>{formatDay(day.date)}</span>
            <span className="text-xs font-normal tracking-widest">
              Day {index + 1} of {schedule.days.length}
            </span>
          </h3>
          <Scenes scenes={day.scenes} />
          {moves && <CompanyMoves sheet moves={movesOn(moves, day.date)} warnAfterMinutes={moves.warnAfterMinutes} />}
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
        {anyTbc && 'TBC: no location is confirmed for the scene yet. '}{anyMoves && `Drive times without traffic. ${moves?.attribution}. `}Made with CineScout.
      </footer>
    </article>
  )
}

function Scenes({ scenes }: { scenes: ScheduledScene[] }) {
  return (
    <div className="sm:overflow-x-auto">
      <table role="table" className="w-full border-collapse text-left align-top max-sm:block sm:min-w-[36rem] sm:table-fixed">
        <thead role="rowgroup" className="max-sm:sr-only">
          <tr role="row" className="border-b border-line text-[11px] tracking-widest uppercase">
            <th role="columnheader" scope="col" className="w-14 px-3 py-1.5 font-bold">Sc.</th>
            <th role="columnheader" scope="col" className="px-3 py-1.5 font-bold">Set</th>
            <th role="columnheader" scope="col" className="w-24 px-3 py-1.5 font-bold">D/N</th>
            <th role="columnheader" scope="col" className="w-[30%] px-3 py-1.5 font-bold">Location</th>
            <th role="columnheader" scope="col" className="w-[22%] px-3 py-1.5 font-bold">Contact</th>
          </tr>
        </thead>
        <tbody role="rowgroup" className="max-sm:block">
          {scenes.map((scene) => (
            <tr key={scene.id} role="row" className="border-b border-line align-top max-sm:grid max-sm:grid-cols-[2.5rem_1fr] max-sm:gap-x-2 max-sm:py-2">
              <td role="cell" className="px-3 py-2 font-bold max-sm:row-span-4 max-sm:px-0">{scene.sceneNumber ?? '—'}</td>
              <td role="cell" className="px-3 py-2 max-sm:col-start-2 max-sm:px-0 max-sm:py-0.5">
                <span className="font-bold">{scene.title}</span>
                {scene.characters.length > 0 && <span className="block">Cast: {scene.characters.join(', ')}</span>}
                {scene.shootDateStart && scene.shootDateEnd && scene.shootDateEnd !== scene.shootDateStart && (
                  <span className="block text-subtle">until {formatDate(scene.shootDateEnd)}</span>
                )}
              </td>
              <td role="cell" className="px-3 py-2 uppercase max-sm:block max-sm:col-start-2 max-sm:px-0 max-sm:py-0.5 max-sm:before:block max-sm:before:text-[10px] max-sm:before:tracking-widest max-sm:before:text-subtle max-sm:before:uppercase max-sm:before:content-['D/N'] max-sm:empty:hidden">
                {scene.timeOfDay ?? '—'}
                {timeWindow(scene.callTime, scene.wrapTime) && <span className="block normal-case">{timeWindow(scene.callTime, scene.wrapTime)}</span>}
              </td>
              <td role="cell" className="px-3 py-2 max-sm:block max-sm:col-start-2 max-sm:px-0 max-sm:py-0.5 max-sm:before:block max-sm:before:text-[10px] max-sm:before:tracking-widest max-sm:before:text-subtle max-sm:before:uppercase max-sm:before:content-['Location'] max-sm:empty:hidden">
                {scene.venues.length === 0 ? (
                  <span className="font-bold">TBC</span>
                ) : (
                  scene.venues.map((venue) => (
                    <span key={venue.id} className="block">
                      <span className="font-bold">{venue.name}</span>
                      {venue.address && <span className="block">{venue.address}</span>}
                      {dayConditions(venue.day) && <span className="block text-subtle">{dayConditions(venue.day)}</span>}
                      {/* Only while the sun is up: a night shoot's "7° below the horizon" lines are noise on paper. */}
                      {venue.day?.sun.filter((sun) => sun.elevation == null || sun.elevation >= 0).map((sun) => sun.text && (
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
                {scene.covers.map((cover) => (
                  <span key={cover.id} className="mt-1 block border-t border-dashed border-line pt-1">
                    <span className="font-bold">Cover: {cover.name}</span>
                    {cover.trigger && <span> ({cover.trigger})</span>}
                    {cover.address && <span className="block">{cover.address}</span>}
                  </span>
                ))}
              </td>
              <td role="cell" className="px-3 py-2 max-sm:block max-sm:col-start-2 max-sm:px-0 max-sm:py-0.5 max-sm:before:block max-sm:before:text-[10px] max-sm:before:tracking-widest max-sm:before:text-subtle max-sm:before:uppercase max-sm:before:content-['Contact'] max-sm:empty:hidden">
                {scene.venues.map((venue) => (
                  <span key={venue.id} className="block">
                    {venue.contactName ?? (venue.contactPhone ? '' : '—')}
                    {venue.contactPhone && <span className="block">{venue.contactPhone}</span>}
                  </span>
                ))}
                {scene.covers
                  .filter((cover) => cover.contactName || cover.contactPhone)
                  .map((cover) => (
                    <span key={cover.id} className="mt-1 block border-t border-dashed border-line pt-1">
                      <span className="text-subtle">Cover: </span>
                      {[cover.contactName, cover.contactPhone].filter(Boolean).join(', ')}
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
