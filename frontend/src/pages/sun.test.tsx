import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { LogisticsReport, Schedule, SolarDay, SunPosition } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, locationVideos, logIn, logisticsReport, pageOf, project, scene, schedule, scheduled } from '../test/fixtures'
import { formatDate } from '../lib/format'
import { renderApp } from '../test/renderApp'

const path: SunPosition[] = [
  { at: '2026-09-28T16:00:00-04:00', azimuth: 225, elevation: 18, compass: 'SW', text: 'Sun from SW (225°), 18° high at 16:00' },
  { at: '2026-09-28T17:30:00-04:00', azimuth: 248, elevation: 6, compass: 'W', text: 'Sun from W (248°), 6° high at 17:30' },
  { at: '2026-09-28T19:00:00-04:00', azimuth: 270, elevation: -3, compass: 'W', text: 'Sun 3° below the horizon (W, 270°) at 19:00' },
]

function withSun(): LogisticsReport {
  const base = logisticsReport()
  const first: SolarDay = { ...base.solar.days[0], sunPath: path }
  const second: SolarDay = { ...first, date: '2026-09-29', sunPath: [{ ...path[0], at: '2026-09-29T16:00:00-04:00', text: 'Sun from SW (226°), 17° high at 16:00' }] }
  return { ...base, solar: { ...base.solar, days: [first, second] } }
}

function venueServer(extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location({ latitude: 40.67, longitude: -73.96, logistics: withSun() })),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    'GET /api/locations/l1/availability?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
    ...extra,
  })
}

describe('the sun during the scene', () => {
  it('is drawn as a compass over the venue map, with each position in words, a shoot day at a time', async () => {
    venueServer()
    renderApp('/locations/l1')
    const user = await logIn()

    const figure = (await screen.findByText('Sun during the scene')).closest('figure') as HTMLElement
    const lines = within(figure).getAllByRole('listitem')
    expect(lines.map((line) => line.textContent)).toEqual([
      'Start: Sun from SW (225°), 18° high at 16:00',
      'Middle: Sun from W (248°), 6° high at 17:30',
      'End: Sun 3° below the horizon (W, 270°) at 19:00',
    ])
    expect(figure.querySelectorAll('svg line')).toHaveLength(3)

    await user.selectOptions(within(figure).getByLabelText('Shoot day'), formatDate('2026-09-29'))
    expect(within(figure).getAllByRole('listitem').map((line) => line.textContent)).toEqual(['Sun from SW (226°), 17° high at 16:00'])
  })

  it('has its own column in the light table of the logistics report', async () => {
    venueServer()
    renderApp('/locations/l1?tab=logistics')
    await logIn()

    expect(await screen.findByRole('columnheader', { name: 'Sun during the scene' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: /Sun from SW \(225°\), 18° high at 16:00/ })).toBeInTheDocument()
  })

  it('is on the call sheet, and an old report without it is offered a refresh once the scene has a call time', async () => {
    const venue = scheduled().venues[0]
    const timed: Schedule = {
      ...schedule,
      days: [
        {
          date: '2026-10-12',
          scenes: [
            scheduled({
              callTime: '16:00:00',
              wrapTime: '19:00:00',
              venues: [{ ...venue, day: { ...venue.day!, sun: [{ time: '16:00', azimuth: 225, elevation: 18, compass: 'SW', text: 'Sun from SW (225°), 18° high at 16:00' }] } }],
            }),
            scheduled({ id: 's9', title: 'EXT. YARD - DAY', callTime: '08:00:00' }),
          ],
        },
      ],
      unscheduled: [],
    }
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
      'GET /api/projects/p1/schedule': () => json(timed),
      'GET /api/projects/p1/call-sheet-link': () => problem(404, 'Not found', 'Not shared'),
    })
    const { unmount } = renderApp('/projects/p1/call-sheet')
    await logIn()
    expect(await screen.findByText('Sun from SW (225°), 18° high at 16:00')).toBeInTheDocument()
    expect(screen.getAllByText('16:00–19:00').length).toBeGreaterThan(0)
    unmount()

    renderApp('/projects/p1?tab=schedule')
    // The yard scene's report has no sun path from its 08:00 call yet.
    expect(await screen.findByRole('button', { name: 'Get light and weather' })).toBeInTheDocument()
  })
})
