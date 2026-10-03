import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { OutreachDraft, OutreachReply } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, draft, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function serverFor(drafts: () => OutreachDraft[], extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project()),
    'GET /api/locations/l1/outreach-drafts?page=0&size=24': () => json(pageOf(drafts())),
    ...extra,
  })
}

const reply = (overrides: Partial<OutreachReply> = {}): OutreachReply => ({
  id: 'r1',
  draftId: 'd1',
  source: 'INBOUND',
  fromAddress: 'owner@diner.example',
  fromName: 'Sal',
  subject: 'Re: Filming at Tom’s Diner',
  text: 'Yes, Tuesdays work.',
  receivedAt: '2026-10-02T13:05:00Z',
  recordedBy: null,
  ...overrides,
})

describe('replies to an outreach email', () => {
  it('show the reply address, put it in Cc, and list what came back', async () => {
    const tracked = draft({ status: 'REPLIED', sentAt: '2026-10-01T10:00:00Z', replyTo: 'scout+abc123@replies.example.com', recipientEmail: 'owner@diner.example' })
    serverFor(() => [tracked], { 'GET /api/outreach-drafts/d1/replies?page=0&size=50': () => json(pageOf([reply()])) })
    renderApp('/locations/l1?tab=outreach')
    await logIn()

    expect(await screen.findByText('scout+abc123@replies.example.com')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open in email app' }).getAttribute('href')).toContain('cc=scout%2Babc123@replies.example.com')
    const replies = await screen.findByRole('region', { name: /Replies to/ })
    expect(within(replies).getByText('Yes, Tuesdays work.')).toBeInTheDocument()
    expect(within(replies).getByText('Sal')).toBeInTheDocument()
  })

  it('can be pasted in by hand, or the email just marked replied, when nothing tracks them', async () => {
    let drafts = [draft({ status: 'SENT', sentAt: '2026-10-01T10:00:00Z', recipientName: 'Sal', recipientEmail: 'owner@diner.example' })]
    const { requests } = serverFor(() => drafts, {
      'POST /api/outreach-drafts/d1/replies': (req) => {
        drafts = [{ ...drafts[0], status: 'REPLIED' }]
        return json(reply({ source: 'MANUAL', recordedBy: 'Ada', ...(req.body as object) }), 201)
      },
      'GET /api/outreach-drafts/d1/replies?page=0&size=50': () => json(pageOf([reply({ source: 'MANUAL', recordedBy: 'Ada', text: 'Call me Monday' })])),
    })
    renderApp('/locations/l1?tab=outreach')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Paste a reply or mark replied' }))
    expect(screen.getByLabelText('From (name)')).toHaveValue('Sal')
    await user.type(screen.getByLabelText('Their reply'), 'Call me Monday')
    await user.click(screen.getByRole('button', { name: 'Save the reply' }))

    expect(await screen.findByText('Call me Monday')).toBeInTheDocument()
    expect(screen.getByText('Pasted in by Ada')).toBeInTheDocument()
    await vi.waitFor(() =>
      expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ fromName: 'Sal', fromAddress: 'owner@diner.example', text: 'Call me Monday' }),
    )
  })
})
