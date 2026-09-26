import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { OutreachDraft } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, draft, location, logIn, scene, locationVideos } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function serverFor(drafts: OutreachDraft[], extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/locations/l1/outreach-drafts': () => json(drafts),
    'GET /api/locations/l1/videos': () => json(locationVideos({ videos: [] })),
    ...extra,
  })
}

const dateOf = (iso: string) => new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(iso))

describe("a location's outreach emails", () => {
  it('are listed newest first, ready to send from the email app', async () => {
    serverFor([
      draft({ id: 'd2', subject: 'Following up', recipientName: null, recipientEmail: null, tone: 'CONCISE', createdAt: '2026-09-22T10:00:00Z' }),
      draft({ status: 'SENT', sentAt: '2026-09-21T09:30:00Z' }),
    ])
    renderApp('/locations/l1')
    await logIn()

    const [newest, older] = within(await screen.findByRole('list', { name: 'Emails' })).getAllByRole('article')

    expect(within(newest).getByRole('heading')).toHaveTextContent('Following up')
    expect(newest).toHaveTextContent('No recipient yet · Concise')
    expect(within(newest).getByRole('link', { name: 'Open in email app' })).toHaveAttribute(
      'href',
      expect.stringMatching(/^mailto:\?subject=Following%20up&body=/),
    )

    expect(older).toHaveTextContent('To Tom Miller <tom@toms-diner.example> · Professional')
    expect(older).toHaveTextContent(`Sent ${dateOf('2026-09-21T09:30:00Z')}`)
    expect(older).toHaveTextContent('would love to film one night scene in your diner')
    expect(within(older).getByRole('link', { name: 'Open in email app' })).toHaveAttribute(
      'href',
      expect.stringMatching(/^mailto:tom@toms-diner\.example\?subject=Filming%20request/),
    )
    expect(within(older).getByLabelText(/^Status of/)).toHaveValue('SENT')
  })

  it('are written by the AI on request, addressed to the last recipient by default', async () => {
    const written = draft({ id: 'd9', subject: 'Could we film at Tom’s?', tone: 'FRIENDLY' })
    const { requests } = serverFor([draft()], {
      'POST /api/locations/l1/outreach-drafts/generate': () => json(written, 201),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Write an email' }))
    const form = screen.getByRole('form', { name: 'Write an email' })
    expect(within(form).getByLabelText('Recipient name')).toHaveValue('Tom Miller')
    expect(within(form).getByLabelText('Recipient email')).toHaveValue('tom@toms-diner.example')
    await user.click(within(form).getByRole('radio', { name: /Friendly/ }))
    await user.clear(within(form).getByLabelText('Recipient name'))
    await user.type(within(form).getByLabelText('Anything to mention'), ' We can shoot on a Monday. ')
    await user.click(within(form).getByRole('button', { name: 'Draft email' }))

    const articles = await screen.findAllByRole('article')
    expect(within(articles[0]).getByRole('heading')).toHaveTextContent('Could we film at Tom’s?')
    expect(articles).toHaveLength(2)
    expect(screen.queryByRole('form', { name: 'Write an email' })).toBeNull()
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      tone: 'FRIENDLY',
      recipientName: null,
      recipientEmail: 'tom@toms-diner.example',
      additionalContext: 'We can shoot on a Monday.',
    })
  })

  it('catch a mistyped recipient address before asking the AI', async () => {
    const { requests } = serverFor([])
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Write an email' }))
    await user.type(screen.getByLabelText('Recipient email'), 'tom.diner.example')

    expect(screen.getByText('Enter an email address like owner@example.com.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Draft email' })).toBeDisabled()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)
  })

  it('explain when the server cannot write them', async () => {
    serverFor([], {
      'POST /api/locations/l1/outreach-drafts/generate': () => problem(503, 'Not available', 'Outreach generation is not configured on this server'),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Write an email' }))
    await user.click(screen.getByRole('button', { name: 'Draft email' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Outreach generation is not configured on this server')
    expect(screen.getByRole('form', { name: 'Write an email' })).toBeInTheDocument()
  })

  it('can be edited, sending the whole email back', async () => {
    const { requests } = serverFor([draft({ tone: 'FRIENDLY', status: 'SENT', sentAt: '2026-09-21T09:30:00Z' })], {
      'PUT /api/outreach-drafts/d1': (req) => json(draft({ ...(req.body as object) })),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const form = screen.getByRole('form', { name: 'Edit email' })
    await user.clear(within(form).getByLabelText('Subject'))
    await user.type(within(form).getByLabelText('Subject'), 'Filming at Tom’s ')
    await user.clear(within(form).getByLabelText('Recipient email'))
    await user.click(within(form).getByRole('button', { name: 'Save email' }))

    expect(await screen.findByRole('heading', { name: 'Filming at Tom’s' })).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({
      subject: 'Filming at Tom’s',
      body: draft().body,
      tone: 'FRIENDLY',
      status: 'SENT',
      recipientName: 'Tom Miller',
      recipientEmail: null,
    })
  })

  it('cannot be saved with a mistyped address or an empty message', async () => {
    const { requests } = serverFor([draft()])
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const form = screen.getByRole('form', { name: 'Edit email' })
    const save = within(form).getByRole('button', { name: 'Save email' })
    await user.type(within(form).getByLabelText('Recipient email'), ' x')
    expect(within(form).getByText('Enter an email address like owner@example.com.')).toBeInTheDocument()
    expect(save).toBeDisabled()

    await user.clear(within(form).getByLabelText('Recipient email'))
    await user.clear(within(form).getByLabelText('Message'))
    expect(save).toBeDisabled()
    expect(requests.some((r) => r.method === 'PUT')).toBe(false)
  })

  it('are marked as sent, keeping everything else', async () => {
    const { requests } = serverFor([draft()], {
      'PUT /api/outreach-drafts/d1': (req) => json(draft({ ...(req.body as object), sentAt: '2026-09-26T08:00:00Z' })),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.selectOptions(await screen.findByLabelText(/^Status of “Filming request/), 'Sent')

    expect(await screen.findByText(`Sent ${dateOf('2026-09-26T08:00:00Z')}`)).toBeInTheDocument()
    const { status, ...rest } = requests.find((r) => r.method === 'PUT')?.body as Record<string, unknown>
    expect(status).toBe('SENT')
    expect(rest).toEqual({
      subject: draft().subject,
      body: draft().body,
      tone: 'PROFESSIONAL',
      recipientName: 'Tom Miller',
      recipientEmail: 'tom@toms-diner.example',
    })
  })

  it('can be copied with the subject line', async () => {
    serverFor([draft()])
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Copy' }))

    expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument()
    await expect(navigator.clipboard.readText()).resolves.toBe(`Subject: ${draft().subject}\n\n${draft().body}`)
  })

  it('are deleted after confirmation', async () => {
    const { requests } = serverFor([draft()], { 'DELETE /api/outreach-drafts/d1': () => new Response(null, { status: 204 }) })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Delete' }))
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete email' }))

    expect(await screen.findByText(/No emails yet/)).toBeInTheDocument()
    expect(requests.filter((r) => r.method === 'DELETE').map((r) => r.path)).toEqual(['/api/outreach-drafts/d1'])
  })
})
