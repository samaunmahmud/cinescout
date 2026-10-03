import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Agreement } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, locationVideos, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function agreement(overrides: Partial<Agreement> = {}): Agreement {
  return { id: 'ag1', locationId: 'l1', version: 1, sizeBytes: 4300, createdByName: 'Ada', createdAt: '2026-10-04T09:00:00Z', ...overrides }
}

function server(agreements: () => Agreement[], extra: Parameters<typeof fakeServer>[0] = {}, role = project()) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(role),
    'GET /api/locations/l1/outreach-drafts?page=0&size=24': () => json(pageOf([])),
    'GET /api/locations/l1/agreements?page=0&size=50': () => json(pageOf(agreements(), { size: 50 })),
    'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
    ...extra,
  })
}

describe('a venue’s location release', () => {
  it('is made from the venue’s details, a new version each time, and says it is not legal advice', async () => {
    let made: Agreement[] = []
    const { requests } = server(() => made, {
      'POST /api/locations/l1/agreements': () => {
        made = [agreement({ id: `ag${made.length + 1}`, version: made.length + 1 }), ...made]
        return json(made[0], 201)
      },
    })
    renderApp('/locations/l1?tab=outreach')
    const user = await logIn()

    expect(await screen.findByText(/It is not legal advice/)).toBeInTheDocument()
    await user.type(await screen.findByLabelText('Production company'), ' Harbour Films Ltd ')
    await user.click(screen.getByRole('button', { name: 'Make the release' }))
    expect(within(await screen.findByRole('list', { name: 'Versions of the release' })).getByText('Version 1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Make a new version' }))

    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Versions of the release' })).getAllByRole('listitem')).toHaveLength(2))
    expect(requests.filter((r) => r.method === 'POST').map((r) => r.body)).toEqual([
      { productionCompany: 'Harbour Films Ltd' },
      { productionCompany: 'Harbour Films Ltd' },
    ])
  })

  it('downloads as a PDF', async () => {
    server(() => [agreement()], {
      'GET /api/agreements/ag1/file': () =>
        new Response('%PDF-1.4', { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="location-release-toms-diner-v1.pdf"' } }),
    })
    const saved: string[] = []
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:pdf'), revokeObjectURL: vi.fn() }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      saved.push(this.download)
    })
    renderApp('/locations/l1?tab=outreach')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Download version 1' }))

    await vi.waitFor(() => expect(saved).toEqual(['location-release-toms-diner-v1.pdf']))
    click.mockRestore()
  })

  it('says why no new version could be made', async () => {
    server(() => [agreement()], {
      'POST /api/locations/l1/agreements': () => problem(409, 'Conflict', 'This venue already has 30 versions of its agreement; delete an old one first'),
    })
    renderApp('/locations/l1?tab=outreach')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Make a new version' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('delete an old one first')
  })

  it('can be downloaded but not made or deleted by a viewer', async () => {
    server(() => [agreement()], {}, project({ role: 'VIEWER' }))
    renderApp('/locations/l1?tab=outreach')
    await logIn()

    expect(await screen.findByRole('button', { name: 'Download version 1' })).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('form', { name: 'Make a location release' })).toBeNull())
    expect(screen.queryByRole('button', { name: 'Delete version 1' })).toBeNull()
  })
})
