import { describe, expect, it } from 'vitest'
import type { Move, Moves } from '../api/types'
import { movesOn } from './moves'

const move = { fromName: 'Diner', toName: 'Rooftop', status: 'OK' } as Move

const moves: Moves = {
  days: [{ date: '2026-10-12', moves: [move] }],
  warnAfterMinutes: 60,
  attribution: 'OSRM',
}

describe('movesOn', () => {
  it('gives the moves of the shoot day asked for', () => {
    expect(movesOn(moves, '2026-10-12')).toEqual([move])
  })

  it('gives none for another day or before the moves have loaded', () => {
    expect(movesOn(moves, '2026-10-13')).toEqual([])
    expect(movesOn(undefined, '2026-10-12')).toEqual([])
  })
})
