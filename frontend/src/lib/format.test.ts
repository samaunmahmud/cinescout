import { describe, expect, it } from 'vitest'
import { formatDate, formatShootWindow, sceneLabel, scoutingSummary } from './format'
import { location } from '../test/fixtures'

describe('formatDate', () => {
  it('shows the calendar date as written, whatever the time zone', () => {
    // Midnight UTC is still the previous evening west of Greenwich; a plain date must not move.
    expect(formatDate('2026-01-01')).toMatch(/2026/)
    expect(formatDate('2026-01-01')).toMatch(/\b1\b/)
  })
})

describe('formatShootWindow', () => {
  it('covers every combination of dates', () => {
    expect(formatShootWindow(null, null)).toBeNull()
    expect(formatShootWindow('2026-10-12', '2026-10-12')).toBe(formatDate('2026-10-12'))
    expect(formatShootWindow('2026-10-12', '2026-10-14')).toBe(`${formatDate('2026-10-12')} – ${formatDate('2026-10-14')}`)
    expect(formatShootWindow('2026-10-12', null)).toBe(`From ${formatDate('2026-10-12')}`)
    expect(formatShootWindow(null, '2026-10-14')).toBe(`Until ${formatDate('2026-10-14')}`)
  })
})

describe('sceneLabel', () => {
  it('puts the number first when there is one', () => {
    expect(sceneLabel({ sceneNumber: 4, title: 'Chase' })).toBe('Scene 4: Chase')
    expect(sceneLabel({ sceneNumber: null, title: 'Chase' })).toBe('Chase')
  })
})

describe('scoutingSummary', () => {
  it('only mentions what happened, in the right number', () => {
    expect(scoutingSummary({ added: [], alreadySaved: 0, unassessed: 0 })).toBe('No new venues found.')
    expect(scoutingSummary({ added: [location(), location({ id: 'l2' })], alreadySaved: 1, unassessed: 0 })).toBe(
      'Found 2 new venues. 1 venue was already saved and left as it was.',
    )
    expect(scoutingSummary({ added: [], alreadySaved: 0, unassessed: 3 })).toBe('No new venues found. 3 venues could not be assessed and were left out.')
  })
})
