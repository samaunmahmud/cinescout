import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ProjectRole } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function server(role: ProjectRole) {
  const unassessed = location({ fitScore: null, fitReason: null, bookingFriction: null, frictionNote: null, footprintWarnings: [] })
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(unassessed),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project({ role })),
    'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    'GET /api/locations/l1/availability?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'POST /api/locations/l1/assess': () =>
      json({ ...unassessed, fitScore: 72, fitReason: 'A real diner with booths and neon.', bookingFriction: 'COMMERCIAL', footprintWarnings: ['Tight kitchen'] }),
  })
}

describe('assessing a venue with AI', () => {
  it('scores a venue added by hand and shows the report in place', async () => {
    const { requests } = server('EDITOR')
    renderApp('/locations/l1')
    const user = await logIn()

    expect(await screen.findByRole('heading', { name: 'Not assessed yet' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Assess with AI' }))

    expect(await screen.findByRole('heading', { name: 'Scout’s report' })).toBeInTheDocument()
    expect(screen.getByText('A real diner with booths and neon.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Assess again' })).toBeInTheDocument()
    expect(requests.filter((r) => r.path === '/api/locations/l1/assess')).toHaveLength(1)
  })

  it('is not offered to a viewer', async () => {
    server('VIEWER')
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument()
    // Controls show while the role loads; once it is known, a viewer is not offered the AI.
    await vi.waitFor(() => expect(screen.queryByRole('button', { name: 'Assess with AI' })).not.toBeInTheDocument())
  })
})
