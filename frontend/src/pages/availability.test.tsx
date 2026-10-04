import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Availability, Schedule } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, locationVideos, logIn, pageOf, project, scene, schedule, scheduled } from '../test/fixtures'
import { formatDate, formatDay } from '../lib/format'
import { renderApp } from '../test/renderApp'

function day(overrides: Partial<Availability> = {}): Availability {
  return {
    id: 'a1',
    locationId: 'l1',
    day: '2026-10-12',
    state: 'HELD',
    holdExpiresOn: '2026-10-05',
    note: 'Held by Jo, call back Friday',
    setByName: 'Ada',
    updatedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  }
}

function venueServer(days: () => Availability[], extra: Parameters<typeof fakeServer>[0] = {}, role = project()) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    'GET /api/locations/l1/availability?page=0&size=100': () => json(pageOf(days(), { size: 100 })),
    'GET /api/scenes/s1': () => json(scene({ shootDateStart: '2026-10-12', shootDateEnd: '2026-10-14' })),
    'GET /api/projects/p1': () => json(role),
    'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
    ...extra,
  })
}

describe('a venue’s holds and dates', () => {
  it('are set for the scene’s shoot days at once, with when a hold lapses', async () => {
    let days: Availability[] = []
    const { requests } = venueServer(() => days, {
      'PUT /api/locations/l1/availability': () => {
        days = ['2026-10-12', '2026-10-13', '2026-10-14'].map((date, i) => day({ id: `a${i}`, day: date }))
        return json(days)
      },
    })
    renderApp('/locations/l1')
    const user = await logIn()

    expect(await screen.findByText(/Nothing recorded yet/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Set a day' }))
    const form = screen.getByRole('form', { name: 'Set a day' })
    expect(within(form).getByLabelText('Day')).toHaveValue('2026-10-12')
    expect(within(form).getByLabelText('Last day')).toHaveValue('2026-10-14')
    await user.click(within(form).getByRole('radio', { name: 'Held' }))
    await user.type(within(form).getByLabelText('Lapses on'), '2026-10-05')
    await user.type(within(form).getByLabelText('Note'), 'Held by Jo, call back Friday')
    await user.click(within(form).getByRole('button', { name: 'Set 3 days' }))

    const list = within(await screen.findByRole('list', { name: 'Days at this venue' }))
    expect(list.getAllByRole('listitem')).toHaveLength(3)
    expect(list.getAllByRole('listitem')[0]).toHaveTextContent(`${formatDay('2026-10-12')}Helduntil ${formatDate('2026-10-05')}· by Ada`)
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({
      from: '2026-10-12',
      to: '2026-10-14',
      state: 'HELD',
      holdExpiresOn: '2026-10-05',
      note: 'Held by Jo, call back Friday',
    })
  })

  it('drop the lapse date for a state that cannot lapse, and refuse a backwards run', async () => {
    const { requests } = venueServer(() => [], { 'PUT /api/locations/l1/availability': () => json([]) })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Set a day' }))
    const form = within(screen.getByRole('form', { name: 'Set a day' }))
    await user.clear(form.getByLabelText('Last day'))
    await user.type(form.getByLabelText('Last day'), '2026-10-01')
    expect(form.getByText('The last day cannot be before the first.')).toBeInTheDocument()
    expect(form.getByRole('button', { name: 'Set day' })).toBeDisabled()
    await user.clear(form.getByLabelText('Last day'))
    await user.type(form.getByLabelText('Lapses on'), '2026-10-05')
    await user.click(form.getByRole('radio', { name: 'Unavailable' }))
    expect(form.queryByLabelText('Lapses on')).toBeNull()
    await user.click(form.getByRole('button', { name: 'Set day' }))

    await waitFor(() => expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({
      from: '2026-10-12',
      to: null,
      state: 'UNAVAILABLE',
      holdExpiresOn: null,
      note: null,
    }))
  })

  it('can be cleared a day at a time', async () => {
    let days = [day()]
    const { requests } = venueServer(() => days, {
      'DELETE /api/locations/l1/availability/2026-10-12': () => {
        days = []
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: `Clear ${formatDay('2026-10-12')}` }))

    expect(await screen.findByText(/Nothing recorded yet/)).toBeInTheDocument()
    expect(requests.some((r) => r.method === 'DELETE')).toBe(true)
  })

  it('are only read by a viewer', async () => {
    venueServer(() => [day()], {}, project({ role: 'VIEWER' }))
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByRole('list', { name: 'Days at this venue' })).toHaveTextContent('Held')
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Set a day' })).toBeNull())
    expect(screen.queryByRole('button', { name: /^Clear/ })).toBeNull()
  })
})

describe('the schedule’s clashes', () => {
  const base = {
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    'GET /api/projects/p1/moves': () => json({ days: [], warnAfterMinutes: 60, attribution: '' }),
  }

  it('are listed above the days, problems marked apart from warnings, with each venue’s state and the scene’s times', async () => {
    const withClashes: Schedule = {
      ...schedule,
      days: [
        {
          date: '2026-10-12',
          scenes: [
            scheduled({
              callTime: '07:30:00',
              wrapTime: '16:00:00',
              venues: [{ ...scheduled().venues[0], booking: { state: 'HELD', holdExpiresOn: '2026-10-05' } }],
            }),
          ],
        },
      ],
      conflicts: [
        {
          kind: 'HOLD_EXPIRES',
          problem: true,
          date: '2026-10-12',
          sceneIds: ['s1'],
          locationId: 'l1',
          venueName: 'Tom’s Diner',
          message: 'The hold on Tom’s Diner for Mon 12 Oct lapses on Mon 5 Oct, before the shoot. Renew it or confirm the booking.',
        },
        {
          kind: 'DOUBLE_BOOKED',
          problem: false,
          date: '2026-10-12',
          sceneIds: ['s1', 's2'],
          locationId: 'l1',
          venueName: 'Tom’s Diner',
          message: 'Scene 12 and scene 13 are both at Tom’s Diner on Mon 12 Oct; set their call and wrap times to check they do not overlap.',
        },
      ],
    }
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json(withClashes) })
    renderApp('/projects/p1?tab=schedule')
    await logIn()

    const clashes = within(await screen.findByRole('region', { name: 'Clashes: 1 to sort out, 1 to check' }))
    const [problem, warning] = clashes.getAllByRole('listitem')
    expect(problem).toHaveTextContent(/^Problem.*The hold on Tom’s Diner/)
    expect(warning).toHaveTextContent(/^Check.*both at Tom’s Diner/)
    expect(within(problem).getByRole('link', { name: 'Open Tom’s Diner' })).toHaveAttribute('href', '/locations/l1')
    const diner = within(screen.getByRole('list', { name: 'Confirmed for INT. DINER - NIGHT' }))
    expect(diner.getByText(`Held until ${formatDate('2026-10-05')}`)).toBeInTheDocument()
    expect(screen.getByText('07:30–16:00')).toBeInTheDocument()
  })

  it('come from the call and wrap times set with the dates', async () => {
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/schedule': () => json(schedule),
      'PUT /api/scenes/s1/shoot-dates': () => json(scene({ shootDateStart: '2026-10-12' })),
    })
    renderApp('/projects/p1?tab=schedule')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Change the dates of INT. DINER - NIGHT' }))
    const form = within(screen.getByRole('form', { name: 'Shoot dates of INT. DINER - NIGHT' }))
    await user.type(form.getByLabelText('Call'), '18:00')
    await user.type(form.getByLabelText('Wrap'), '02:00')
    expect(form.getByText('The next morning.')).toBeInTheDocument()
    await user.click(form.getByRole('button', { name: 'Save dates' }))

    await waitFor(() => expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({
      shootDateStart: '2026-10-12',
      shootDateEnd: '2026-10-12',
      callTime: '18:00',
      wrapTime: '02:00',
    }))
  })
})
