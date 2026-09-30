import { describe, expect, it } from 'vitest'
import { dayConditions, formatDate, formatShootWindow, sceneLabel, scoutingSummary } from './format'
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
    expect(scoutingSummary({ added: [], alreadySaved: 0, unassessed: 0, notVenues: 0, unsuitable: 0 })).toBe('No new venues found.')
    expect(scoutingSummary({ added: [location(), location({ id: 'l2' })], alreadySaved: 1, unassessed: 0, notVenues: 0, unsuitable: 0 })).toBe(
      'Found 2 new venues. 1 venue was already saved and left as it was.',
    )
    expect(scoutingSummary({ added: [], alreadySaved: 0, unassessed: 3, notVenues: 0, unsuitable: 0 })).toBe('No new venues found. 3 venues could not be assessed and were left out.')
    expect(scoutingSummary({ added: [location()], alreadySaved: 0, unassessed: 0, notVenues: 7, unsuitable: 0 })).toBe(
      'Found 1 new venue. Skipped 7 pages that listed several venues rather than one.',
    )
    expect(scoutingSummary({ added: [], alreadySaved: 0, unassessed: 0, notVenues: 1, unsuitable: 0 })).toBe(
      'No new venues found. Skipped 1 page that listed several venues rather than one.',
    )
    expect(scoutingSummary({ added: [location()], alreadySaved: 0, unassessed: 0, notVenues: 0, unsuitable: 2 })).toBe(
      'Found 1 new venue. Left out 2 venues that could not work for this scene, such as ones in another area.',
    )
  })
})

describe('dayConditions', () => {
  it('puts the light and the weather in one line, leaving out what is not known', () => {
    expect(dayConditions({ sunrise: '07:04', sunset: '18:20', weather: 'Clear sky', temperatureMinC: 12.4, temperatureMaxC: 23.1 })).toBe(
      'Sun 07:04–18:20 · Clear sky, 12–23 °C',
    )
    expect(dayConditions({ sunrise: null, sunset: null, weather: 'Rain', temperatureMinC: null, temperatureMaxC: null })).toBe('Rain')
    expect(dayConditions({ sunrise: '07:04', sunset: '18:20', weather: null, temperatureMinC: null, temperatureMaxC: null })).toBe('Sun 07:04–18:20')
    expect(dayConditions({ sunrise: null, sunset: null, weather: null, temperatureMinC: null, temperatureMaxC: null })).toBeNull()
    expect(dayConditions(null)).toBeNull()
  })
})
