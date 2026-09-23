import { describe, expect, it } from 'vitest'
import { formatDate, formatShootWindow, sceneLabel } from './format'

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
