import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { LibraryVenue } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const saved = (overrides: Partial<LibraryVenue> = {}): LibraryVenue => ({
  id: 'v1',
  sourceLocationId: 'l1',
  name: 'Moonlight Diner',
  address: '12 Main St, Brooklyn',
  latitude: null,
  longitude: null,
  sourceUrl: 'https://diner.example/',
  imageUrl: null,
  bookingFriction: 'COMMERCIAL',
  frictionNote: null,
  footprintWarnings: [],
  contactName: 'Sal',
  contactEmail: null,
  contactPhone: '555 0100',
  tags: ['diner'],
  notes: 'Owner loves film crews',
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
  ...overrides,
})

describe('my locations', () => {
  it('lists the library, searches it, filters it by tag and tags a venue', async () => {
    let venue = saved()
    const bar = saved({ id: 'v2', name: 'Neon Bar', tags: ['night'], notes: null })
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/library?page=0&size=24': () => json(pageOf([bar, venue])),
      'GET /api/library?q=film&page=0&size=24': () => json(pageOf([venue])),
      'GET /api/library?tag=night&page=0&size=24': () => json(pageOf([bar])),
      'GET /api/library/tags': () => json([{ tag: 'night', venues: 1 }, { tag: 'diner', venues: 1 }]),
      'PUT /api/library/v1': (req) => {
        venue = { ...venue, ...(req.body as object) }
        return json(venue)
      },
    })
    renderApp('/library')
    const user = await logIn()

    const list = await screen.findByRole('list', { name: 'Your locations' })
    expect(within(list).getAllByRole('heading').map((h) => h.textContent)).toEqual(['Neon Bar', 'Moonlight Diner'])
    expect(screen.getByRole('link', { name: 'My locations' })).toHaveAttribute('aria-current', 'page')

    await user.click(screen.getByRole('button', { name: /^night/ }))
    await vi.waitFor(() => expect(within(screen.getByRole('list', { name: 'Your locations' })).getAllByRole('heading')).toHaveLength(1))
    await user.click(screen.getByRole('button', { name: 'All' }))

    await user.type(screen.getByLabelText('Search your locations'), 'film')
    await vi.waitFor(() => expect(requests.some((r) => r.path === '/api/library?q=film&page=0&size=24')).toBe(true))

    await user.click(await screen.findByRole('button', { name: 'Tag and note Moonlight Diner' }))
    await user.type(screen.getByLabelText('Add a tag'), 'neon{Enter}')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await vi.waitFor(() => expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ name: 'Moonlight Diner', tags: ['diner', 'neon'], notes: 'Owner loves film crews' }))
  })

  it('says how to fill an empty library', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/library?page=0&size=24': () => json(pageOf([])),
      'GET /api/library/tags': () => json([]),
    })
    renderApp('/library')
    await logIn()

    expect(await screen.findByText(/Your library is empty/)).toBeInTheDocument()
  })
})

describe('the library from a venue and a scene', () => {
  it('keeps a venue from its page', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/locations/l1': () => json(location()),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
      'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
      'POST /api/library': () => json(saved(), 201),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Save to my library' }))
    expect(await screen.findByRole('link', { name: 'your library' })).toHaveAttribute('href', '/library')
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ locationId: 'l1' })
  })

  it('copies a library venue into a scene from the add-venue page', async () => {
    const copy = location({ id: 'l9', name: 'Moonlight Diner', sourceProvider: 'library', fitScore: null })
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/library?page=0&size=24': () => json(pageOf([saved()])),
      'POST /api/scenes/s1/locations/from-library': () => json(copy, 201),
      'GET /api/locations/l9': () => json(copy),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/locations/l9/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
      'GET /api/locations/l9/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    })
    const { router } = renderApp('/scenes/s1/locations/new')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Add from your library' }))
    await user.click(await screen.findByRole('button', { name: 'Add Moonlight Diner to this scene' }))

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/locations/l9'))
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ libraryVenueId: 'v1' })
  })
})
