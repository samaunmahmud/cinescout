import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ScoutFilters } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'
import { noFilters } from '../lib/scoutFilters'

describe('scouting filters', () => {
  it('are set for the whole project on its settings page', async () => {
    let stored: ScoutFilters = noFilters
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/members': () => json({ members: [], invites: [] }),
      'GET /api/projects/p1/scout-filters': () => json(stored),
      'PUT /api/projects/p1/scout-filters': (req) => {
        stored = req.body as ScoutFilters
        return json(stored)
      },
    })
    renderApp('/projects/p1/settings?tab=scouting')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Base point'), 'Bedford Ave, Brooklyn')
    await user.type(screen.getByLabelText('Radius (km)'), '3')
    await user.type(screen.getByLabelText('Most a shooting day may cost'), '1500')
    await user.type(screen.getByLabelText('Leave out these kinds of place'), 'church{Enter}nightclub{Enter}')
    await user.click(screen.getByRole('button', { name: 'Stop leaving out church' }))
    await user.click(screen.getByRole('checkbox', { name: /Include private property/ }))
    await user.click(screen.getByRole('button', { name: 'Save filters' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Saved. Within 3 km of Bedford Ave, Brooklyn · up to 1,500 a day · no nightclub · no private property')
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({
      baseAddress: 'Bedford Ave, Brooklyn',
      baseLatitude: null,
      baseLongitude: null,
      radiusKm: 3,
      maxBudget: 1500,
      excludedTypes: ['nightclub'],
      includePrivate: false,
    })
  })

  it('will not take a radius without a base point', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/members': () => json({ members: [], invites: [] }),
      'GET /api/projects/p1/scout-filters': () => json(noFilters),
    })
    renderApp('/projects/p1/settings?tab=scouting')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Radius (km)'), '3')
    expect(screen.getByText('A radius needs a base point above.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save filters' })).toBeDisabled()
  })

  it('can be changed for one run on the scene, and the run says what they left out', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
      'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf([location()])),
      'GET /api/projects/p1/scout-filters': () => json({ ...noFilters, maxBudget: 900 }),
      'POST /api/scenes/s1/scout': () =>
        json({ added: [], alreadySaved: 1, unassessed: 0, notVenues: 0, unsuitable: 0, filteredOut: { outsideRadius: 0, overBudget: 2, excludedType: 0, privateProperty: 0 } }),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Scout with other filters this time' }))
    expect(await screen.findByText(/The project’s filters: Up to 900 a day/)).toBeInTheDocument()
    const budget = screen.getByLabelText('Most a shooting day may cost')
    await user.clear(budget)
    await user.type(budget, '2000')
    await user.click(screen.getByRole('button', { name: 'Scout with these filters' }))

    expect(await screen.findByText(/Your filters left out 2 over budget/)).toBeInTheDocument()
    const run = requests.find((r) => r.method === 'POST')!.body as { filters: ScoutFilters }
    expect(run.filters.maxBudget).toBe(2000)
    expect(within(screen.getByRole('region', { name: 'Locations' })).queryByLabelText('Most a shooting day may cost')).not.toBeInTheDocument()
  })
})
