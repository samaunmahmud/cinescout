import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Location } from '../api/types'
import { formatDate } from '../lib/format'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, logIn, logisticsReport, scene, locationVideos, pageOf } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function serverFor(current: () => Location, extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(current()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/locations/l1/outreach-drafts?page=0&size=24': () => json(pageOf([])),
    'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
    ...extra,
  })
}

describe("a location's logistics", () => {
  it('are worked out on request, keeping the coordinates the server looked up', async () => {
    const report = logisticsReport()
    let current = location()
    const { requests } = serverFor(() => current, {
      'POST /api/locations/l1/logistics': () => {
        current = location({ logistics: report, latitude: 40.67447, longitude: -73.963316 })
        return json(report)
      },
      'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf([current])),
    })
    renderApp('/locations/l1?tab=logistics')
    const user = await logIn()

    expect(await screen.findByText(/Not worked out yet/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Work out logistics' }))

    const light = await screen.findByRole('region', { name: 'Light' })
    expect(requests.filter((r) => r.method === 'POST').map((r) => r.path)).toEqual(['/api/locations/l1/logistics'])
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument()
    expect(screen.getByText(/times are local to the venue \(America\/New_York\)/)).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'About this report' })).toHaveTextContent('The coordinates were looked up')
    // The header's position tile, on every tab, picks up the coordinates the server looked up.
    expect(await screen.findByText('On the map')).toBeInTheDocument()

    // The venue's own clock, including the night window running to midnight.
    const [header, row] = within(light).getAllByRole('row')
    expect(within(header).getAllByRole('columnheader').map((c) => c.textContent)).toEqual([
      'Day', 'Sunrise', 'Sunset', 'Daylight', 'Golden hour', 'Blue hour', 'Night (scene)',
    ])
    expect([...row.children].map((c) => c.textContent)).toEqual([
      formatDate('2026-09-28'),
      '06:49',
      '18:43',
      '11 h 53 min',
      '06:33–07:26, 18:07–19:00',
      '06:22–06:33, 19:00–19:10',
      '00:00–06:22, 19:10–24:00',
    ])
    expect(light).toHaveTextContent('The scene is set at “Night”, read as night.')

    const weather = screen.getByRole('region', { name: 'Weather' })
    expect(weather).toHaveTextContent('Rain')
    expect(weather).toHaveTextContent('Forecast')
    expect(weather).toHaveTextContent('14–16 °C · 7 mm rain (89% chance) · Wind 27 km/h, gusts 54 km/h · 100% cloud')
    expect(weather).toHaveTextContent('Rain likely: plan cover for cast, crew and equipment')

    const surroundings = screen.getByRole('region', { name: 'Surroundings' })
    expect(surroundings).toHaveTextContent('Noise risk High')
    expect(within(surroundings).getByRole('list', { name: 'Noise sources' })).toHaveTextContent(
      'Major road: Atlantic Avenue · 120 m High' + 'Traffic noise all day: record sound early or late',
    )
    const services = within(surroundings).getByLabelText('Nearby services')
    expect(services).toHaveTextContent('HospitalInterfaith Medical Center · 1.9 km')
    expect(services).toHaveTextContent('ParkingUnnamed · 240 m')

    expect(screen.getByText('Weather data by Open-Meteo.com (CC BY 4.0)')).toBeInTheDocument()
  })

  it('are shown from the cache without being worked out again', async () => {
    const { requests } = serverFor(() => location({ logistics: logisticsReport() }))
    renderApp('/locations/l1?tab=logistics')
    await logIn()

    expect(await screen.findByRole('region', { name: 'Light' })).toBeInTheDocument()
    expect(screen.getByText(/^Worked out /)).toBeInTheDocument()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)
  })

  it('explain sections the providers could not fill, and the caveats of the dates used', async () => {
    const report = logisticsReport({
      shootWindow: { start: '2026-09-26', end: '2026-10-02', assumed: true, truncated: false },
      solar: { ...logisticsReport().solar, timeOfDay: 'interior, any time', sceneLight: null },
      weather: {
        status: 'PARTIAL',
        message: 'Some shoot days could not be looked up',
        days: [{ ...logisticsReport().weather.days[0], basis: 'PAST_YEAR', referenceDate: '2025-09-28', warnings: [] }],
      },
      environment: { ...logisticsReport().environment, status: 'UNAVAILABLE', message: 'The map service could not be reached; try again later', noiseRisk: null, noiseSources: [], nearbyServices: [] },
    })
    serverFor(() => location({ logistics: report }))
    renderApp('/locations/l1?tab=logistics')
    await logIn()

    expect(await screen.findByRole('list', { name: 'About this report' })).toHaveTextContent('The scene has no shoot dates yet')
    const light = screen.getByRole('region', { name: 'Light' })
    expect(light).toHaveTextContent('which names no natural light')
    expect(within(light).getAllByRole('columnheader')).toHaveLength(6)
    const weather = screen.getByRole('region', { name: 'Weather' })
    expect(weather).toHaveTextContent('Some shoot days could not be looked up')
    expect(weather).toHaveTextContent(`Too far ahead to forecast: as recorded on ${formatDate('2025-09-28')}`)
    const surroundings = screen.getByRole('region', { name: 'Surroundings' })
    expect(surroundings).toHaveTextContent('The map service could not be reached; try again later')
    expect(surroundings).not.toHaveTextContent('No known noise sources')
  })

  it('explain a venue that cannot be found on the map', async () => {
    serverFor(() => location(), {
      'POST /api/locations/l1/logistics': () =>
        problem(409, 'Conflict', 'The venue could not be found on the map; set its coordinates first'),
    })
    renderApp('/locations/l1?tab=logistics')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Work out logistics' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The venue could not be found on the map; set its coordinates first')
  })

  it('ask for a refresh when the saved report is in an unknown format', async () => {
    serverFor(() => location({ logistics: logisticsReport({ version: 2 }) }))
    renderApp('/locations/l1?tab=logistics')
    await logIn()

    expect(await screen.findByText(/format this page does not know/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Light' })).toBeNull()
  })
})
