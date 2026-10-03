import { describe, expect, it } from 'vitest'
import { describeFilters, hasFilters, noFilters, parseBase } from './scoutFilters'

describe('scout filters', () => {
  it('read a base point as coordinates or as an address', () => {
    expect(parseBase(' 40.71780, -73.9577 ')).toEqual({ baseAddress: null, baseLatitude: 40.7178, baseLongitude: -73.9577 })
    expect(parseBase('12 Main St, Brooklyn')).toEqual({ baseAddress: '12 Main St, Brooklyn', baseLatitude: null, baseLongitude: null })
    expect(parseBase('  ')).toEqual({ baseAddress: null, baseLatitude: null, baseLongitude: null })
  })

  it('say in a line what they keep to', () => {
    expect(describeFilters(noFilters)).toBe('No filters: anywhere in the location area.')
    expect(hasFilters(noFilters)).toBe(false)
    const set = { ...noFilters, baseAddress: 'Bedford Ave', radiusKm: 3, maxBudget: 1500, excludedTypes: ['church', 'nightclub'], includePrivate: false }
    expect(describeFilters(set)).toBe('Within 3 km of Bedford Ave · up to 1,500 a day · no church, nightclub · no private property')
    expect(hasFilters(set)).toBe(true)
    expect(describeFilters({ ...noFilters, maxBudget: 900 })).toBe('Up to 900 a day')
  })
})
