import { Car, TriangleAlert } from 'lucide-react'
import type { Move } from '../api/types'

/**
 * A shoot day's company moves, in order: the drive from each venue to the next. A move longer than the project's
 * threshold is flagged; one not worked out says why. `sheet` is the call sheet's ink-on-paper look.
 */
export function CompanyMoves({
  moves,
  warnAfterMinutes,
  attribution,
  sheet = false,
}: {
  moves: Move[]
  warnAfterMinutes: number
  /** The routing data's credit, shown under the list (the call sheet carries it in its footer instead). */
  attribution?: string
  sheet?: boolean
}) {
  if (moves.length === 0) return null
  if (sheet) {
    return (
      <div className="px-3">
        <p className="font-bold uppercase">Company moves</p>
        <ul>
          {moves.map((move) => (
            <li key={`${move.fromLocationId}-${move.toLocationId}`} className={move.tooLong ? 'font-bold' : undefined}>
              {move.tooLong && '! '}
              {move.text}
              {move.tooLong && ` (over ${warnAfterMinutes} min)`}
            </li>
          ))}
        </ul>
      </div>
    )
  }
  return (
    <div className="border-t border-line-soft bg-ground/60 px-4 py-3">
      <p className="flex items-center gap-1.5 text-sm font-semibold text-graphite">
        <Car aria-hidden className="size-4" />
        Company moves
      </p>
      <ul className="mt-1 space-y-0.5 text-sm">
        {moves.map((move) => (
          <li key={`${move.fromLocationId}-${move.toLocationId}`} className={`flex items-start gap-1.5 ${move.tooLong ? 'font-semibold text-stop-ink' : move.status === 'OK' ? 'text-graphite' : 'text-muted'}`}>
            {move.tooLong && <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />}
            <span>
              {move.text}
              {move.tooLong && ` (over ${warnAfterMinutes} min)`}
            </span>
          </li>
        ))}
      </ul>
      {attribution && moves.some((move) => move.status === 'OK') && (
        <p className="mt-1 text-xs text-muted">Drive times without traffic. {attribution}.</p>
      )}
    </div>
  )
}
