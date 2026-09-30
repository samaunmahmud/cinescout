import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Location, Scene } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, logIn, project, scene, pageOf } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function serverFor(locations: Location[], extra: Parameters<typeof fakeServer>[0] = {}, current: Scene = scene()) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/scenes/s1': () => json(current),
    'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf(locations)),
    ...extra,
  })
}

describe("a scene's locations", () => {
  it('fill in the pictures of venues never looked up, one at a time, each once', async () => {
    const fresh = [
      location({ id: 'l1', imageCheckedAt: null }),
      location({ id: 'l2', name: 'Corner Bistro', imageCheckedAt: null }),
      location({ id: 'l3', name: 'Sal’s Pizza', imageCheckedAt: null }),
      location({ id: 'l4', name: 'By hand', sourceUrl: null, imageCheckedAt: null }),
    ]
    const { requests } = serverFor(fresh, {
      'POST /api/locations/l1/image': () => json({ ...fresh[0], imageUrl: 'https://cdn.example/diner.jpg', imageCheckedAt: '2026-09-30T10:00:00Z' }),
      'POST /api/locations/l2/image': () => json({ ...fresh[1], imageCheckedAt: '2026-09-30T10:00:00Z' }),
      'POST /api/locations/l3/image': () => json({ ...fresh[2], imageCheckedAt: '2026-09-30T10:00:00Z' }),
    })
    const { container } = renderApp('/scenes/s1')
    await logIn()

    await vi.waitFor(() => expect(container.querySelector('img[src="https://cdn.example/diner.jpg"]')).not.toBeNull())
    await vi.waitFor(() => expect(requests.filter((r) => r.path.endsWith('/image')).map((r) => r.path)).toEqual([
      '/api/locations/l1/image',
      '/api/locations/l2/image',
      '/api/locations/l3/image',
    ]))
  })

  it('show the assessment of each venue in the order the server gives', async () => {
    serverFor([
      location(),
      location({
        id: 'l2',
        name: 'Corner Bistro',
        fitScore: null,
        bookingFriction: null,
        fitReason: null,
        frictionNote: null,
        footprintWarnings: [],
        sourceUrl: 'javascript:alert(1)',
        notes: 'Owner is a friend of the producer.',
      }),
    ])
    renderApp('/scenes/s1')
    await logIn()

    const list = await screen.findByRole('list', { name: 'Candidate locations' })
    const [first, second] = within(list).getAllByRole('article')

    expect(within(first).getByRole('heading', { name: 'Tom’s Diner' })).toBeInTheDocument()
    expect(within(first).getByRole('link', { name: 'Tom’s Diner' })).toHaveAttribute('href', '/locations/l1')
    expect(within(first).getByLabelText('Fit 82 out of 100')).toBeInTheDocument()
    expect(first).toHaveTextContent('Business')
    expect(first).toHaveTextContent('Neon sign and red booths match the mood.')
    expect(first).toHaveTextContent('Needs the owner’s permission; closed Mondays.')
    expect(within(within(first).getByRole('list', { name: 'Warnings' })).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Narrow street: no room for a generator truck',
      'Subway noise every 10 minutes',
    ])
    expect(within(first).getByRole('link', { name: /toms-diner\.example/ })).toHaveAttribute('href', 'https://www.toms-diner.example/')
    expect(within(first).getByLabelText('Status of Tom’s Diner')).toHaveValue('SUGGESTED')

    // Added by hand: no assessment, and a source URL that is not http(s) is never rendered as a link.
    expect(second).toHaveTextContent('Added by hand')
    expect(second).toHaveTextContent('Owner is a friend of the producer.')
    expect(within(second).getAllByRole('link').map((a) => a.getAttribute('href'))).toEqual(['/locations/l2'])
    expect(within(second).queryByLabelText(/^Fit/)).toBeNull()
  })

  it('can be scouted: the run is summarised and the new venues and parsed scene appear', async () => {
    let saved: Location[] = []
    let current = scene()
    const { requests } = serverFor(
      [],
      {
        'GET /api/scenes/s1': () => json(current),
        'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf(saved)),
        'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([current])),
        'POST /api/scenes/s1/scout': () => {
          saved = [location()]
          current = scene({ parseStatus: 'PARSED', requirements: { settingType: 'Late-night diner', visualMood: null, lightingNeeds: null, timeOfDay: null, acousticSensitivity: null, estimatedCastAndCrewSize: null } })
          return json({ added: saved, alreadySaved: 2, unassessed: 1, notVenues: 0, unsuitable: 0 })
        },
      },
      scene(),
    )
    renderApp('/scenes/s1')
    const user = await logIn()

    expect(await screen.findByText(/No locations yet/)).toBeInTheDocument()
    expect(screen.getByText('Scouting in Brooklyn, New York')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Scout locations' }))

    expect(await screen.findByText('Found 1 new venue. 2 venues were already saved and left as they were. 1 venue could not be assessed and was left out.')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Tom’s Diner' })).toBeInTheDocument()
    expect(await screen.findByText('Requirements ready')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Scout again' })).toBeInTheDocument()
    expect(requests.filter((r) => r.method === 'POST').map((r) => r.path)).toEqual(['/api/scenes/s1/scout'])
  })

  it('cannot be scouted until the project has a location area', async () => {
    const { requests } = serverFor([], { 'GET /api/projects/p1': () => json(project({ locationArea: null })) })
    renderApp('/scenes/s1')
    await logIn()

    expect(await screen.findByRole('link', { name: 'Set one on the project' })).toHaveAttribute('href', '/projects/p1')
    expect(screen.getByRole('button', { name: 'Scout locations' })).toBeDisabled()
    expect(screen.queryByText(/Scouting in/)).toBeNull()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)
  })

  it('explain a failed scouting run as the server does', async () => {
    serverFor([], {
      'POST /api/scenes/s1/scout': () => problem(503, 'Service Unavailable', 'Scouting is not configured on this server'),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Scout locations' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Scouting is not configured on this server')
  })

  it('can be moved along the workflow, keeping their notes', async () => {
    const { requests } = serverFor([location({ notes: 'Call after 3pm.' })], {
      'PUT /api/locations/l1': (req) => json(location({ ...(req.body as object) })),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    const status = await screen.findByLabelText('Status of Tom’s Diner')
    await user.selectOptions(status, 'Shortlisted')

    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ status: 'SHORTLISTED', notes: 'Call after 3pm.' })
    expect(await screen.findByLabelText('Status of Tom’s Diner')).toHaveValue('SHORTLISTED')
    expect(screen.getByLabelText('Status of Tom’s Diner')).toBeEnabled()
  })

  it('can be removed after confirmation', async () => {
    const locations = [location(), location({ id: 'l2', name: 'Corner Bistro', sourceUrl: 'https://bistro.example' })]
    const { requests } = serverFor(locations, {
      'DELETE /api/locations/l1': () => {
        locations.splice(0, 1)
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    const card = await screen.findByRole('article', { name: 'Tom’s Diner' })
    await user.click(within(card).getByRole('button', { name: 'Remove' }))
    await user.click(within(within(card).getByRole('alertdialog')).getByRole('button', { name: 'Remove location' }))

    expect(await screen.findByRole('article', { name: 'Corner Bistro' })).toBeInTheDocument()
    await expect.poll(() => screen.queryByRole('article', { name: 'Tom’s Diner' })).toBeNull()
    expect(requests.filter((r) => r.method === 'DELETE').map((r) => r.path)).toEqual(['/api/locations/l1'])
  })
})
