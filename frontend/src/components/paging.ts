import { useCallback, useEffect } from 'react'
import { useSearchParams } from 'react-router'
import type { Page } from '../api/types'

/**
 * The page of the screen's list, kept in the URL (`?page=2`, one-based) so a reload or the back button returns to
 * it. Other parameters (a tab, a filter) are left alone. Returns the zero-based page and a setter.
 */
export function usePageParam(): [number, (page: number) => void] {
  const [params, setParams] = useSearchParams()
  const raw = Number(params.get('page'))
  const page = Number.isInteger(raw) && raw > 1 ? raw - 1 : 0
  const setPage = useCallback(
    (next: number) =>
      setParams((current) => {
        const updated = new URLSearchParams(current)
        if (next > 0) updated.set('page', String(next + 1))
        else updated.delete('page')
        return updated
      }),
    [setParams],
  )
  return [page, setPage]
}

/** Moves to the last page when the list has shrunk under the one shown (its last items deleted, say). */
export function useStayInRange(data: Page<unknown> | undefined, setPage: (page: number) => void) {
  const pastTheEnd = data !== undefined && data.page > 0 && data.page >= data.totalPages
  const lastPage = data ? Math.max(0, data.totalPages - 1) : 0
  useEffect(() => {
    if (pastTheEnd) setPage(lastPage)
  }, [pastTheEnd, lastPage, setPage])
}

/**
 * For `useQuery`'s `placeholderData`: while another page of the same list loads, keep showing the one before, so
 * the list does not flash a spinner. A different list (another scene's, say) starts from its own spinner.
 */
export function previousPageOf<T>(listKey: readonly unknown[]) {
  return (previous: T | undefined, previousQuery?: { queryKey: readonly unknown[] }) =>
    previousQuery && listKey.every((part, i) => previousQuery.queryKey[i] === part) ? previous : undefined
}
