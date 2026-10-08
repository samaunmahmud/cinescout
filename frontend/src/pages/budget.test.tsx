import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Budget, ProjectRole } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const empty: Budget = {
  currency: 'GBP',
  total: 5000,
  totals: { planned: 0, committed: 0, paid: 0, remaining: 5000 },
  byCategory: [],
  items: [],
  unbudgetedVenues: [{ locationId: 'l1', name: 'Starlite Diner', sceneId: 's1', sceneTitle: 'INT. DINER - NIGHT', quote: '£800 a day', shootDays: 2 }],
}

const withDiner: Budget = {
  ...empty,
  totals: { planned: 1600, committed: 1600, paid: 0, remaining: 3400 },
  byCategory: [{ category: 'VENUE', amount: 1600 }],
  items: [
    {
      id: 'b1', category: 'VENUE', label: 'Starlite Diner, 2 days', amount: 1600, status: 'COMMITTED', note: 'Quote: £800 a day',
      locationId: 'l1', venueName: 'Starlite Diner', sceneId: 's1', addedByName: 'Ada', createdAt: '2026-10-08T10:00:00Z', updatedAt: '2026-10-08T10:00:00Z',
    },
  ],
  unbudgetedVenues: [],
}

function server(role: ProjectRole, extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/projects/p1': () => json(project({ role })),
    'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    'GET /api/projects/p1/budget': () => json(empty),
    ...extra,
  })
}

describe('the budget', () => {
  it('adds a confirmed venue at its quoted day rate times the shoot days, and adds a line by hand', async () => {
    let current = empty
    const { requests } = server('OWNER', {
      'GET /api/projects/p1/budget': () => json(current),
      'POST /api/projects/p1/budget/items': (req) => {
        const body = req.body as { label: string }
        current = body.label.startsWith('Starlite')
          ? withDiner
          : { ...withDiner, totals: { planned: 1850, committed: 1600, paid: 0, remaining: 3150 }, items: [...withDiner.items, { ...withDiner.items[0], id: 'b2', category: 'PERMIT', label: body.label, amount: 250, status: 'ESTIMATE', locationId: null, venueName: null, note: null }] }
        return json(current, 201)
      },
    })
    renderApp('/projects/p1?tab=budget')
    const user = await logIn()

    expect(await screen.findAllByText('£5,000')).toHaveLength(2) // the total, and all of it left
    await user.click(screen.getByRole('button', { name: 'Add Starlite Diner to the budget' }))
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      category: 'VENUE', label: 'Starlite Diner, 2 days', amount: 1600, status: 'COMMITTED', note: 'Quote: £800 a day', locationId: 'l1',
    })
    const lines = await screen.findByRole('list', { name: 'Budget lines' })
    expect(within(lines).getByText('Starlite Diner, 2 days')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Confirmed venues not in the budget yet' })).not.toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Planned against the total' })).toHaveAttribute('aria-valuenow', '32')

    await user.click(screen.getByRole('button', { name: 'Add a line' }))
    await user.type(screen.getByLabelText(/What for/), 'Street permit')
    await user.type(screen.getByLabelText(/Amount \(GBP\)/), '250')
    await user.selectOptions(screen.getByLabelText('Category'), 'PERMIT')
    await user.click(screen.getByRole('button', { name: 'Add the line' }))

    await vi.waitFor(() => expect(within(screen.getByRole('list', { name: 'Budget lines' })).getByText('Street permit')).toBeInTheDocument())
    expect(requests.filter((r) => r.method === 'POST')[1].body).toEqual({
      category: 'PERMIT', label: 'Street permit', amount: 250, status: 'ESTIMATE', note: null, locationId: null,
    })
    expect(screen.getByText('£3,150')).toBeInTheDocument()
  })

  it('shows a viewer the figures without the controls', async () => {
    server('VIEWER', { 'GET /api/projects/p1/budget': () => json(withDiner) })
    renderApp('/projects/p1?tab=budget')
    await logIn()

    expect(await screen.findByText('Starlite Diner, 2 days')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add a line' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: /Stage of/ })).not.toBeInTheDocument()
  })
})
