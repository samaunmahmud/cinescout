import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, locationVideos, logIn, scene, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function server() {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/locations/l1/outreach-drafts?page=0&size=24': () => json(pageOf([])),
    'GET /api/locations/l1/videos': () => json(locationVideos()),
  })
}

describe('the location page tabs', () => {
  it('open on the overview, and fetch videos only when their tab is opened', async () => {
    const { requests } = server()
    const { router } = renderApp('/locations/l1')
    const user = await logIn()

    const tabs = await screen.findByRole('tablist', { name: 'About this location' })
    expect(within(tabs).getByRole('tab', { name: 'Overview' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('heading', { name: 'Scout’s report' })).toBeInTheDocument()
    expect(requests.some((r) => r.path.endsWith('/videos'))).toBe(false)

    await user.click(within(tabs).getByRole('tab', { name: 'Videos' }))

    expect(await screen.findByRole('list', { name: 'Videos of the venue' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?tab=videos')
    expect(screen.queryByRole('heading', { name: 'Scout’s report' })).toBeNull()
    expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Videos')
  })

  it('open the tab named in the address', async () => {
    server()
    renderApp('/locations/l1?tab=outreach')
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Outreach' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Outreach' })).toHaveAttribute('aria-selected', 'true')
  })

  it('move with the arrow keys, keeping only the selected tab in the tab order', async () => {
    server()
    const { router } = renderApp('/locations/l1')
    const user = await logIn()

    const overview = await screen.findByRole('tab', { name: 'Overview' })
    expect(overview).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('tab', { name: 'Logistics' })).toHaveAttribute('tabindex', '-1')

    overview.focus()
    await user.keyboard('{ArrowLeft}')

    const outreach = screen.getByRole('tab', { name: 'Outreach' })
    expect(outreach).toHaveFocus()
    expect(outreach).toHaveAttribute('aria-selected', 'true')
    expect(router.state.location.search).toBe('?tab=outreach')

    await user.keyboard('{Home}')
    expect(screen.getByRole('tab', { name: 'Overview' })).toHaveFocus()
    expect(router.state.location.search).toBe('')
  })
})
