import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { CoverSet, LibraryVenue, ProjectRole } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene, schedule, scheduled } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const cover = (overrides: Partial<CoverSet> = {}): CoverSet => ({
  id: 'c1',
  sceneId: 's1',
  locationId: 'l2',
  venueName: 'Dock Street Warehouse',
  address: '9 Dock St, Brooklyn',
  status: 'SHORTLISTED',
  trigger: 'if rain > 60%',
  addedByName: 'Ada',
  createdAt: '2026-10-07T10:00:00Z',
  ...overrides,
})

const libraryVenue: LibraryVenue = {
  id: 'v1',
  sourceLocationId: null,
  name: 'Moonlight Diner',
  address: '12 Main St, Brooklyn',
  latitude: null,
  longitude: null,
  sourceUrl: null,
  imageUrl: null,
  bookingFriction: null,
  frictionNote: null,
  footprintWarnings: [],
  contactName: null,
  contactEmail: null,
  contactPhone: null,
  tags: [],
  notes: null,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
}

function serverFor(covers: () => CoverSet[], extra: Parameters<typeof fakeServer>[0] = {}, role: ProjectRole = 'OWNER') {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project({ role })),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf([])),
    'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf(covers(), { size: 50 })),
    'GET /api/scenes/s1/shots': () => json([]),
    ...extra,
  })
}

