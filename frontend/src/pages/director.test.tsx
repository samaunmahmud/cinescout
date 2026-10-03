import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DirectorCall, PublicShortlist, ShortlistVenue } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const call = (overrides: Partial<DirectorCall> = {}): DirectorCall => ({
  id: 'c1',
  locationId: 'l1',
  guestName: 'Wes',
  verdict: 'APPROVE',
  comment: 'Love the booths',
  createdAt: '2026-10-02T10:00:00Z',
  updatedAt: '2026-10-02T10:00:00Z',
  ...overrides,
})

const venue = (overrides: Partial<ShortlistVenue> = {}): ShortlistVenue => ({
  id: 'l1',
  sceneId: 's1',
  sceneTitle: 'INT. DINER - NIGHT',
  name: 'Moonlight Diner',
  address: '12 Main St, Brooklyn',
  latitude: null,
  longitude: null,
  imageUrl: null,
  fitScore: 82,
  bookingFriction: 'COMMERCIAL',
  frictionNote: 'Book two weeks ahead',
  warnings: ['Busy street at night'],
  status: 'SHORTLISTED',
  fitReason: null,
  notes: null,
  quote: null,
  responses: [],
  ...overrides,
})

const shortlist = (overrides: Partial<PublicShortlist> = {}): PublicShortlist => ({
  projectTitle: 'Night Shift',
  sceneTitle: null,
  sharedBy: 'Ada',
  showPrivate: false,
  venues: pageOf([venue()]),
  ...overrides,
})

afterEach(() => localStorage.clear())

describe('a shortlist sent to the director', () => {
  it('needs no account, shows each venue and takes a call on it under the name typed', async () => {
    let responses: DirectorCall[] = []
    const { requests } = fakeServer({
      'GET /api/public/shortlists/tok?page=0&size=24': () => json(shortlist({ venues: pageOf([venue({ responses })]) })),
      'POST /api/public/shortlists/tok/venues/l1/response': (req) => {
        responses = [call({ ...(req.body as object), comment: (req.body as { comment: string | null }).comment })]
        return json(responses[0])
      },
    })
    renderApp('/shortlist/tok')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { level: 1, name: 'Night Shift' })).toBeInTheDocument()
    expect(screen.getByText(/Ada sent you the venues/)).toBeInTheDocument()
    const card = within(screen.getByRole('article', { name: 'Moonlight Diner' }))
    expect(card.getByLabelText('Fit 82 out of 100')).toBeInTheDocument()
    expect(card.getByText('Book two weeks ahead')).toBeInTheDocument()
    expect(card.getByText('Busy street at night')).toBeInTheDocument()
    expect(card.queryByText('Production notes')).not.toBeInTheDocument()

    const send = card.getByRole('button', { name: 'Send my call' })
    await user.click(card.getByRole('radio', { name: 'Approve' }))
    expect(send).toBeDisabled()
    await user.type(screen.getByLabelText('Your name'), ' Wes ')
    await user.type(card.getByLabelText('A note for the production (optional)'), 'Love the booths')
    await user.click(send)

    expect(await card.findByRole('status')).toHaveTextContent('Sent.')
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ guestName: 'Wes', verdict: 'APPROVE', comment: 'Love the booths' })
    expect(await card.findByRole('list', { name: 'Director’s call' })).toHaveTextContent('ApprovedWes')
    expect(localStorage.getItem('cinescout.guestName')).toBe('Wes')
  })

  it('starts from the call the remembered name gave before, and shows the private details only when shared', async () => {
    localStorage.setItem('cinescout.guestName', 'wes')
    fakeServer({
      'GET /api/public/shortlists/tok?page=0&size=24': () =>
        json(
          shortlist({
            showPrivate: true,
            venues: pageOf([venue({ fitReason: 'Booths and neon, as written', notes: 'Owner is keen', quote: '$400 an hour', responses: [call({ verdict: 'MAYBE' })] })]),
          }),
        ),
    })
    renderApp('/shortlist/tok')

    const card = within(await screen.findByRole('article', { name: 'Moonlight Diner' }))
    expect(card.getByRole('radio', { name: 'Maybe' })).toBeChecked()
    expect(card.getByLabelText('A note for the production (optional)')).toHaveValue('Love the booths')
    expect(card.getByRole('button', { name: 'Update my call' })).toBeEnabled()
    expect(card.getByText('Booths and neon, as written')).toBeInTheDocument()
    expect(card.getByText('$400 an hour')).toBeInTheDocument()
    expect(card.getByText('Owner is keen')).toBeInTheDocument()
  })

  it('says plainly when the link has been withdrawn', async () => {
    fakeServer({ 'GET /api/public/shortlists/gone?page=0&size=24': () => problem(404, 'Not Found', 'This shortlist is not shared, or no longer is') })
    renderApp('/shortlist/gone')

    expect(await screen.findByRole('heading', { name: 'Not shared' })).toBeInTheDocument()
  })
})

