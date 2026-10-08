import { LayoutGrid, List } from 'lucide-react'
import { useId } from 'react'
import type { LocationSort } from '../api/types'
import { statusLabels } from '../lib/status'
import { sortLabels, sorts, statuses, type useVenueListView } from './venueListView'

/** Sort, filter by status, and cards or a compact list, above a list of venues. */
export function ListControls({ state }: { state: ReturnType<typeof useVenueListView> }) {
  const sortId = useId()
  const chip = (active: boolean) =>
    `rounded-full border-2 px-3 py-1 text-sm font-semibold transition focus-visible:ring-4 focus-visible:ring-cue/30 focus-visible:outline-none ${
      active ? 'border-ink bg-ink text-white' : 'border-line bg-white text-graphite hover:border-ink hover:text-ink'
    }`
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div role="group" aria-label="Show venues" className="flex flex-wrap gap-1.5">
        <button type="button" aria-pressed={state.status === null} className={chip(state.status === null)} onClick={() => state.setStatus(null)}>
          All
        </button>
        {statuses.map((status) => (
          <button key={status} type="button" aria-pressed={state.status === status} className={chip(state.status === status)} onClick={() => state.setStatus(status)}>
            {status === 'REJECTED' ? 'Passed' : statusLabels[status]}
          </button>
        ))}
      </div>
      <div className="flex items-center gap-2">
        <label htmlFor={sortId} className="font-script text-xs font-bold tracking-[0.08em] text-muted uppercase">
          Sort
        </label>
        <select
          id={sortId}
          value={state.sort}
          onChange={(e) => state.setSort(e.target.value as LocationSort)}
          className="rounded-lg border-2 border-ink bg-white px-2 py-1.5 text-sm font-semibold text-ink focus:ring-4 focus:ring-cue/25 focus:outline-none"
        >
          {sorts.map((sort) => (
            <option key={sort} value={sort}>
              {sortLabels[sort]}
            </option>
          ))}
        </select>
        <div role="group" aria-label="Layout" className="flex overflow-hidden rounded-lg border-2 border-ink">
          {(['cards', 'list'] as const).map((view) => {
            const Icon = view === 'cards' ? LayoutGrid : List
            return (
              <button
                key={view}
                type="button"
                aria-pressed={state.view === view}
                aria-label={view === 'cards' ? 'Cards' : 'Compact list'}
                title={view === 'cards' ? 'Cards' : 'Compact list'}
                onClick={() => state.setView(view)}
                className={`flex size-8 items-center justify-center transition focus-visible:ring-4 focus-visible:ring-cue/30 focus-visible:outline-none ${
                  state.view === view ? 'bg-ink text-white' : 'bg-white text-graphite hover:text-ink'
                }`}
              >
                <Icon aria-hidden className="size-4" />
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
