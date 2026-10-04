import type { Move, Moves } from '../api/types'

/** The moves of one shoot day, or none. */
export function movesOn(moves: Moves | undefined, date: string): Move[] {
  return moves?.days.find((day) => day.date === date)?.moves ?? []
}
