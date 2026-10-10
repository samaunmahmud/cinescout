import { describe, expect, it } from 'vitest'
import { scheduled } from '../test/fixtures'
import { movedTo, nextDay, pagesLabel, pagesText, stripColour, stripKind, stripLabel } from './stripboard'

const kind = (title: string, settingType: string | null = null, timeOfDay: string | null = null) => stripKind({ title, settingType, timeOfDay })

describe('stripKind', () => {
  it('reads inside or out, and day or night, from the scene heading', () => {
    expect(kind('INT. DINER - NIGHT')).toEqual({ place: 'INT', light: 'NIGHT' })
    expect(kind('EXT. ROOFTOP - DAWN')).toEqual({ place: 'EXT', light: 'DAY' })
    expect(kind('INT./EXT. CAR - MOVING - DAY')).toEqual({ place: 'INT/EXT', light: 'DAY' })
    expect(kind('I/E VAN – EVENING')).toEqual({ place: 'INT/EXT', light: 'NIGHT' })
    expect(kind('EXT. FERRY TERMINAL - PRE-DAWN')).toEqual({ place: 'EXT', light: 'NIGHT' })
    expect(kind('EST. HARBOUR')).toEqual({ place: 'EXT', light: null })
    expect(kind('12 int. kitchen - continuous')).toEqual({ place: 'INT', light: null })
  })

  it('falls back on the AI reading of the scene, which wins for the time of day', () => {
    expect(kind('Montage', 'Open-air market', 'Late afternoon')).toEqual({ place: 'EXT', light: 'DAY' })
    expect(kind('The chase', 'Warehouse interior', null)).toEqual({ place: 'INT', light: null })
    expect(kind('INT. FLAT - DAY', null, 'Night, lights off')).toEqual({ place: 'INT', light: 'NIGHT' })
    expect(kind('Montage')).toEqual({ place: null, light: null })
    // A title's dash is no time of day when it is not a heading.
    expect(kind('Interiors - Day one', null, null)).toEqual({ place: null, light: null })
  })
})

describe('strip colours and labels', () => {
  it('use the board colours, grey when unsure', () => {
    expect(stripColour({ place: 'INT', light: 'DAY' })).toBe('bg-strip-int-day')
    expect(stripColour({ place: 'EXT', light: 'DAY' })).toBe('bg-strip-ext-day')
    expect(stripColour({ place: 'INT', light: 'NIGHT' })).toBe('bg-strip-int-night')
    expect(stripColour({ place: 'INT/EXT', light: 'NIGHT' })).toBe('bg-strip-ext-night')
    expect(stripColour({ place: 'EXT', light: null })).toBe('bg-strip-unknown')
    expect(stripLabel({ place: 'EXT', light: 'NIGHT' })).toBe('EXT. NIGHT')
    expect(stripLabel({ place: null, light: 'DAY' })).toBe('DAY')
    expect(stripLabel({ place: null, light: null })).toBeNull()
  })
})

describe('movedTo', () => {
  it('keeps a scene’s length and times when it moves to another day', () => {
    const rooftop = scheduled({ shootDateStart: '2026-10-12', shootDateEnd: '2026-10-14', callTime: '06:00:00', wrapTime: '14:30:00' })
    expect(movedTo(rooftop, '2026-10-30')).toEqual({ shootDateStart: '2026-10-30', shootDateEnd: '2026-11-01', callTime: '06:00', wrapTime: '14:30' })
    expect(movedTo(scheduled({ shootDateEnd: null }), '2026-10-20')).toEqual({ shootDateStart: '2026-10-20', shootDateEnd: null, callTime: null, wrapTime: null })
  })

  it('takes the dates off when the scene leaves the board', () => {
    expect(movedTo(scheduled({ callTime: '07:30:00' }), null)).toEqual({ shootDateStart: null, shootDateEnd: null, callTime: '07:30', wrapTime: null })
  })

  it('counts on across months', () => {
    expect(nextDay('2026-10-31')).toBe('2026-11-01')
  })
})

describe('page counts', () => {
  it('are written in eighths, left unreduced', () => {
    expect(pagesText(3)).toBe('3/8')
    expect(pagesText(4)).toBe('4/8')
    expect(pagesText(8)).toBe('1')
    expect(pagesText(17)).toBe('2 1/8')
    expect(pagesLabel(3)).toBe('3/8 page')
    expect(pagesLabel(8)).toBe('1 page')
    expect(pagesLabel(12)).toBe('1 4/8 pages')
  })
})
