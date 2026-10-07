import { describe, expect, it } from 'vitest'
import type { Alert } from '../api/types'
import { formatDate } from './format'
import { alertWords } from './alerts'

const weather = (payload: Record<string, unknown>): Alert => ({
  id: 'a1',
  kind: 'WEATHER',
  projectId: 'p1',
  projectTitle: 'Night Shift',
  sceneId: 's1',
  locationId: 'l1',
  draftId: null,
  payload: { scene: 'Rooftop chase', venue: 'Skyline Rooftop', day: '2026-10-12', rainThreshold: 60, windThreshold: 40, covers: [], ...payload },
  read: false,
  createdAt: '2026-10-11T06:23:00Z',
})

describe('alert words', () => {
  it('say what the weather threatens, against the thresholds, and offer the cover sets', () => {
    const rain = alertWords(weather({ reasons: ['RAIN'], rainChance: 75, covers: [{ locationId: 'l2', name: 'Dock Street Warehouse', trigger: 'if rain > 60%' }] }))
    expect(rain.title).toBe(`Rain likely at Skyline Rooftop on ${formatDate('2026-10-12')}`)
    expect(rain.detail).toEqual(['75% chance of rain (alert from 60%).', 'Switch to the cover set? Dock Street Warehouse (if rain > 60%).'])
    expect(rain.to).toBe('/scenes/s1')

    const both = alertWords(weather({ reasons: ['RAIN', 'WIND'], rainChance: 80, windKmh: 45, gustKmh: 70 }))
    expect(both.title).toMatch(/^Rain and strong wind at Skyline Rooftop/)
    expect(both.detail).toEqual([
      '80% chance of rain (alert from 60%), wind 45 km/h, gusts 70 (alert from 40 km/h).',
      'Rooftop chase has no cover set.',
    ])
    expect(alertWords(weather({ reasons: ['WIND'], windKmh: 52, gustKmh: null })).detail[0]).toBe('Wind 52 km/h (alert from 40 km/h).')
    expect(alertWords(weather({ reasons: ['RAIN'], rainChance: null, rainMm: 7.5 })).detail[0]).toBe('7.5 mm of rain forecast.')
  })

  it('lead a follow-up to the venue’s outreach', () => {
    const words = alertWords({
      ...weather({}),
      kind: 'FOLLOW_UP',
      draftId: 'd1',
      payload: { venue: 'The Sky Bar', scene: 'Rooftop', subject: 'Location enquiry', sentAt: '2026-10-01T09:30:00+00:00' },
    })
    expect(words.title).toBe('Follow up with The Sky Bar')
    expect(words.detail).toEqual([`“Location enquiry” sent ${formatDate('2026-10-01')}, no reply yet.`])
    expect(words.to).toBe('/locations/l1?tab=outreach')
  })
})
