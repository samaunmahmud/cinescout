import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentPosition } from '../lib/currentPosition'
import { noFilters } from '../lib/scoutFilters'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

/** A browser that is at `spot`, or refuses with a GeolocationPositionError code. */
function browserAt(spot: { latitude: number; longitude: number } | { refuse: 1 | 2 | 3 }) {
  const geolocation = {
    getCurrentPosition: vi.fn((ok: PositionCallback, fail: PositionErrorCallback) =>
      'refuse' in spot ? fail({ code: spot.refuse } as GeolocationPositionError) : ok({ coords: spot } as GeolocationPosition),
    ),
  }
  vi.stubGlobal('navigator', { ...navigator, geolocation })
  return geolocation
}

beforeEach(() => {
  vi.stubGlobal('isSecureContext', true)
})
afterEach(() => {
  vi.unstubAllGlobals()
})

describe('use my current location', () => {
  it('names the place to fill a new production’s location area', async () => {
    browserAt({ latitude: 51.52600049, longitude: -0.078 })
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([project()])),
      'GET /api/places/here?lat=51.526&lng=-0.078': () =>
        json({ name: 'Shoreditch, London, United Kingdom', latitude: 51.526, longitude: -0.078, attribution: 'Nominatim' }),
    })
    renderApp('/projects?new')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Use where I am now' }))

    expect(await screen.findByDisplayValue('Shoreditch, London, United Kingdom')).toBe(screen.getByLabelText('Location area'))
    expect(requests.some((r) => r.path === '/api/places/here?lat=51.526&lng=-0.078')).toBe(true)
  })

  it('sets the scouting base point to where you are, and says plainly when the browser will not tell', async () => {
    const geolocation = browserAt({ refuse: 1 })
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/members': () => json({ members: [], invites: [] }),
      'GET /api/projects/p1/scout-filters': () => json(noFilters),
    })
    renderApp('/projects/p1/settings?tab=scouting')
    const user = await logIn()

    const button = await screen.findByRole('button', { name: 'Search around where I am' })
    await user.click(button)
    expect(await screen.findByRole('alert')).toHaveTextContent('Location is blocked for this site')

    geolocation.getCurrentPosition.mockImplementation((ok: PositionCallback) => ok({ coords: { latitude: 40.6745, longitude: -73.9633 } } as GeolocationPosition))
    await user.click(button)
    expect(screen.getByLabelText('Base point')).toHaveValue('40.6745, -73.9633')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('currentPosition', () => {
  it('explains a browser without location, an insecure page and a timeout', async () => {
    await expect(currentPosition(undefined)).rejects.toThrow('cannot share its location')
    const timesOut = { getCurrentPosition: (_: PositionCallback, fail: PositionErrorCallback) => fail({ code: 3 } as GeolocationPositionError) }
    await expect(currentPosition(timesOut as unknown as Geolocation)).rejects.toThrow('took too long')
    vi.stubGlobal('isSecureContext', false)
    await expect(currentPosition(timesOut as unknown as Geolocation)).rejects.toThrow('secure (https)')
  })
})
