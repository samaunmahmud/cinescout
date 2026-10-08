import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const venues = [location(), location({ id: 'l2', name: 'Corner Bistro', status: 'SHORTLISTED', fitScore: 55 })]

function server() {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
    'GET /api/scenes/s1/shots': () => json([]),
    'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf(venues)),
    'GET /api/scenes/s1/locations?sort=NAME&page=0&size=24': () => json(pageOf([venues[1], venues[0]])),
    'GET /api/scenes/s1/locations?sort=NAME&status=SHORTLISTED&page=0&size=24': () => json(pageOf([venues[1]])),
    'GET /api/scenes/s1/locations?sort=NAME&status=CONFIRMED&page=0&size=24': () => json(pageOf([])),
  })
}

describe('the venue list controls', () => {
  it('sort and filter on the server, keep both in the address, and say when a filter leaves nothing', async () => {
    const { requests } = server()
    const { router } = renderApp('/scenes/s1?page=1')
    const user = await logIn()

    await screen.findByRole('article', { name: 'Tom’s Diner' })
    await user.selectOptions(screen.getByLabelText('Sort'), 'Name A–Z')
    await user.click(within(screen.getByRole('group', { name: 'Show venues' })).getByRole('button', { name: 'Shortlisted' }))

    await expect.poll(() => screen.queryByRole('article', { name: 'Tom’s Diner' })).toBeNull()
    expect(screen.getByRole('article', { name: 'Corner Bistro' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?sort=NAME&status=SHORTLISTED')
    expect(within(screen.getByRole('group', { name: 'Show venues' })).getByRole('button', { name: 'Shortlisted' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(within(screen.getByRole('group', { name: 'Show venues' })).getByRole('button', { name: 'Confirmed' }))
    expect(await screen.findByText(/No venue here is confirmed\./)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Show all' }))
    expect(await screen.findByRole('article', { name: 'Tom’s Diner' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?sort=NAME')
    expect(requests.map((r) => r.path).filter((path) => path.includes('/locations?'))).toContain('/api/scenes/s1/locations?sort=NAME&status=SHORTLISTED&page=0&size=24')
  })

  it('switch to a compact list, remembered in this browser, with the same quick actions', async () => {
    localStorage.removeItem('cinescout.venueView')
    server()
    renderApp('/scenes/s1')
    const user = await logIn()

    await screen.findByRole('article', { name: 'Tom’s Diner' })
    const layout = screen.getByRole('group', { name: 'Layout' })
    await user.click(within(layout).getByRole('button', { name: 'Compact list' }))

    expect(within(layout).getByRole('button', { name: 'Compact list' })).toHaveAttribute('aria-pressed', 'true')
    const row = screen.getByRole('article', { name: 'Corner Bistro' })
    expect(within(row).getByRole('link', { name: 'Corner Bistro' })).toHaveAttribute('href', '/locations/l2')
    expect(within(row).getByLabelText('Status of Corner Bistro')).toHaveValue('SHORTLISTED')
    expect(within(row).getByRole('button', { name: 'Quick actions for Corner Bistro' })).toBeInTheDocument()
    expect(localStorage.getItem('cinescout.venueView')).toBe('list')
    localStorage.removeItem('cinescout.venueView')
  })
})
