import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, projectLocation, projectProgress } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

// Leaflet is loaded on demand, and the first import in a test run takes a moment.
const MAP_LOAD = { timeout: 5000 }

const diner = projectLocation({ latitude: 40.6745, longitude: -73.9633 })
const bistro = projectLocation({ id: 'l2', name: 'Corner Bistro', fitScore: 61, status: 'SHORTLISTED', notes: 'Owner is keen.' })
const rooftop = projectLocation({ id: 'l3', sceneId: 's2', sceneNumber: null, sceneTitle: 'Rooftop', name: 'Sky Bar', fitScore: null, bookingFriction: null })

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
  'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
  'GET /api/projects/p1/progress': () => json(projectProgress()),
}

describe('the locations of a project', () => {
  it('are a tab of the project, grouped by scene, with where the scouting stands', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/locations?page=0&size=24': () => json(pageOf([diner, bistro, rooftop])) })
    const { router } = renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('tab', { name: 'Locations' }))

    expect(router.state.location.search).toBe('?tab=locations')
    expect(await screen.findByText('3 of 12 scenes have a confirmed location. 7 have candidates.')).toBeInTheDocument()
    const dinerScene = await screen.findByRole('region', { name: 'Scene 12: INT. DINER - NIGHT' })
    expect(within(dinerScene).getByRole('link', { name: 'Scene 12: INT. DINER - NIGHT' })).toHaveAttribute('href', '/scenes/s1')
    expect(within(dinerScene).getAllByRole('article').map((a) => a.getAttribute('aria-label'))).toEqual(['Tom’s Diner', 'Corner Bistro'])
    expect(within(dinerScene).getByRole('link', { name: 'Corner Bistro' })).toHaveAttribute('href', '/locations/l2')
    expect(within(dinerScene).getByText('Owner is keen.')).toBeInTheDocument()
    const rooftopScene = screen.getByRole('region', { name: 'Rooftop' })
    expect(within(rooftopScene).getByRole('article', { name: 'Sky Bar' })).toHaveTextContent('Added by hand')

    const map = await screen.findByRole('region', { name: 'Map of the project’s locations' }, MAP_LOAD)
    expect(within(map).getByRole('button', { name: 'Tom’s Diner, fit 82' })).toBeInTheDocument()
    expect(screen.getByText('2 of 3 venues are not on the map yet.')).toBeInTheDocument()
  })

  it('can be narrowed to one status, which is kept in the address', async () => {
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/locations?page=0&size=24': () => json(pageOf([diner, bistro, rooftop])),
      'GET /api/projects/p1/locations?status=SHORTLISTED&page=0&size=24': () => json(pageOf([bistro])),
      'GET /api/projects/p1/locations?status=CONFIRMED&page=0&size=24': () => json(pageOf([])),
    })
    const { router } = renderApp('/projects/p1?tab=locations')
    const user = await logIn()

    const filter = await screen.findByRole('group', { name: 'Filter by status' })
    expect(within(filter).getByRole('button', { name: 'All 3' })).toHaveAttribute('aria-pressed', 'true')
    await user.click(within(filter).getByRole('button', { name: 'Shortlisted 1' }))

    expect(router.state.location.search).toBe('?tab=locations&status=SHORTLISTED')
    expect(within(filter).getByRole('button', { name: 'Shortlisted 1' })).toHaveAttribute('aria-pressed', 'true')
    await screen.findByRole('article', { name: 'Corner Bistro' })
    expect(screen.queryByRole('article', { name: 'Tom’s Diner' })).not.toBeInTheDocument()

    await user.click(within(filter).getByRole('button', { name: 'Confirmed 0' }))
    expect(await screen.findByText('No confirmed locations in this project.')).toBeInTheDocument()
    expect(requests.map((r) => r.path)).toContain('/api/projects/p1/locations?status=CONFIRMED&page=0&size=24')
  })

  it('change status in place, and the counts follow', async () => {
    let shortlisted = false
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/progress': () =>
        json(projectProgress(shortlisted ? { locationsByStatus: { SUGGESTED: 1, SHORTLISTED: 2, REJECTED: 0, CONTACTED: 0, CONFIRMED: 0 } } : {})),
      'GET /api/projects/p1/locations?page=0&size=24': () => json(pageOf([shortlisted ? { ...diner, status: 'SHORTLISTED' } : diner])),
      'PUT /api/locations/l1': () => {
        shortlisted = true
        return json(location({ status: 'SHORTLISTED' }))
      },
    })
    renderApp('/projects/p1?tab=locations')
    const user = await logIn()

    await user.selectOptions(await screen.findByLabelText('Status of Tom’s Diner'), 'SHORTLISTED')

    expect(await screen.findByRole('button', { name: 'Shortlisted 2' })).toBeInTheDocument()
    expect(screen.getByLabelText('Status of Tom’s Diner')).toHaveValue('SHORTLISTED')
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ status: 'SHORTLISTED', notes: null })
  })

  it('say what to do while the project has none, without a filter to choose from', async () => {
    fakeServer({
      ...base,
      'GET /api/projects/p1/progress': () =>
        json(projectProgress({ scenes: 1, scenesWithLocations: 0, scenesConfirmed: 0, locations: 0, locationsByStatus: { SUGGESTED: 0, SHORTLISTED: 0, REJECTED: 0, CONTACTED: 0, CONFIRMED: 0 } })),
      'GET /api/projects/p1/locations?page=0&size=24': () => json(pageOf([])),
    })
    renderApp('/projects/p1?tab=locations')
    await logIn()

    expect(await screen.findByText(/No locations yet\. Open a scene and scout it/)).toBeInTheDocument()
    expect(await screen.findByText('0 of 1 scene have a confirmed location. 0 have candidates.')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Filter by status' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /Map/ })).not.toBeInTheDocument()
  })

  it('ignore a status in the address that does not exist, and show a failed load with a way to retry', async () => {
    let calls = 0
    fakeServer({
      ...base,
      'GET /api/projects/p1/locations?page=0&size=24': () => (calls++ === 0 ? problem(503, 'Service unavailable', 'Try again shortly') : json(pageOf([diner]))),
    })
    renderApp('/projects/p1?tab=locations&status=NOPE')
    const user = await logIn()

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again shortly')
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('article', { name: 'Tom’s Diner' })).toBeInTheDocument()
  })

  it('download as a spreadsheet, only the chosen status when one is chosen', async () => {
    const csv = () =>
      new Response('Venue\r\nCorner Bistro\r\n', {
        headers: { 'Content-Type': 'text/csv;charset=UTF-8', 'Content-Disposition': 'attachment; filename="night-shift-locations.csv"' },
      })
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/locations?status=SHORTLISTED&page=0&size=24': () => json(pageOf([bistro])),
      'GET /api/projects/p1/locations/export?status=SHORTLISTED': csv,
    })
    const saved: { name: string; href: string }[] = []
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      saved.push({ name: this.download, href: this.href })
    })
    renderApp('/projects/p1?tab=locations&status=SHORTLISTED')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Export shortlisted as CSV' }))

    await vi.waitFor(() => expect(saved).toEqual([{ name: 'night-shift-locations.csv', href: 'blob:csv' }]))
    expect(requests.find((r) => r.path.includes('/export'))?.headers).toMatchObject({ Accept: 'text/csv, application/problem+json', 'X-Requested-With': 'XMLHttpRequest' })
    click.mockRestore()
  })

  it('say why an export failed', async () => {
    fakeServer({
      ...base,
      'GET /api/projects/p1/locations?page=0&size=24': () => json(pageOf([diner])),
      'GET /api/projects/p1/locations/export': () => problem(500, 'Internal error', 'Something went wrong on our side'),
    })
    renderApp('/projects/p1?tab=locations')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Export as CSV' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on our side')
  })
})
