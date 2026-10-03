import { describe, expect, it } from 'vitest'
import { bookingText, dayCount, timeWindow } from './availability'
import { formatDate } from './format'

describe('availability words', () => {
  it('say how far a booking has got and when a hold lapses', () => {
    expect(bookingText({ state: 'HELD', holdExpiresOn: '2026-10-28' })).toBe(`Held until ${formatDate('2026-10-28')}`)
    expect(bookingText({ state: 'CONFIRMED', holdExpiresOn: null })).toBe('Confirmed')
  })

  it('give a scene’s times as a window, or what there is of one', () => {
    expect(timeWindow('07:30:00', '16:00:00')).toBe('07:30–16:00')
    expect(timeWindow('07:30:00', null)).toBe('Call 07:30')
    expect(timeWindow(null, '02:00')).toBe('Wrap 02:00')
    expect(timeWindow(null, null)).toBeNull()
  })

  it('count the days of a run', () => {
    expect(dayCount('2026-11-02', '2026-11-02')).toBe(1)
    expect(dayCount('2026-10-30', '2026-11-02')).toBe(4)
    expect(dayCount('2026-11-02', '2026-11-01')).toBe(0)
  })
})
