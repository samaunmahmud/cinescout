import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ProjectOutreach } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function outreach(overrides: Partial<ProjectOutreach> = {}): ProjectOutreach {
  return {
    id: 'd1',
    locationId: 'l1',
    locationName: 'Tom’s Diner',
    sceneId: 's1',
    sceneNumber: 12,
    sceneTitle: 'INT. DINER - NIGHT',
    recipientName: 'Tom Miller',
    recipientEmail: 'tom@toms-diner.example',
    subject: 'Filming request: Night Shift at Tom’s Diner',
    tone: 'PROFESSIONAL',
    status: 'SENT',
    sentAt: '2026-09-21T09:30:00Z',
    createdAt: '2026-09-20T10:00:00Z',
    updatedAt: '2026-09-21T09:30:00Z',
    ...overrides,
  }
}

const unsent = outreach({ id: 'd2', locationId: 'l2', locationName: 'Sky Bar', subject: 'A night on your roof', recipientName: null, recipientEmail: null, status: 'DRAFT', sentAt: null })

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
  'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
}

describe('the outreach of a project', () => {
  it('is a tab listing every email with its venue, scene, recipient and how far it got', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/outreach-drafts?page=0&size=24': () => json(pageOf([unsent, outreach()])) })
    const { router } = renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('tab', { name: 'Outreach' }))

    expect(router.state.location.search).toBe('?tab=outreach')
    const [draft, sent] = within(await screen.findByRole('list', { name: 'Outreach emails' })).getAllByRole('article')
    expect(within(draft).getByRole('link', { name: 'A night on your roof' })).toHaveAttribute('href', '/locations/l2?tab=outreach')
    expect(draft).toHaveTextContent('Sky Bar')
    expect(draft).toHaveTextContent('No recipient yet')
    expect(draft).toHaveTextContent(/Draft.*Written/)
    expect(within(sent).getByRole('link', { name: 'Scene 12: INT. DINER - NIGHT' })).toHaveAttribute('href', '/scenes/s1')
    expect(sent).toHaveTextContent('To Tom Miller (tom@toms-diner.example)')
    expect(sent).toHaveTextContent(/Sent.*Sent .*2026/)
  })

  it('can be narrowed to the emails waiting for an answer', async () => {
    fakeServer({
      ...base,
      'GET /api/projects/p1/outreach-drafts?page=0&size=24': () => json(pageOf([unsent, outreach()])),
      'GET /api/projects/p1/outreach-drafts?status=SENT&page=0&size=24': () => json(pageOf([outreach()])),
      'GET /api/projects/p1/outreach-drafts?status=REPLIED&page=0&size=24': () => json(pageOf([])),
    })
    const { router } = renderApp('/projects/p1?tab=outreach')
    const user = await logIn()

    const filter = within(await screen.findByRole('group', { name: 'Filter by status' }))
    expect(filter.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'true')
    await screen.findByRole('article', { name: 'A night on your roof' })
    await user.click(filter.getByRole('button', { name: 'Sent' }))

    expect(router.state.location.search).toBe('?tab=outreach&status=SENT')
    await screen.findByRole('article', { name: /Filming request/ })
    expect(screen.queryByRole('article', { name: 'A night on your roof' })).not.toBeInTheDocument()

    await user.click(filter.getByRole('button', { name: 'Replied' }))
    expect(await screen.findByText('No emails in this state.')).toBeInTheDocument()
  })

  it('says how emails come about while there are none', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/outreach-drafts?page=0&size=24': () => json(pageOf([])) })
    renderApp('/projects/p1?tab=outreach')
    await logIn()

    expect(await screen.findByText(/No emails yet\. Open a venue/)).toBeInTheDocument()
  })
})
