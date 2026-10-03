import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { OutreachDraft, ProjectOutreach, ProjectSettings } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, draft, location, locationVideos, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function venueServer(drafts: OutreachDraft[], extra: Parameters<typeof fakeServer>[0] = {}, role = project()) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(role),
    'GET /api/locations/l1/outreach-drafts?page=0&size=24': () => json(pageOf(drafts)),
    'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
    ...extra,
  })
}

const unanswered = draft({ status: 'SENT', sentAt: '2026-09-21T09:30:00Z', followUpFlaggedAt: '2026-09-27T07:17:00Z' })

describe('follow-ups', () => {
  it('flag an unanswered email and draft a chaser for it on request', async () => {
    const chaser = draft({ id: 'd9', subject: 'Re: Filming request: Night Shift at Tom’s Diner', followUpOfId: 'd1', body: 'Hello Tom,\n\nJust following up.\n\nAda' })
    const drafts = [unanswered]
    const { requests } = venueServer(drafts, {
      'POST /api/outreach-drafts/d1/follow-up': () => {
        drafts.splice(0, 1, chaser, { ...unanswered, followUpFlaggedAt: null })
        return json(chaser, 201)
      },
    })
    renderApp('/locations/l1?tab=outreach')
    const user = await logIn()

    const card = await screen.findByRole('article', { name: /^Filming request/ })
    expect(card).toHaveTextContent('Follow up')
    expect(card).toHaveTextContent(/No answer since/)
    await user.click(within(card).getByRole('button', { name: 'Draft follow-up' }))

    const [first, second] = within(await screen.findByRole('list', { name: 'Emails' })).getAllByRole('article')
    expect(within(first).getByRole('heading')).toHaveTextContent('Re: Filming request')
    expect(first).toHaveTextContent('Follow-up · To Tom Miller')
    expect(second).not.toHaveTextContent('No answer since')
    expect(requests.filter((r) => r.method === 'POST').map((r) => r.path)).toEqual(['/api/outreach-drafts/d1/follow-up'])
  })

  it('can be drafted for any sent email, but not one that is unsent or answered, nor by a viewer', async () => {
    venueServer([draft({ id: 'd3', subject: 'Unsent' }), draft({ id: 'd2', subject: 'Answered', status: 'REPLIED', sentAt: '2026-09-21T09:30:00Z' }), draft({ subject: 'Sent', status: 'SENT', sentAt: '2026-09-29T09:30:00Z' })], {
      'GET /api/outreach-drafts/d2/replies?page=0&size=50': () => json(pageOf([], { size: 50 })),
    })
    renderApp('/locations/l1?tab=outreach')
    await logIn()

    expect(within(await screen.findByRole('article', { name: 'Sent' })).getByRole('button', { name: 'Draft follow-up' })).toBeInTheDocument()
    expect(within(screen.getByRole('article', { name: 'Unsent' })).queryByRole('button', { name: 'Draft follow-up' })).toBeNull()
    expect(within(screen.getByRole('article', { name: 'Answered' })).queryByRole('button', { name: 'Draft follow-up' })).toBeNull()
  })

  it('are not offered to a viewer', async () => {
    venueServer([unanswered], {}, project({ role: 'VIEWER' }))
    renderApp('/locations/l1?tab=outreach')
    await logIn()

    const card = await screen.findByRole('article', { name: /^Filming request/ })
    expect(card).toHaveTextContent('Follow up')
    // Once the role is known, the editing controls go.
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Write an email' })).toBeNull())
    expect(within(card).queryByRole('button', { name: 'Draft follow-up' })).toBeNull()
  })

  it('say plainly when the AI cannot write one', async () => {
    venueServer([unanswered], {
      'POST /api/outreach-drafts/d1/follow-up': () => problem(409, 'Conflict', 'Mark this email as sent before drafting a follow-up'),
    })
    renderApp('/locations/l1?tab=outreach')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Draft follow-up' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Mark this email as sent before drafting a follow-up')
  })

  it('are counted on the project poster', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () =>
        json(pageOf([project({ followUpCount: 2 }), project({ id: 'p2', title: 'Paper Moons', followUpCount: 0 })])),
    })
    renderApp('/projects')
    await logIn()

    expect(await screen.findByRole('link', { name: /Night Shift/ })).toHaveTextContent('2 to follow up')
    expect(screen.getByRole('link', { name: /Paper Moons/ })).not.toHaveTextContent('to follow up')
  })

  it('have their own filter on the project’s outreach tab', async () => {
    const row: ProjectOutreach = {
      id: 'd1',
      locationId: 'l1',
      locationName: 'Tom’s Diner',
      sceneId: 's1',
      sceneNumber: 12,
      sceneTitle: 'INT. DINER - NIGHT',
      recipientName: 'Tom Miller',
      recipientEmail: null,
      subject: 'Filming request',
      tone: 'PROFESSIONAL',
      status: 'SENT',
      sentAt: '2026-09-21T09:30:00Z',
      followUpFlaggedAt: '2026-09-27T07:17:00Z',
      followUpOfId: null,
      createdAt: '2026-09-20T10:00:00Z',
      updatedAt: '2026-09-27T07:17:00Z',
    }
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ followUpCount: 1 })),
      'GET /api/projects/p1/outreach-drafts?page=0&size=24': () => json(pageOf([])),
      'GET /api/projects/p1/outreach-drafts?followUp=true&page=0&size=24': () => json(pageOf([row])),
    })
    const { router } = renderApp('/projects/p1?tab=outreach')
    const user = await logIn()

    await user.click(within(await screen.findByRole('group', { name: 'Filter by status' })).getByRole('button', { name: 'Follow up' }))

    expect(router.state.location.search).toBe('?tab=outreach&status=FOLLOW_UP')
    expect(await screen.findByRole('article', { name: 'Filming request' })).toHaveTextContent(/Sent.*Follow up/)
  })

  it('come after the number of days set on the project’s settings page', async () => {
    let stored: ProjectSettings = { followUpDays: 5 }
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/members': () => json({ members: [], invites: [] }),
      'GET /api/projects/p1/settings': () => json(stored),
      'PUT /api/projects/p1/settings': (req) => {
        stored = req.body as ProjectSettings
        return json(stored)
      },
    })
    renderApp('/projects/p1/settings?tab=outreach')
    const user = await logIn()

    const days = await screen.findByLabelText('Days before a follow-up')
    expect(days).toHaveValue(5)
    await user.clear(days)
    await user.type(days, '0')
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    await user.clear(days)
    await user.type(days, '3')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Saved. An email is flagged for a follow-up after 3 days without an answer.')
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ followUpDays: 3 })
  })

  it('show a viewer the setting without a form', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ role: 'VIEWER' })),
      'GET /api/projects/p1/members': () => json({ members: [], invites: [] }),
      'GET /api/projects/p1/settings': () => json({ followUpDays: 1 }),
    })
    renderApp('/projects/p1/settings?tab=outreach')
    await logIn()

    expect(await screen.findByText('An email is flagged for a follow-up after 1 day without an answer.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Days before a follow-up')).toBeNull()
  })
})
