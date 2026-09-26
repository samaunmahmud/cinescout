import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Location } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, logIn, project, scene, logisticsReport, locationVideos } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function serverFor(current: Location, extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(current),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/locations/l1/outreach-drafts': () => json([]),
    'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
    ...extra,
  })
}

describe('a location', () => {
  it('shows its assessment, source and position', async () => {
    serverFor(location({ latitude: 40.67447, longitude: -73.963316 }))
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByRole('heading', { level: 1, name: 'Tom’s Diner' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Scene 12: INT. DINER - NIGHT' })).toHaveAttribute('href', '/scenes/s1')
    expect(screen.getByLabelText('Fit 82 out of 100')).toBeInTheDocument()
    const assessment = screen.getByRole('region', { name: 'Assessment' })
    expect(assessment).toHaveTextContent('Neon sign and red booths match the mood.')
    expect(assessment).toHaveTextContent('A classic Brooklyn diner since 1936.')
    expect(within(assessment).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('link', { name: /View on OpenStreetMap/ })).toHaveAttribute(
      'href',
      'https://www.openstreetmap.org/?mlat=40.67447&mlon=-73.963316#map=17/40.67447/-73.963316',
    )
    expect(screen.getByText(/40\.67447, -73\.963316/)).toBeInTheDocument()
  })

  it('has no assessment when it was added by hand', async () => {
    serverFor(location({ fitScore: null, fitReason: null, bookingFriction: null, frictionNote: null, footprintWarnings: [], sourceExcerpt: null }))
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByText('Added by hand')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Assessment' })).toBeNull()
    expect(screen.getByText(/Not set\. Logistics look the venue up from its address/)).toBeInTheDocument()
  })

  it('saves notes with its status, a blank note as null', async () => {
    const { requests } = serverFor(location({ status: 'SHORTLISTED', notes: 'Call after 3pm.' }), {
      'PUT /api/locations/l1': (req) => json(location({ status: 'SHORTLISTED', ...(req.body as object) })),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    const notes = await screen.findByLabelText('Your notes on this venue')
    const save = screen.getByRole('button', { name: 'Save notes' })
    expect(save).toBeDisabled()

    await user.clear(notes)
    await user.type(notes, '  ')
    await user.click(save)

    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ status: 'SHORTLISTED', notes: null })
    await expect.poll(() => screen.getByRole('button', { name: 'Save notes' })).toBeDisabled()
  })

  it('can have its pin set from coordinates copied from a map', async () => {
    const { requests } = serverFor(location(), {
      'PUT /api/locations/l1/coordinates': (req) => json(location({ ...(req.body as object) })),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Set coordinates' }))
    const field = screen.getByLabelText('Coordinates')
    await user.type(field, '40.6744')
    expect(screen.getByText(/Enter latitude and longitude/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save position' })).toBeDisabled()

    await user.clear(field)
    await user.type(field, '40.67447012, -73.96331649')
    await user.click(screen.getByRole('button', { name: 'Save position' }))

    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ latitude: 40.67447, longitude: -73.963316 })
    expect(await screen.findByRole('link', { name: /View on OpenStreetMap/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Move pin' })).toBeInTheDocument()
  })

  it('warns that moving the pin discards the logistics report', async () => {
    serverFor(location({ latitude: 40.67447, longitude: -73.963316, logistics: logisticsReport() }))
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Move pin' }))

    expect(screen.getByLabelText('Coordinates')).toHaveValue('40.67447, -73.963316')
    expect(screen.getByRole('note')).toHaveTextContent('Moving the pin discards the logistics report')
  })

  it('is removed after confirmation, returning to its scene', async () => {
    const { requests } = serverFor(location(), {
      'DELETE /api/locations/l1': () => new Response(null, { status: 204 }),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/scenes/s1/locations': () => json([]),
    })
    const { router } = renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Remove' }))
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Remove location' }))

    expect(await screen.findByRole('heading', { name: 'Scene 12: INT. DINER - NIGHT' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/scenes/s1')
    expect(requests.filter((r) => r.method === 'DELETE')).toHaveLength(1)
  })

  it("is not found when it is another user's", async () => {
    fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/locations/l1': () => problem(404, 'Not found', 'Location not found') })
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Not found' })).toBeInTheDocument()
  })
})

describe('adding a venue by hand', () => {
  function serverForNew(extra: Parameters<typeof fakeServer>[0] = {}) {
    return fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/scenes/s1': () => json(scene()),
      ...extra,
    })
  }

  it('starts from the scene page and opens the new venue', async () => {
    const created = location({ id: 'l9', name: 'Corner Bistro', fitScore: null, sourceUrl: 'https://bistro.example/', latitude: 40.738, longitude: -74.004 })
    const { requests } = serverForNew({
      'GET /api/projects/p1': () => json(project()),
      'GET /api/scenes/s1/locations': () => json([]),
      'POST /api/scenes/s1/locations': () => json(created, 201),
      'GET /api/locations/l9/outreach-drafts': () => json([]),
      'GET /api/locations/l9/videos': () => json(locationVideos({ videos: [] })),
    })
    const { router } = renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('link', { name: 'Add venue' }))
    await user.type(await screen.findByLabelText('Name'), ' Corner Bistro ')
    await user.type(screen.getByLabelText('Website'), 'https://bistro.example/')
    await user.type(screen.getByLabelText('Coordinates'), '40.738 -74.004')
    await user.click(screen.getByRole('button', { name: 'Add venue' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Corner Bistro' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/locations/l9')
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      name: 'Corner Bistro',
      address: null,
      latitude: 40.738,
      longitude: -74.004,
      sourceUrl: 'https://bistro.example/',
      notes: null,
    })
  })

  it('catches a web address that is not http(s) before sending anything', async () => {
    const { requests } = serverForNew()
    renderApp('/scenes/s1/locations/new')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Name'), 'Bistro')
    await user.type(screen.getByLabelText('Website'), 'javascript:alert(1)')

    expect(screen.getByText(/starting with http:\/\/ or https:\/\//)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add venue' })).toBeDisabled()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)
  })

  it('shows a duplicate as the server explains it', async () => {
    serverForNew({
      'POST /api/scenes/s1/locations': () => problem(409, 'Conflict', 'That page is already saved for this scene'),
    })
    renderApp('/scenes/s1/locations/new')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Name'), 'Bistro')
    await user.type(screen.getByLabelText('Website'), 'https://bistro.example/')
    await user.click(screen.getByRole('button', { name: 'Add venue' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That page is already saved for this scene')
  })
})

describe('adding a venue by hand, server-side checks', () => {
  it('puts the coordinate errors next to the coordinates field', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/scenes/s1': () => json(scene()),
      'POST /api/scenes/s1/locations': () =>
        problem(400, 'Validation failed', 'The request is invalid', {
          errors: [{ field: 'coordinates', message: 'latitude and longitude must be given together' }],
        }),
    })
    renderApp('/scenes/s1/locations/new')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Name'), 'Bistro')
    await user.click(screen.getByRole('button', { name: 'Add venue' }))

    expect(await screen.findByText('latitude and longitude must be given together')).toBeInTheDocument()
    expect(screen.getByLabelText('Coordinates')).toHaveAttribute('aria-invalid', 'true')
  })
})
