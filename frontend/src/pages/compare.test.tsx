import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Location } from '../api/types'
import { venuesToCompare } from '../lib/compare'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, logIn, logisticsReport, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const diner = location({ status: 'SHORTLISTED', notes: 'Owner is keen.', quote: '$400 a night', logistics: logisticsReport() })
const bistro = location({ id: 'l2', name: 'Corner Bistro', fitScore: 61, status: 'CONTACTED', footprintWarnings: [], frictionNote: null })
const pizza = location({ id: 'l3', name: 'Sal’s Pizza', fitScore: 40 })
const byHand = location({ id: 'l4', name: 'Aunt May’s Kitchen', fitScore: null, fitReason: null, bookingFriction: null, frictionNote: null, footprintWarnings: [], address: null })

function server(locations: Location[], extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
    'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf(locations)),
    'GET /api/scenes/s1/locations?page=0&size=100': () => json(pageOf(locations, { size: 100 })),
    'GET /api/scenes/s1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    ...extra,
  })
}

/** The cells of the row with that heading, one per venue. */
function row(name: string) {
  return within(screen.getByRole('row', { name: new RegExp(`^${name}`) })).getAllByRole('cell')
}

describe('comparing the venues of a scene', () => {
  it('is reached from the scene and puts the shortlisted venues side by side', async () => {
    server([diner, bistro, pizza])
    const { router } = renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('link', { name: 'Compare' }))

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/scenes/s1/compare'))
    const table = await screen.findByRole('table', { name: 'Venues compared' })
    expect(within(table).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Tom’s Diner', 'Corner Bistro'])
    expect(within(table).getByRole('link', { name: 'Corner Bistro' })).toHaveAttribute('href', '/locations/l2')
    expect(screen.getByText(/The venues you have shortlisted, contacted or confirmed/)).toBeInTheDocument()
    expect(within(row('Fit')[0]).getByLabelText('Fit 82 out of 100')).toBeInTheDocument()
    expect(row('Booking')[0]).toHaveTextContent('BusinessNeeds the owner’s permission; closed Mondays.')
    expect(within(row('Warnings')[0]).getAllByRole('listitem')).toHaveLength(2)
    expect(row('Warnings')[1]).toHaveTextContent('None found')
    expect(row('Noise risk')[0]).toHaveTextContent('HighAtlantic Avenue')
    expect(row('Noise risk')[1]).toHaveTextContent('Work out the venue’s logistics to see this')
    expect(row('First shoot day')[0]).toHaveTextContent(/2026: sun 06:49–18:43Rain, 14–16 °CRain likely/)
    expect(row('Your notes')[0]).toHaveTextContent('Owner is keen.')
    expect(row('Quote')[0]).toHaveTextContent('$400 a night')
    expect(row('Quote')[1]).toHaveTextContent('None yet')
    expect(screen.getByRole('link', { name: 'Scene 12: INT. DINER - NIGHT' })).toHaveAttribute('href', '/scenes/s1')
  })

  it('falls back to the best fits while fewer than two are shortlisted, and copes with a venue nobody assessed', async () => {
    server([location(), byHand, location({ id: 'l5', name: 'Rejected Place', status: 'REJECTED' })])
    renderApp('/scenes/s1/compare')
    await logIn()

    const table = await screen.findByRole('table', { name: 'Venues compared' })
    expect(within(table).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Tom’s Diner', 'Aunt May’s Kitchen'])
    expect(screen.getByText(/The best-fitting venues for this scene/)).toBeInTheDocument()
    expect(row('Fit')[1]).toHaveTextContent('Not assessed')
    expect(row('Booking')[1]).toHaveTextContent('Unknown')
    expect(row('Address')[1]).toHaveTextContent('Not known')
  })

  it('changes a status in place, and a rejected venue leaves the comparison', async () => {
    const third = location({ id: 'l3', name: 'Sal’s Pizza', fitScore: 40, status: 'SHORTLISTED' })
    const { requests } = server([diner, bistro, third], {
      'PUT /api/locations/l2': () => json({ ...bistro, status: 'REJECTED' }),
    })
    renderApp('/scenes/s1/compare')
    const user = await logIn()
    await screen.findByRole('table', { name: 'Venues compared' })
    expect(screen.getAllByRole('columnheader')).toHaveLength(3)

    await user.selectOptions(screen.getByLabelText('Status of Corner Bistro'), 'REJECTED')
    const why = screen.getByRole('dialog', { name: 'Why pass on Corner Bistro?' })
    await user.click(within(why).getByRole('button', { name: 'Too expensive' }))
    await user.click(within(why).getByRole('button', { name: 'Reject' }))

    await screen.findByRole('table', { name: 'Venues compared' })
    await vi.waitFor(() => expect(screen.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Tom’s Diner', 'Sal’s Pizza']))
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ status: 'REJECTED', notes: null, rejectionReason: 'Too expensive' })
  })

  it('says so when there is nothing to compare, and is not found for another user’s scene', async () => {
    server([diner])
    const { unmount } = renderApp('/scenes/s1/compare')
    await logIn()
    expect(await screen.findByText(/nothing to compare yet/)).toBeInTheDocument()
    unmount()

    fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/scenes/s1': () => problem(404, 'Not found', 'Scene not found') }, { loggedIn: true })
    renderApp('/scenes/s1/compare')
    expect(await screen.findByRole('heading', { name: 'Not found' })).toBeInTheDocument()
  })
})

describe('venuesToCompare', () => {
  const many = Array.from({ length: 6 }, (_, i) => location({ id: `v${i}`, status: i < 5 ? 'SHORTLISTED' : 'SUGGESTED' }))

  it('takes at most four, in the order given', () => {
    expect(venuesToCompare(many).venues.map((v) => v.id)).toEqual(['v0', 'v1', 'v2', 'v3'])
    expect(venuesToCompare(many).shortlist).toBe(true)
  })

  it('compares the best fits when only one venue is picked out', () => {
    const result = venuesToCompare([location({ id: 'a' }), location({ id: 'b', status: 'CONFIRMED' }), location({ id: 'c', status: 'REJECTED' })])
    expect(result).toMatchObject({ shortlist: false })
    expect(result.venues.map((v) => v.id)).toEqual(['a', 'b'])
  })
})
