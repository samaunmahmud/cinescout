import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SearchResults } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const found: SearchResults = {
  projects: [{ id: 'p1', title: 'Night Shift', locationArea: 'Brooklyn' }],
  scenes: [{ id: 's1', sceneNumber: 12, title: 'INT. DINER - NIGHT', projectId: 'p1', projectTitle: 'Night Shift' }],
  venues: [{ id: 'l1', name: 'Tom’s Diner', address: '782 Washington Ave', status: 'CONFIRMED', sceneId: 's1', sceneTitle: 'INT. DINER - NIGHT', projectTitle: 'Night Shift' }],
}

function server() {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([project()])),
    'GET /api/search?q=di': () => json(found),
    'GET /api/locations/l1': () => json({ type: 'about:blank', title: 'Not Found', status: 404 }, 404),
  })
}

describe('the command palette', () => {
  it('opens with Ctrl+K, finds productions, scenes and venues, and opens one with the keyboard', async () => {
    const { requests } = server()
    const { router } = renderApp('/projects')
    const user = await logIn()
    await screen.findByRole('heading', { name: /productions/i })

    await user.keyboard('{Control>}k{/Control}')
    const dialog = screen.getByRole('dialog', { name: 'Search and jump' })
    const box = within(dialog).getByRole('combobox', { name: 'Search productions, scenes and venues' })
    expect(box).toHaveFocus()
    expect(within(dialog).getAllByRole('option').map((o) => o.textContent)).toContain('My locationsYour venue library')

    await user.type(box, 'di')
    expect(await within(dialog).findByRole('option', { name: /Tom’s Diner/ })).toHaveTextContent('Confirmed · Night Shift')
    expect(within(dialog).getByRole('option', { name: /12\. INT\. DINER - NIGHT/ })).toBeInTheDocument()
    expect(requests.filter((r) => r.path.startsWith('/api/search')).map((r) => r.path)).toEqual(['/api/search?q=di'])

    // Commands that do not match drop out; the first match is chosen, arrows move.
    const options = within(dialog).getAllByRole('option')
    expect(options[0]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowUp}')
    expect(options[options.length - 1]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{Enter}')

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/locations/l1')) // a lazy route

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('opens from the header button, jumps to a page, and closes on Escape back to the button', async () => {
    server()
    const { router } = renderApp('/projects')
    const user = await logIn()

    const button = await screen.findByRole('button', { name: 'Search (Ctrl+K)' })
    await user.click(button)
    await user.type(screen.getByRole('combobox'), 'acc')
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['AccountName, password, calendar feed'])
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(button).toHaveFocus()

    await user.click(button)
    await user.type(screen.getByRole('combobox'), 'new prod{Enter}')
    expect(router.state.location.pathname + router.state.location.search).toBe('/projects?new')
  })
})