describe("a scene's cover sets", () => {
  it('are listed with when to switch, and a candidate becomes one: not the confirmed venue, nor one already a cover', async () => {
    let current = [cover()]
    const { requests } = serverFor(() => current, {
      'GET /api/scenes/s1/locations?page=0&size=100': () =>
        json(
          pageOf(
            [
              location({ id: 'l1', name: 'Tom’s Diner', status: 'CONFIRMED' }),
              location({ id: 'l2', name: 'Dock Street Warehouse' }),
              location({ id: 'l3', name: 'Pier 5 Rooftop' }),
            ],
            { size: 100 },
          ),
        ),
      'GET /api/library?page=0&size=24': () => json(pageOf([])),
      'POST /api/scenes/s1/covers': () => {
        current = [...current, cover({ id: 'c2', locationId: 'l3', venueName: 'Pier 5 Rooftop', trigger: 'if wind > 40 km/h' })]
        return json(current[1], 201)
      },
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    const section = await screen.findByRole('region', { name: 'Cover sets' })
    const list = await within(section).findByRole('list', { name: 'Cover sets' })
    expect(within(list).getByRole('link', { name: 'Dock Street Warehouse' })).toHaveAttribute('href', '/locations/l2')
    expect(list).toHaveTextContent('if rain > 60%')

    await user.click(within(section).getByRole('button', { name: 'Add a cover set' }))
    const form = await within(section).findByRole('form', { name: 'New cover set' })
    const venue = within(form).getByLabelText('Backup venue')
    expect(within(venue).getAllByRole('option').map((option) => option.textContent)).toEqual(['Choose a venue', 'Pier 5 Rooftop'])

    await user.click(within(form).getByRole('button', { name: 'Add cover set' }))
    expect(within(form).getByText('Pick a venue.')).toBeInTheDocument()

    await user.selectOptions(venue, 'Pier 5 Rooftop')
    await user.type(within(form).getByLabelText('When to switch'), 'if wind > 40 km/h')
    await user.click(within(form).getByRole('button', { name: 'Add cover set' }))

    expect(await within(list).findByRole('link', { name: 'Pier 5 Rooftop' })).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ locationId: 'l3', trigger: 'if wind > 40 km/h' })
    expect(within(section).queryByRole('form')).not.toBeInTheDocument()
  })

  it('can come from the library: the venue joins the scene first', async () => {
    let current: CoverSet[] = []
    const { requests } = serverFor(() => current, {
      'GET /api/scenes/s1/locations?page=0&size=100': () => json(pageOf([], { size: 100 })),
      'GET /api/library?page=0&size=24': () => json(pageOf([libraryVenue])),
      'POST /api/scenes/s1/locations/from-library': () => json(location({ id: 'l7', name: 'Moonlight Diner' }), 201),
      'POST /api/scenes/s1/covers': () => {
        current = [cover({ locationId: 'l7', venueName: 'Moonlight Diner', trigger: null })]
        return json(current[0], 201)
      },
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    const section = await screen.findByRole('region', { name: 'Cover sets' })
    expect(await within(section).findByText(/No cover set yet/)).toBeInTheDocument()
    await user.click(within(section).getByRole('button', { name: 'Add a cover set' }))
    const form = await within(section).findByRole('form', { name: 'New cover set' })
    await user.selectOptions(await within(form).findByLabelText('Backup venue'), await within(form).findByRole('option', { name: 'Moonlight Diner' }))
    await user.click(within(form).getByRole('button', { name: 'Add cover set' }))

    expect(await within(section).findByText('No trigger set')).toBeInTheDocument()
    expect(requests.filter((r) => r.method === 'POST').map((r) => [r.path, r.body])).toEqual([
      ['/api/scenes/s1/locations/from-library', { libraryVenueId: 'v1' }],
      ['/api/scenes/s1/covers', { locationId: 'l7', trigger: null }],
    ])
  })

  it('have their trigger changed and are removed, and a refusal is explained', async () => {
    let current = [cover()]
    const { requests } = serverFor(() => current, {
      'PUT /api/covers/c1': (req) => {
        current = [cover({ trigger: (req.body as { trigger: string | null }).trigger })]
        return json(current[0])
      },
      'DELETE /api/covers/c1': () => {
        if (requests.filter((r) => r.method === 'DELETE').length === 1) return problem(409, 'Conflict', 'Try again')
        current = []
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    const section = await screen.findByRole('region', { name: 'Cover sets' })
    await user.click(await within(section).findByRole('button', { name: 'Change when to switch to Dock Street Warehouse' }))
    const form = within(section).getByRole('form', { name: 'When to switch to Dock Street Warehouse' })
    const field = within(form).getByLabelText('When to switch')
    expect(field).toHaveValue('if rain > 60%')
    await user.clear(field)
    await user.click(within(form).getByRole('button', { name: 'Save' }))
    expect(await within(section).findByText('No trigger set')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ trigger: null })

    const remove = within(section).getByRole('button', { name: 'Stop using Dock Street Warehouse as a cover set' })
    await user.click(remove)
    expect(await within(section).findByRole('alert')).toHaveTextContent('Try again')
    await user.click(remove)
    expect(await within(section).findByText(/No cover set yet/)).toBeInTheDocument()
  })

  it('are read only for a viewer', async () => {
    serverFor(() => [cover()], {}, 'VIEWER')
    renderApp('/scenes/s1')
    await logIn()

    const section = await screen.findByRole('region', { name: 'Cover sets' })
    expect(await within(section).findByText('if rain > 60%')).toBeInTheDocument()
    expect(within(section).queryByRole('button')).not.toBeInTheDocument()
  })
})

describe('cover sets on the schedule and call sheet', () => {
  const withCover = {
    ...schedule,
    days: [
      {
        date: '2026-10-12',
        scenes: [
          scheduled({
            covers: [
              { id: 'c1', locationId: 'l2', name: 'Dock Street Warehouse', address: '9 Dock St, Brooklyn', contactName: 'Rae', contactPhone: '555 0199', trigger: 'if rain > 60%' },
            ],
          }),
        ],
      },
    ],
    unscheduled: [],
  }
  const base = {
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    'GET /api/projects/p1/moves': () => json({ days: [], warnAfterMinutes: 60, attribution: '' }),
    'GET /api/projects/p1/schedule': () => json(withCover),
  }

  it('are listed under the scene on the schedule', async () => {
    fakeServer(base)
    renderApp('/projects/p1?tab=schedule')
    await logIn()

    const covers = await screen.findByRole('list', { name: 'Cover sets for INT. DINER - NIGHT' })
    expect(within(covers).getByRole('link', { name: 'Dock Street Warehouse' })).toHaveAttribute('href', '/locations/l2')
    expect(covers).toHaveTextContent('if rain > 60%')
  })

  it('are on the call sheet with their trigger, address and contact', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/call-sheet-link': () => problem(404, 'Not found', 'Not shared') })
    renderApp('/projects/p1/call-sheet')
    await logIn()

    const sheet = await screen.findByRole('article', { name: 'Call sheet' })
    expect(await within(sheet).findByText('Cover: Dock Street Warehouse')).toBeInTheDocument()
    expect(sheet).toHaveTextContent('(if rain > 60%)9 Dock St, Brooklyn')
    expect(sheet).toHaveTextContent('Cover: Rae, 555 0199')
  })
})
