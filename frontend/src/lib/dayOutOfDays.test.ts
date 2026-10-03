import { describe, expect, it } from 'vitest'
import { schedule, scheduled } from '../test/fixtures'
import { dayOutOfDays } from './dayOutOfDays'

describe('dayOutOfDays', () => {
  it('lists each speaking part with the shoot days they work, leaving out undated scenes', () => {
    const report = dayOutOfDays({
      days: [
        { date: '2026-10-12', scenes: [scheduled({ characters: ['MARA', 'JONES'] }), scheduled({ id: 's2', characters: ['MARA'] })] },
        { date: '2026-10-15', scenes: [scheduled({ id: 's3', characters: ['KAPLAN', 'MARA'] })] },
      ],
      unscheduled: [scheduled({ id: 's4', characters: ['EXTRA'] })],
      conflicts: [],
    })

    expect(report.days).toEqual(['2026-10-12', '2026-10-15'])
    expect(report.cast.map(({ name, days }) => [name, [...days]])).toEqual([
      ['MARA', ['2026-10-12', '2026-10-15']],
      ['JONES', ['2026-10-12']],
      ['KAPLAN', ['2026-10-15']],
    ])
  })

  it('has nobody when no dated scene has dialogue', () => {
    expect(dayOutOfDays({ ...schedule, days: [{ date: '2026-10-12', scenes: [scheduled({ characters: [] })] }] }).cast).toEqual([])
  })
})
