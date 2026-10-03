import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Location, ProjectRole } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const answered = location({
  recce: {
    sockets: { value: 6, by: 'u2', byName: 'Grace', at: '2026-10-02T10:00:00Z' },
    toilets: { value: true, by: 'u2', byName: 'Grace', at: '2026-10-02T10:00:00Z' },
  },
})

function server(role: ProjectRole, current: Location, extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(current),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project({ role })),
    ...extra,
  })
}

describe('the tech recce', () => {
  it('is a tab of the venue page that sends only the answers changed and says who gave each', async () => {
    const { requests } = server('EDITOR', answered, {
      'PATCH /api/locations/l1/recce': () =>
        json({
          ...answered,
          recce: { ...answered.recce, ceilingHeightM: { value: 3.4, by: 'u1', byName: 'Ada', at: '2026-10-03T10:00:00Z' } },
        }),
    })
    renderApp('/locations/l1?tab=recce')
    const user = await logIn()

    expect(await screen.findByRole('heading', { name: 'Tech recce' })).toBeInTheDocument()
    expect(screen.getByLabelText('Sockets')).toHaveValue(6)
    expect(screen.getAllByText(/Grace ·/)).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Save the recce' })).toBeDisabled()

    await user.type(screen.getByLabelText('Ceiling height (m)'), '3.4')
    const toilets = screen.getByRole('group', { name: 'Toilets on site' })
    await user.click(within(toilets).getByRole('radio', { name: 'Not checked' }))
    await user.selectOptions(screen.getByLabelText('Stairs or lift'), 'LIFT')
    await user.click(within(screen.getByRole('group', { name: 'Ambient noise' })).getByRole('radio', { name: '2' }))
    expect(screen.getByText('4 unsaved')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save the recce' }))

    await vi.waitFor(() =>
      expect(requests.find((r) => r.method === 'PATCH')?.body).toEqual({ ceilingHeightM: 3.4, toilets: null, stairsOrLift: 'LIFT', ambientNoise: 2 }),
    )
  })

  it('is read as answers by a viewer', async () => {
    server('VIEWER', answered)
    renderApp('/locations/l1?tab=recce')
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Tech recce' })).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByRole('button', { name: 'Save the recce' })).not.toBeInTheDocument())
    expect(screen.getByText('Sockets').parentElement).toHaveTextContent('Sockets6Grace')
  })

  it('shows its answers as rows in Compare', async () => {
    const venues = [{ ...answered, status: 'SHORTLISTED' as const }, location({ id: 'l2', name: 'Corner Bistro', status: 'SHORTLISTED' })]
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/scenes/s1/locations?page=0&size=100': () => json(pageOf(venues, { size: 100 })),
      'GET /api/scenes/s1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    })
    renderApp('/scenes/s1/compare')
    await logIn()

    const toilets = (await screen.findByRole('rowheader', { name: 'Toilets on site' })).closest('tr')!
    expect(within(toilets).getByText('Yes')).toBeInTheDocument()
    expect(within(toilets).getByText('Not checked')).toBeInTheDocument()
  })
})
