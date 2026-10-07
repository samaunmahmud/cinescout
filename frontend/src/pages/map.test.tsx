import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Location } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, project, scene, locationVideos, pageOf } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

// Leaflet is loaded on demand, and the first import in a test run takes a moment.
const MAP_LOAD = { timeout: 5000 }

const diner = location({ latitude: 40.6745, longitude: -73.9633 })
const bistro = location({ id: 'l2', name: 'Corner Bistro', fitScore: null, bookingFriction: null, latitude: 40.738, longitude: -74.0036 })
const unplaced = location({ id: 'l3', name: 'Sal’s Pizza', fitScore: 40 })

function sceneServer(locations: Location[]) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
    'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf(locations)),
  })
}

describe('the scene map', () => {
  it('pins the venues that have a position and counts the ones that do not', async () => {
    sceneServer([diner, bistro, unplaced])
    renderApp('/scenes/s1')
    await logIn()

    const map = await screen.findByRole('region', { name: 'Map of candidate locations' }, MAP_LOAD)
    expect(within(map).getAllByRole('button', { name: /fit|by hand/ }).map((pin) => pin.getAttribute('title'))).toEqual([
      'Tom’s Diner, fit 82',
      'Corner Bistro, added by hand',
    ])
    expect(screen.getByText(/1 of 3 venues are not on the map yet/)).toBeInTheDocument()
    expect(within(map).getByRole('link', { name: 'OpenStreetMap' })).toHaveAttribute('href', 'https://www.openstreetmap.org/copyright')
  })

  it('opens a venue from its pin, even when it is the only one', async () => {
    sceneServer([diner])
    renderApp('/scenes/s1')
    await logIn()

    const map = await screen.findByRole('region', { name: 'Map of candidate locations' }, MAP_LOAD)
    fireEvent.click(within(map).getByRole('button', { name: 'Tom’s Diner, fit 82' }))

    expect(await within(map).findByRole('link', { name: 'Tom’s Diner' })).toHaveAttribute('href', '/locations/l1')
    expect(map).toHaveTextContent('Fit 82 out of 100')
    expect(screen.queryByText(/not on the map yet/)).toBeNull()
  })

  it('is left out while no venue has a position, saying how they get one', async () => {
    sceneServer([unplaced])
    renderApp('/scenes/s1')
    await logIn()

    expect(await screen.findByText(/None of these venues is on a map yet/)).toHaveTextContent('when their logistics are worked out')
    expect(screen.queryByRole('region', { name: /Map/ })).toBeNull()
  })
})

describe('the location map', () => {
  function locationServer(current: Location, extra: Parameters<typeof fakeServer>[0] = {}) {
    return fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/locations/l1': () => json(current),
      'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
      'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
      'GET /api/locations/l1/availability?page=0&size=100': () => json(pageOf([], { size: 100 })),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/locations/l1/outreach-drafts?page=0&size=24': () => json(pageOf([])),
      'GET /api/locations/l1/agreements?page=0&size=50': () => json(pageOf([], { size: 50 })),
      'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
      ...extra,
    })
  }

  it('shows the venue’s pin', async () => {
    locationServer(diner)
    renderApp('/locations/l1')
    await logIn()

    const map = await screen.findByRole('region', { name: 'Map of Tom’s Diner' }, MAP_LOAD)
    expect(within(map).getByRole('button', { name: 'Tom’s Diner, fit 82' })).toBeInTheDocument()
  })

  it('places the pin where the map is clicked', async () => {
    // jsdom lays nothing out; give the map a size so a click lands east of its centre.
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(400)
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(300)
    const { requests } = locationServer(diner, { 'PUT /api/locations/l1/coordinates': () => json(diner) })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Move pin' }))
    const map = await screen.findByRole('region', { name: 'Map: click to place the pin' }, MAP_LOAD)
    fireEvent.click(map.querySelector('.leaflet-container')!, { clientX: 300, clientY: 150 })

    const field = screen.getByLabelText('Coordinates')
    const [latitude, longitude] = (field as HTMLInputElement).value.split(', ').map(Number)
    expect(latitude).toBeCloseTo(40.6745, 4)
    expect(longitude).toBeGreaterThan(-73.9633)
    expect(longitude).toBeLessThan(-73.95)

    await user.click(screen.getByRole('button', { name: 'Save position' }))
    expect(requests.at(-1)?.body).toEqual({ latitude, longitude })
  })

  it('keeps the view where it is when a spot is first picked on the world map', async () => {
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(400)
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(300)
    locationServer({ ...unplaced, id: 'l1' })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Set coordinates' }))
    const map = await screen.findByRole('region', { name: 'Map: click to place the pin' }, MAP_LOAD)
    const zoomLevels = () => new Set([...map.querySelectorAll('img.leaflet-tile')].map((tile) => tile.getAttribute('src')?.split('/')[3]))
    expect(zoomLevels()).toEqual(new Set(['2']))

    fireEvent.click(map.querySelector('.leaflet-container')!, { clientX: 300, clientY: 150 })

    expect(screen.getByLabelText('Coordinates')).not.toHaveValue('')
    expect(within(map).getByRole('button', { name: 'Sal’s Pizza' })).toBeInTheDocument()
    expect(zoomLevels()).toEqual(new Set(['2']))
  })

  it('is left out while the venue has no position', async () => {
    locationServer({ ...unplaced, id: 'l1' })
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByText(/^Not set\./)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /Map/ })).toBeNull()
  })
})
