import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Move, Moves } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, pageOf, project, schedule } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function move(overrides: Partial<Move> = {}): Move {
  return {
    fromLocationId: 'l1',
    fromName: 'Tom’s Diner',
    fromSceneId: 's1',
    toLocationId: 'l2',
    toName: 'Sky Bar',
    toSceneId: 's2',
    status: 'OK',
    minutes: 21,
    kilometres: 7.8,
    tooLong: false,
    text: 'Tom’s Diner to Sky Bar: 21 min, 7.8 km by road',
    ...overrides,
  }
}

const moves: Moves = {
  days: [
    {
      date: '2026-10-12',
      moves: [
        move(),
        move({ fromLocationId: 'l2', fromName: 'Sky Bar', toLocationId: 'l3', toName: 'Brighton Pier', minutes: 95, kilometres: 88.4, tooLong: true,
          text: 'Sky Bar to Brighton Pier: 1 h 35 min, 88.4 km by road' }),
      ],
    },
    { date: '2026-10-13', moves: [move({ status: 'UNPLACED', minutes: null, kilometres: null, text: 'Tom’s Diner to Sky Bar: set both venues’ pins to work out the drive' })] },
  ],
  warnAfterMinutes: 60,
  attribution: 'Routing by OSRM, map data © OpenStreetMap contributors (ODbL)',
}

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
  'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
  'GET /api/projects/p1/schedule': () => json(schedule),
  'GET /api/projects/p1/moves': () => json(moves),
  'GET /api/projects/p1/call-sheet-link': () => problem(404, 'Not found', 'Not shared'),
}

describe('company moves', () => {
  it('are listed under each shoot day, a long one flagged, and those of later days gathered at the end', async () => {
    fakeServer(base)
    renderApp('/projects/p1?tab=schedule')
    await logIn()

    const day = within(await screen.findByRole('region', { name: /Monday.*October.*2026/ }))
    expect(await day.findByText('Company moves')).toBeInTheDocument()
    expect(day.getByText('Tom’s Diner to Sky Bar: 21 min, 7.8 km by road')).toBeInTheDocument()
    expect(day.getByText(/Sky Bar to Brighton Pier/)).toHaveTextContent('Sky Bar to Brighton Pier: 1 h 35 min, 88.4 km by road (over 60 min)')
    const later = within(screen.getByRole('region', { name: 'Company moves on later shoot days' }))
    expect(later.getByText(/set both venues’ pins/)).toBeInTheDocument()
  })

  it('are on the call sheet with the routing credit', async () => {
    fakeServer(base)
    renderApp('/projects/p1/call-sheet')
    await logIn()

    const sheet = within(await screen.findByRole('article', { name: 'Call sheet' }))
    expect(await sheet.findByText('! Sky Bar to Brighton Pier: 1 h 35 min, 88.4 km by road (over 60 min)')).toBeInTheDocument()
    expect(sheet.getByText(/Drive times without traffic\. Routing by OSRM/)).toBeInTheDocument()
  })

  it('show on the shared call sheet as far as they are known', async () => {
    fakeServer({
      'GET /api/auth/me': () => problem(401, 'Unauthorized', 'Log in'),
      'GET /api/public/call-sheets/tok': () => json({ projectTitle: 'Night Shift', locationArea: null, preparedBy: 'Ada', schedule, moves }),
    })
    renderApp('/call-sheet/tok')

    expect(await screen.findByText('Tom’s Diner to Sky Bar: 21 min, 7.8 km by road')).toBeInTheDocument()
  })
})
