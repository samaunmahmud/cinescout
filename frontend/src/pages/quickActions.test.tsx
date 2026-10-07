import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Location, ProjectRole, Scene } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'
import { mapsUrl } from '../components/venueActions'

function sceneServer(locations: Location[], extra: Parameters<typeof fakeServer>[0] = {}, role: ProjectRole = 'OWNER') {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project({ role })),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
    'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf(locations)),
    ...extra,
  })
}

const menuItems = (menu: HTMLElement) => within(menu).getAllByRole('menuitem').map((item) => item.textContent)

describe('quick actions on a venue card', () => {
  it('open from the ⋯ button, move by keyboard, and close on Escape back to the button', async () => {
    sceneServer([location()])
    renderApp('/scenes/s1')
    const user = await logIn()

    const button = await screen.findByRole('button', { name: 'Quick actions for Tom’s Diner' })
    expect(button).toHaveAttribute('aria-haspopup', 'menu')
    await user.click(button)
    const menu = screen.getByRole('menu', { name: 'Quick actions for Tom’s Diner' })
    expect(menuItems(menu)).toEqual([
      'Shortlist',
      'Confirm for this scene',
      'Pass on it',
      'Make it a cover set',
      'Draft an email',
      'Save to my library',
      'Copy address',
      'Open in Google Maps (opens in a new tab)',
      'Compare with the others',
    ])
    expect(within(menu).getByRole('menuitem', { name: 'Shortlist' })).toHaveFocus()
    await user.keyboard('{ArrowUp}')
    expect(within(menu).getByRole('menuitem', { name: 'Compare with the others' })).toHaveFocus()
    await user.keyboard('{Home}{ArrowDown}')
    expect(within(menu).getByRole('menuitem', { name: 'Confirm for this scene' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(button).toHaveFocus()
  })

  it('confirm the venue, make it a cover set, save it and copy its address, saying what each did', async () => {
    let current = location()
    const { requests } = sceneServer([current], {
      'PUT /api/locations/l1': (req) => {
        current = { ...current, ...(req.body as object) } as Location
        return json(current)
      },
      'POST /api/scenes/s1/covers': () => json({ id: 'c1' }, 201),
      'POST /api/library': () => json({ id: 'v1' }, 201),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    const card = await screen.findByRole('article', { name: 'Tom’s Diner' })
    const pick = async (name: string) => {
      await user.click(within(card).getByRole('button', { name: 'Quick actions for Tom’s Diner' }))
      await user.click(within(card).getByRole('menuitem', { name }))
    }

    await pick('Make it a cover set')
    expect(await within(card).findByRole('status')).toHaveTextContent('Tom’s Diner is now a cover set for this scene.')
    await pick('Save to my library')
    expect(await within(card).findByText('Tom’s Diner is in your library.')).toBeInTheDocument()
    await pick('Copy address')
    expect(await within(card).findByText('Address copied.')).toBeInTheDocument()
    expect(await navigator.clipboard.readText()).toBe('782 Washington Ave, Brooklyn, NY') // user-event's clipboard
    await pick('Confirm for this scene')

    await vi.waitFor(() => expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ status: 'CONFIRMED', notes: current.notes }))
    expect(requests.filter((r) => r.method === 'POST').map((r) => [r.path, r.body])).toEqual([
      ['/api/scenes/s1/covers', { locationId: 'l1', trigger: null }],
      ['/api/library', { locationId: 'l1' }],
    ])
    await user.click(within(card).getByRole('button', { name: 'Quick actions for Tom’s Diner' }))
    // Confirmed now: neither confirm again nor make a backup of itself.
    expect(menuItems(within(card).getByRole('menu'))).not.toContain('Confirm for this scene')
    expect(menuItems(within(card).getByRole('menu'))).not.toContain('Make it a cover set')
  })

  it('offer a viewer only what changes nothing', async () => {
    sceneServer([location({ address: null })], {}, 'VIEWER')
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Quick actions for Tom’s Diner' }))
    expect(menuItems(screen.getByRole('menu'))).toEqual(['Save to my library', 'Compare with the others'])
  })

  it('find the venue on Google Maps by its pin, else by name and address', () => {
    expect(mapsUrl({ name: 'A', address: 'B', latitude: 40.5, longitude: -73.9 })).toBe('https://www.google.com/maps/search/?api=1&query=40.5,-73.9')
    expect(mapsUrl({ name: 'Tom’s Diner', address: '782 Washington Ave', latitude: null, longitude: null })).toBe(
      'https://www.google.com/maps/search/?api=1&query=Tom%E2%80%99s%20Diner%2C%20782%20Washington%20Ave',
    )
    expect(mapsUrl({ name: 'A', address: null, latitude: null, longitude: null })).toBeNull()
  })
})

describe('quick actions on a scene row', () => {
  function projectServer(scenes: () => Scene[], extra: Parameters<typeof fakeServer>[0] = {}) {
    return fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf(scenes())),
      ...extra,
    })
  }

  it('analyse a scene with the AI and delete one after confirmation, without opening them', async () => {
    let scenes = [scene(), scene({ id: 's2', sceneNumber: 13, title: 'EXT. PIER - DAWN', parseStatus: 'PARSED' })]
    const { requests } = projectServer(() => scenes, {
      'POST /api/scenes/s1/parse': () => {
        scenes = [{ ...scenes[0], parseStatus: 'PARSED' }, scenes[1]]
        return json(scenes[0])
      },
      'DELETE /api/scenes/s2': () => {
        scenes = [scenes[0]]
        return new Response(null, { status: 204 })
      },
    })
    const { router } = renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Quick actions for Scene 12: INT. DINER - NIGHT' }))
    expect(menuItems(screen.getByRole('menu'))).toEqual([
      'Open scene', 'Analyse with AI', 'Add a venue by hand', 'Compare venues', 'Set shoot dates', 'Delete scene…',
    ])
    await user.click(screen.getByRole('menuitem', { name: 'Analyse with AI' }))
    await vi.waitFor(() => expect(requests.filter((r) => r.method === 'POST').map((r) => r.path)).toEqual(['/api/scenes/s1/parse']))

    await user.click(screen.getByRole('button', { name: 'Quick actions for Scene 13: EXT. PIER - DAWN' }))
    expect(menuItems(screen.getByRole('menu'))).not.toContain('Analyse with AI')
    await user.click(screen.getByRole('menuitem', { name: 'Delete scene…' }))
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete scene' }))

    await expect.poll(() => screen.queryByRole('link', { name: /EXT\. PIER - DAWN/ })).toBeNull()
    expect(requests.filter((r) => r.method === 'DELETE').map((r) => r.path)).toEqual(['/api/scenes/s2'])
    expect(router.state.location.pathname).toBe('/projects/p1')
  })
})
