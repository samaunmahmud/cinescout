import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Location, ProjectRole } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function standingAt(latitude: number, longitude: number) {
  vi.stubGlobal('navigator', {
    ...navigator,
    geolocation: { getCurrentPosition: (ok: PositionCallback) => ok({ coords: { latitude, longitude } } as GeolocationPosition) },
  })
}

function server(role: ProjectRole, current: Location) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(current),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project({ role })),
    'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    'PUT /api/locations/l1/coordinates': (req) => json({ ...current, ...(req.body as object) }),
  })
}

beforeEach(() => vi.stubGlobal('isSecureContext', true))
afterEach(() => vi.unstubAllGlobals())

describe('recce mode', () => {
  it('pins an unplaced venue where the scout stands in one tap, with the camera and the checklist below', async () => {
    standingAt(40.67447, -73.963316)
    const { requests } = server('EDITOR', location({ latitude: null, longitude: null }))
    renderApp('/locations/l1/recce')
    const user = await logIn()

    expect(await screen.findByRole('heading', { name: 'Pin it here' })).toBeInTheDocument()
    expect(screen.getByLabelText('Take a photo')).toHaveAttribute('capture', 'environment')
    expect(screen.getByRole('heading', { name: 'Tech recce' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'I’m at the venue: pin it here' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Pinned at 40.67447, -73.963316.')
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ latitude: 40.67447, longitude: -73.963316 })
  })

  it('asks before moving a pin that is far from where the scout stands', async () => {
    standingAt(40.7, -73.9)
    const { requests } = server('EDITOR', location({ latitude: 40.75, longitude: -73.9 }))
    renderApp('/locations/l1/recce')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'I’m at the venue: pin it here' }))
    expect(await screen.findByRole('alertdialog', { name: 'Move the pin?' })).toHaveTextContent('The pin is 5.6 km from where you are.')
    expect(requests.some((r) => r.method === 'PUT')).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Move it here' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Pinned at 40.7, -73.9.')
  })

  it('opens from the venue page, and is not offered to a viewer', async () => {
    server('VIEWER', location())
    renderApp('/locations/l1/recce')
    await logIn()

    expect(await screen.findByRole('note')).toHaveTextContent('You can look at this venue but not record a recce')
    expect(screen.queryByRole('button', { name: /pin it here/ })).not.toBeInTheDocument()
  })
})
