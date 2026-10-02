import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { Page } from '../api/types'
import { Button } from './ui'

/** Previous and next for a paged list, and where in it the user is. Nothing when it fits on one page. */
export function Pager({ data, onChange, label }: { data: Page<unknown>; onChange: (page: number) => void; label: string }) {
  if (data.totalPages <= 1) return null
  const first = data.page * data.size + 1
  const last = data.page * data.size + data.items.length
  return (
    <nav aria-label={label} className="flex flex-wrap items-center justify-between gap-3 pt-2">
      <Button variant="ghost" disabled={data.page === 0} onClick={() => onChange(data.page - 1)}>
        <ChevronLeft aria-hidden className="size-4" />
        Previous
      </Button>
      <p className="text-sm text-muted">
        <span className="text-ink">
          {first}–{last}
        </span>{' '}
        of {data.totalItems} · page {data.page + 1} of {data.totalPages}
      </p>
      <Button variant="ghost" disabled={data.page >= data.totalPages - 1} onClick={() => onChange(data.page + 1)}>
        Next
        <ChevronRight aria-hidden className="size-4" />
      </Button>
    </nav>
  )
}
