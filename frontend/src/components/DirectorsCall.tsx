import type { DirectorCall } from '../api/types'
import { formatDate } from '../lib/format'
import { verdictStamps } from './directorCalls'
import { Stamp } from './stickers'

/**
 * What the director (and anyone else given the link) said about a venue: a stamp per guest, with their comment.
 * `compact` drops the comments, for a cell in Compare.
 */
export function DirectorsCall({ calls, compact = false }: { calls: DirectorCall[]; compact?: boolean }) {
  if (calls.length === 0) return compact ? <span className="text-subtle">No call yet</span> : null
  return (
    <ul aria-label="Director’s call" className={compact ? 'space-y-1.5' : 'space-y-3'}>
      {calls.map((call) => {
        const stamp = verdictStamps[call.verdict]
        return (
          <li key={call.id} className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <Stamp announce tone={stamp.tone} className="text-xs">
                {stamp.word}
              </Stamp>
              <span className="text-sm font-semibold text-ink">{call.guestName}</span>
              {!compact && <span className="text-xs text-subtle">{formatDate(call.updatedAt.slice(0, 10))}</span>}
            </div>
            {!compact && call.comment && <p className="border-l-4 border-line pl-3 text-sm whitespace-pre-line text-graphite">{call.comment}</p>}
          </li>
        )
      })}
    </ul>
  )
}
