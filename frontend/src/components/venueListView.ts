import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router'
import type { LocationSort, LocationStatus } from '../api/types'

export type VenueView = 'cards' | 'list'

export const sortLabels: Record<LocationSort, string> = { FIT: 'Best fit', NAME: 'Name A–Z', NEWEST: 'Newest first', STATUS: 'Furthest along' }
export const sorts = Object.keys(sortLabels) as LocationSort[]
export const statuses: LocationStatus[] = ['SUGGESTED', 'SHORTLISTED', 'CONTACTED', 'CONFIRMED', 'REJECTED']
const VIEW_KEY = 'cinescout.venueView'

function storedView(): VenueView {
  try {
    return localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'cards'
  } catch {
    return 'cards'
  }
}

/**
 * How the venue list is shown: the sort and the status filter live in the URL (`?sort=NAME&status=SHORTLISTED`), so a
 * link or the back button keeps them, and changing either goes back to the first page; the cards-or-list view is
 * remembered in this browser.
 */
export function useVenueListView() {
  const [params, setParams] = useSearchParams()
  const sortParam = params.get('sort') as LocationSort | null
  const statusParam = params.get('status') as LocationStatus | null
  const sort: LocationSort = sortParam && sorts.includes(sortParam) ? sortParam : 'FIT'
  const status: LocationStatus | null = statusParam && statuses.includes(statusParam) ? statusParam : null
  const [view, setViewState] = useState<VenueView>(storedView)

  const set = useCallback(
    (key: 'sort' | 'status', value: string | null) =>
      setParams((current) => {
        const next = new URLSearchParams(current)
        if (value) next.set(key, value)
        else next.delete(key)
        next.delete('page')
        return next
      }),
    [setParams],
  )
  const setView = useCallback((next: VenueView) => {
    setViewState(next)
    try {
      localStorage.setItem(VIEW_KEY, next)
    } catch {
      // Not remembered; the choice still holds on this page.
    }
  }, [])
  return {
    sort,
    status,
    view,
    setSort: (next: LocationSort) => set('sort', next === 'FIT' ? null : next),
    setStatus: (next: LocationStatus | null) => set('status', next),
    setView,
  }
}

