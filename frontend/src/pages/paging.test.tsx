import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeServer, json } from '../test/fakeServer'
import { ada, draft, location, locationVideos, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const projects = (from: number, count: number) =>
  Array.from({ length: count }, (_, i) => project({ id: `p${from + i}`, title: `Film ${from + i}` }))

/** 30 active projects, served 24 to a page. */
function projectServer() {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf(projects(1, 24), { totalItems: 30, totalPages: 2 })),
    'GET /api/projects?status=ACTIVE&page=1&size=24': () => json(pageOf(projects(25, 6), { page: 1, totalItems: 30, totalPages: 2 })),
  })
}

describe('long lists', () => {
  it('come a page at a time, with the page in the URL', async () => {
    projectServer()
    const { router } = renderApp('/projects')
    const user = await logIn()

    const pager = await screen.findByRole('navigation', { name: 'Project pages' })
    expect(pager).toHaveTextContent('1–24 of 30 · page 1 of 2')
    expect(within(pager).getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('link', { name: /Film 24/ })).toBeInTheDocument()

    await user.click(within(pager).getByRole('button', { name: 'Next' }))

    expect(await screen.findByRole('link', { name: /Film 30/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Film 24/ })).toBeNull()
    expect(screen.getByRole('navigation', { name: 'Project pages' })).toHaveTextContent('25–30 of 30 · page 2 of 2')
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(router.state.location.search).toBe('?page=2')

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    expect(await screen.findByRole('link', { name: /Film 24/ })).toBeInTheDocument()
    expect(router.state.location.search).toBe('')
  })

  it('open on the page the URL names', async () => {
    const { requests } = projectServer()
    renderApp('/projects?page=2')
    await logIn()

    expect(await screen.findByRole('link', { name: /Film 25/ })).toBeInTheDocument()
    expect(requests.map((r) => r.path)).not.toContain('/api/projects?status=ACTIVE&page=0&size=24')
  })

  it('go back to the last page when the one in the URL is past the end', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=4&size=24': () => json(pageOf([], { page: 4, totalItems: 30, totalPages: 2 })),
      'GET /api/projects?status=ACTIVE&page=1&size=24': () => json(pageOf(projects(25, 6), { page: 1, totalItems: 30, totalPages: 2 })),
    })
    const { router } = renderApp('/projects?page=5')
    await logIn()

    expect(await screen.findByRole('link', { name: /Film 25/ })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?page=2')
  })

  it('show no pager when everything fits on one page', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf(projects(1, 3))),
    })
    renderApp('/projects')
    await logIn()

    expect(await screen.findByRole('link', { name: /Film 3/ })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Project pages' })).toBeNull()
  })

  it('keep the tab when paging a location’s emails', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/locations/l1': () => json(location()),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
      'GET /api/locations/l1/outreach-drafts?page=1&size=24': () =>
        json(pageOf([draft({ id: 'd30', subject: 'An old enquiry' })], { page: 1, totalItems: 25, totalPages: 2 })),
      'GET /api/locations/l1/outreach-drafts?page=0&size=24': () =>
        json(pageOf([draft({ subject: 'The newest' })], { totalItems: 25, totalPages: 2 })),
    })
    const { router } = renderApp('/locations/l1?tab=outreach&page=2')
    const user = await logIn()

    expect(await screen.findByText('An old enquiry')).toBeInTheDocument()
    await user.click(within(screen.getByRole('navigation', { name: 'Email pages' })).getByRole('button', { name: 'Previous' }))

    expect(await screen.findByText('The newest')).toBeInTheDocument()
    expect(new URLSearchParams(router.state.location.search).get('tab')).toBe('outreach')
    expect(requests.map((r) => r.path)).toContain('/api/locations/l1/outreach-drafts?page=0&size=24')
  })

  it('say the scene map shows only the venues on the page', async () => {
    const placed = location({ latitude: 40.7, longitude: -73.9 })
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf([placed], { totalItems: 30, totalPages: 2 })),
    })
    renderApp('/scenes/s1')
    await logIn()

    expect(await screen.findByText('The map shows the venues on this page.')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Location pages' })).toBeInTheDocument()
  })
})
