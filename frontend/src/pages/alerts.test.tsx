import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Alert, ProjectSettings } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const alert = (overrides: Partial<Alert> = {}): Alert => ({
  id: 'a1',
  kind: 'WEATHER',
  projectId: 'p1',
  projectTitle: 'Night Shift',
  sceneId: 's1',
  locationId: 'l1',
  draftId: null,
  payload: { scene: 'Rooftop chase', venue: 'Skyline Rooftop', day: '2026-10-12', reasons: ['RAIN'], rainChance: 75, rainThreshold: 60, covers: [] },
  read: false,
  createdAt: '2026-10-11T06:23:00Z',
  ...overrides,
})

const followUp = alert({
  id: 'a2',
  kind: 'FOLLOW_UP',
  draftId: 'd1',
  payload: { venue: 'The Sky Bar', subject: 'Location enquiry', sentAt: '2026-10-01T09:30:00+00:00' },
  read: true,
})

describe('the alert bell', () => {
  it('counts the unread alerts, lists the latest, and opening one marks it read and goes there', async () => {
    let unread = 1
    const { alertRequests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([project()])),
      'GET /api/alerts/unread-count': () => json({ unread }),
      'GET /api/alerts?page=0&size=10': () => json(pageOf([alert({ read: unread === 0 }), followUp], { size: 10 })),
      'POST /api/alerts/a1/read': () => {
        unread = 0
        return new Response(null, { status: 204 })
      },
      'GET /api/scenes/s1': () => problem(404, 'Not found'),
    })
    const { router } = renderApp('/projects')
    const user = await logIn()

    const bell = await screen.findByRole('button', { name: 'Alerts, 1 unread' })
    expect(bell).toHaveAttribute('aria-expanded', 'false')
    await user.click(bell)

    const panel = await screen.findByRole('region', { name: 'Alerts' })
    const items = await within(panel).findAllByRole('link')
    expect(items[0]).toHaveTextContent(/Unread: Rain likely at Skyline Rooftop/)
    expect(items[0]).toHaveTextContent('Rooftop chase has no cover set.')
    expect(items[0]).toHaveAttribute('href', '/scenes/s1')
    expect(items[1]).toHaveTextContent('Follow up with The Sky Bar')
    expect(items[1]).not.toHaveTextContent('Unread')
    expect(items[1]).toHaveAttribute('href', '/locations/l1?tab=outreach')

    await user.click(items[0])
    expect(router.state.location.pathname).toBe('/scenes/s1')
    expect(screen.queryByRole('region', { name: 'Alerts' })).not.toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Alerts' })).toBeInTheDocument()
    expect(alertRequests.filter((r) => r.method === 'POST').map((r) => r.path)).toEqual(['/api/alerts/a1/read'])
  })

  it('marks everything read at once, closes on Escape, and says so when there is nothing', async () => {
    let unread = 3
    let items = [alert(), alert({ id: 'a3' }), alert({ id: 'a4' })]
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])),
      'GET /api/alerts/unread-count': () => json({ unread }),
      'GET /api/alerts?page=0&size=10': () => json(pageOf(items, { size: 10 })),
      'POST /api/alerts/read-all': () => {
        unread = 0
        items = []
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/projects')
    const user = await logIn()

    const bell = await screen.findByRole('button', { name: 'Alerts, 3 unread' })
    await user.click(bell)
    const panel = await screen.findByRole('region', { name: 'Alerts' })
    await user.click(within(panel).getByRole('button', { name: 'Mark all read' }))
    expect(await within(panel).findByText(/No alerts\./)).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Alerts' })).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('region', { name: 'Alerts' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Alerts' })).toHaveFocus()
  })
})

describe('the weather watch settings', () => {
  it('are changed by an editor and only read by a viewer', async () => {
    let stored: ProjectSettings = { followUpDays: 5, rainAlertPercent: 60, windAlertKmh: 40 }
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/settings': () => json(stored),
      'PUT /api/projects/p1/settings': (req) => {
        stored = req.body as ProjectSettings
        return json(stored)
      },
    })
    renderApp('/projects/p1/settings?tab=weather')
    const user = await logIn()

    const form = await screen.findByRole('form', { name: 'Weather watch settings' })
    const rain = within(form).getByLabelText('Chance of rain (%)')
    await user.clear(rain)
    await user.type(rain, '101')
    expect(within(form).getByText('Enter a whole percentage from 1 to 100.')).toBeInTheDocument()
    expect(within(form).getByRole('button', { name: 'Save' })).toBeDisabled()
    await user.clear(rain)
    await user.type(rain, '70')
    const wind = within(form).getByLabelText('Wind (km/h)')
    await user.clear(wind)
    await user.type(wind, '50')
    await user.click(within(form).getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('status')).toHaveTextContent('from a 70% chance of rain, or wind of 50 km/h')
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ followUpDays: 5, rainAlertPercent: 70, windAlertKmh: 50 })
  })

  it('are a sentence for a viewer', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ role: 'VIEWER' })),
      'GET /api/projects/p1/settings': () => json({ followUpDays: 5, rainAlertPercent: 60, windAlertKmh: 40 }),
    })
    renderApp('/projects/p1/settings?tab=weather')
    await logIn()

    expect(await screen.findByText('A shoot day raises an alert from a 60% chance of rain, or wind of 40 km/h.')).toBeInTheDocument()
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
  })
})
