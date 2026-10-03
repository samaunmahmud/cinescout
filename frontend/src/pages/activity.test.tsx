import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Activity } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const line = (overrides: Partial<Activity>): Activity => ({
  id: 'a1',
  kind: 'VENUE',
  verb: 'VENUE_STATUS_CHANGED',
  targetType: 'LOCATION',
  targetId: 'l1',
  actorId: 'u2',
  actorName: 'Grace',
  payload: { venue: 'Moonlight Diner', from: 'SUGGESTED', to: 'SHORTLISTED' },
  createdAt: '2026-10-03T10:00:00Z',
  ...overrides,
})

describe('the activity tab of a project', () => {
  it('lists what happened newest first, links to it, and filters by kind', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/activity?page=0&size=24': () =>
        json(
          pageOf([
            line({ id: 'a2', kind: 'CREW', verb: 'MEMBER_JOINED', targetType: 'MEMBER', actorName: 'Ada', payload: { member: 'Grace', role: 'EDITOR' } }),
            line({}),
          ]),
        ),
      'GET /api/projects/p1/activity?kind=CREW&page=0&size=24': () =>
        json(pageOf([line({ id: 'a2', kind: 'CREW', verb: 'MEMBER_JOINED', targetType: 'MEMBER', actorName: 'Ada', payload: { member: 'Grace', role: 'EDITOR' } })])),
    })
    renderApp('/projects/p1?tab=activity')
    const user = await logIn()

    const log = await screen.findByRole('list', { name: 'Activity, newest first' })
    expect(within(log).getAllByRole('listitem').map((item) => item.querySelector('p')?.textContent)).toEqual([
      'Ada added Grace as an editor',
      'Grace moved Moonlight Diner from Suggested to Shortlisted',
    ])
    expect(within(log).getByRole('link', { name: 'Moonlight Diner' })).toHaveAttribute('href', '/locations/l1')

    await user.click(screen.getByRole('button', { name: 'Crew' }))
    await vi.waitFor(() => expect(within(screen.getByRole('list', { name: 'Activity, newest first' })).getAllByRole('listitem')).toHaveLength(1))
    expect(screen.getByRole('button', { name: 'Crew' })).toHaveAttribute('aria-pressed', 'true')
    expect(requests.some((r) => r.path === '/api/projects/p1/activity?kind=CREW&page=0&size=24')).toBe(true)
  })

  it('says so while nothing has happened', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/activity?page=0&size=24': () => json(pageOf([])),
    })
    renderApp('/projects/p1?tab=activity')
    await logIn()

    expect(await screen.findByText(/Nothing has happened yet/)).toBeInTheDocument()
  })
})
