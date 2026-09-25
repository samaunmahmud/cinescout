import { describe, expect, it } from 'vitest'
import { formatDaylight, formatDistance, localTime, temperatureRange, timeWindow } from './logisticsFormat'

describe('localTime', () => {
  it("shows the venue's clock, whatever the viewer's time zone", () => {
    expect(localTime('2026-09-28T06:49:00-04:00', '2026-09-28')).toBe('06:49')
    expect(localTime('2026-06-21T21:58:00+09:30', '2026-06-21')).toBe('21:58')
  })

  it('marks times that spill into a neighbouring day', () => {
    expect(localTime('2026-09-29T00:00:00-04:00', '2026-09-28')).toBe('24:00')
    expect(localTime('2026-09-29T01:15:00-04:00', '2026-09-28')).toBe('01:15 +1d')
    expect(localTime('2026-09-27T23:50:00-04:00', '2026-09-28')).toBe('23:50 -1d')
  })
})

describe('timeWindow', () => {
  it('joins start and end with an en dash', () => {
    expect(timeWindow({ start: '2026-09-28T19:10:00-04:00', end: '2026-09-29T00:00:00-04:00' }, '2026-09-28')).toBe('19:10–24:00')
  })
})

describe('formatDaylight', () => {
  it('reads as hours and minutes', () => {
    expect(formatDaylight(713)).toBe('11 h 53 min')
    expect(formatDaylight(1440)).toBe('24 h')
    expect(formatDaylight(45)).toBe('45 min')
    expect(formatDaylight(0)).toBe('0 min')
  })
})

describe('formatDistance', () => {
  it('uses metres below a kilometre', () => {
    expect(formatDistance(240)).toBe('240 m')
    expect(formatDistance(1900)).toBe('1.9 km')
    expect(formatDistance(2000)).toBe('2 km')
  })
})

describe('temperatureRange', () => {
  it('rounds and collapses a range', () => {
    expect(temperatureRange(14.3, 16.4)).toBe('14–16 °C')
    expect(temperatureRange(15.6, 16.4)).toBe('16 °C')
    expect(temperatureRange(null, 9.5)).toBe('10 °C')
    expect(temperatureRange(-2.4, null)).toBe('-2 °C')
    expect(temperatureRange(null, null)).toBeNull()
  })
})