describe('the director link on a scene', () => {
  const sceneServer = (role: 'OWNER' | 'EDITOR' | 'VIEWER', extra: Parameters<typeof fakeServer>[0] = {}) =>
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ role })),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf([location()])),
      ...extra,
    })

  it('is made by the owner, private details included if they choose, and can be withdrawn', async () => {
    const { requests } = sceneServer('OWNER', {
      'GET /api/scenes/s1/director-link': () => problem(404, 'Not Found', 'No link'),
      'POST /api/scenes/s1/director-link': () => json({ token: 'tok', sceneId: 's1', showPrivate: true, createdAt: '2026-10-03T10:00:00Z' }),
      'DELETE /api/scenes/s1/director-link': () => new Response(null, { status: 204 }),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Send to the director' }))
    await user.click(await screen.findByRole('checkbox', { name: /Also show private notes/ }))
    await user.click(screen.getByRole('button', { name: 'Make a director link' }))

    expect(await screen.findByLabelText('Director link')).toHaveValue(`${window.location.origin}/shortlist/tok`)
    expect(screen.getByText(/private notes and quotes included/)).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ showPrivate: true })

    await user.click(screen.getByRole('button', { name: 'Stop sharing' }))
    expect(await screen.findByRole('button', { name: 'Make a director link' })).toBeInTheDocument()
  })

  it('is shown to a viewer, who can neither make nor withdraw one', async () => {
    sceneServer('VIEWER', {
      'GET /api/scenes/s1/director-link': () => json({ token: 'tok', sceneId: 's1', showPrivate: false, createdAt: '2026-10-03T10:00:00Z' }),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Send to the director' }))
    expect(await screen.findByLabelText('Director link')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Stop sharing' })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('only offers an editor the plain link', async () => {
    sceneServer('EDITOR', { 'GET /api/scenes/s1/director-link': () => problem(404, 'Not Found', 'No link') })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Send to the director' }))
    expect(await screen.findByRole('button', { name: 'Make a director link' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })
})

describe("the director's call", () => {
  it('is stamped on the venue page with each guest’s comment', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/locations/l1': () => json(location()),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/locations/l1/director-responses?page=0&size=100': () =>
        json(pageOf([call(), call({ id: 'c2', guestName: 'Sofia', verdict: 'NO', comment: 'Too bright' })], { size: 100 })),
      'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    })
    renderApp('/locations/l1')
    await logIn()

    const calls = await screen.findByRole('list', { name: 'Director’s call' })
    expect(screen.getByRole('heading', { name: 'Director’s call' })).toBeInTheDocument()
    expect(within(calls).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      expect.stringContaining('ApprovedWes'),
      expect.stringContaining('PassedSofia'),
    ])
    expect(within(calls).getByText('Too bright')).toBeInTheDocument()
  })

  it('is a row of Compare', async () => {
    const venues = [location({ id: 'l1', status: 'SHORTLISTED' }), location({ id: 'l2', name: 'Corner Bistro', status: 'SHORTLISTED' })]
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/scenes/s1/locations?page=0&size=100': () => json(pageOf(venues, { size: 100 })),
      'GET /api/scenes/s1/director-responses?page=0&size=100': () => json(pageOf([call({ locationId: 'l2', verdict: 'MAYBE' })], { size: 100 })),
    })
    renderApp('/scenes/s1/compare')
    await logIn()

    const row = (await screen.findByRole('rowheader', { name: 'Director’s call' })).closest('tr')!
    await vi.waitFor(() => expect(within(row).getByText('Maybe')).toBeInTheDocument())
    expect(within(row).getByText('No call yet')).toBeInTheDocument()
  })
})
