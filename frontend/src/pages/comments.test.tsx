import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Crew, ProjectRole, VenueComment } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const crew: Crew = {
  members: [
    { userId: 'u1', displayName: 'Ada', email: ada.email, role: 'OWNER', joinedAt: '2026-09-01T10:00:00Z' },
    { userId: 'u2', displayName: 'Grace', email: 'grace@example.com', role: 'EDITOR', joinedAt: '2026-09-02T10:00:00Z' },
    { userId: 'u3', displayName: 'Gregor', email: 'gregor@example.com', role: 'VIEWER', joinedAt: '2026-09-03T10:00:00Z' },
  ],
  invites: [],
}

const comment = (overrides: Partial<VenueComment> = {}): VenueComment => ({
  id: 'c1',
  locationId: 'l1',
  parentId: null,
  authorId: 'u2',
  authorName: 'Grace',
  guest: false,
  body: 'Power is in the basement',
  mentions: [],
  edited: false,
  createdAt: '2026-10-02T10:00:00Z',
  updatedAt: '2026-10-02T10:00:00Z',
  replies: [],
  ...overrides,
})

function server(role: ProjectRole, threads: () => VenueComment[], extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project({ role })),
    'GET /api/projects/p1/members': () => json(crew),
    'GET /api/locations/l1/comments?page=0&size=24': () => json(pageOf(threads())),
    ...extra,
  })
}

describe('comments on a venue', () => {
  it('are a tab of the venue page, where a member posts one mentioning someone picked from the crew', async () => {
    let threads: VenueComment[] = []
    const { requests } = server('EDITOR', () => threads, {
      'POST /api/locations/l1/comments': (req) => {
        const body = req.body as { body: string; mentions: string[] }
        threads = [comment({ id: 'c9', authorId: 'u1', authorName: 'Ada', body: body.body, mentions: [{ userId: 'u2', displayName: 'Grace' }] })]
        return json(threads[0], 201)
      },
    })
    renderApp('/locations/l1?tab=comments')
    const user = await logIn()

    expect(await screen.findByText(/No comments yet/)).toBeInTheDocument()
    const box = screen.getByRole('combobox', { name: 'Add a comment' })
    await user.type(box, 'Can @Gr')
    const options = within(screen.getByRole('listbox', { name: 'Crew to mention' })).getAllByRole('option')
    expect(options.map((option) => option.textContent)).toEqual(['Grace grace@example.com', 'Gregor gregor@example.com'])
    expect(box).toHaveAttribute('aria-expanded', 'true')
    await user.keyboard('{Enter}')
    expect(box).toHaveValue('Can @Grace ')
    expect(box).toHaveAttribute('aria-expanded', 'false')
    await user.type(box, 'check the power?')
    await user.click(screen.getByRole('button', { name: 'Post comment' }))

    const posted = await screen.findByText('@Grace')
    expect(posted).toHaveClass('font-semibold')
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ body: 'Can @Grace check the power?', parentId: null, mentions: ['u2'] })
  })

  it('take replies in the thread, and a mention typed then deleted is not sent', async () => {
    let threads = [comment()]
    const { requests } = server('EDITOR', () => threads, {
      'POST /api/locations/l1/comments': (req) => {
        const reply = comment({ id: 'r1', parentId: 'c1', authorId: 'u1', authorName: 'Ada', body: (req.body as { body: string }).body })
        threads = [comment({ replies: [reply] })]
        return json(reply, 201)
      },
    })
    renderApp('/locations/l1?tab=comments')
    const user = await logIn()

    const thread = within((await screen.findByText('Power is in the basement')).closest('article')!)
    await user.click(thread.getByRole('button', { name: 'Reply' }))
    const box = thread.getByRole('combobox', { name: 'Reply to Grace' })
    await user.type(box, '@Gre')
    await user.keyboard('{Enter}')
    await user.clear(box)
    await user.type(box, 'Three-phase too')
    await user.click(thread.getByRole('button', { name: 'Reply' }))

    const replies = await thread.findByRole('list', { name: 'Replies to Grace' })
    expect(within(replies).getByText('Three-phase too')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ body: 'Three-phase too', parentId: 'c1', mentions: [] })
  })

  it('can be edited by their author and deleted by the owner, guests’ comments included', async () => {
    let threads = [
      comment({ id: 'mine', authorId: 'u1', authorName: 'Ada', body: 'Too loud?' }),
      comment({ id: 'guest', authorId: null, authorName: 'Wes', guest: true, body: 'Love the booths' }),
    ]
    const { requests } = server('OWNER', () => threads, {
      'PUT /api/comments/mine': (req) => {
        threads = [{ ...threads[0], body: (req.body as { body: string }).body, edited: true }, threads[1]]
        return json(threads[0])
      },
      'DELETE /api/comments/guest': () => {
        threads = [threads[0]]
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/locations/l1?tab=comments')
    const user = await logIn()

    const guest = within((await screen.findByText('Love the booths')).closest('article')!)
    expect(guest.getByText('Guest · director link')).toBeInTheDocument()
    expect(guest.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()

    const mine = within(screen.getByText('Too loud?').closest('article')!)
    await user.click(mine.getByRole('button', { name: 'Edit' }))
    const box = mine.getByRole('combobox', { name: 'Edit your comment' })
    await user.clear(box)
    await user.type(box, 'Too loud at night?')
    await user.click(mine.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText(/edited/)).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ body: 'Too loud at night?', mentions: [] })

    await user.click(guest.getByRole('button', { name: 'Delete the comment by Wes' }))
    await user.click(within(guest.getByRole('alertdialog')).getByRole('button', { name: 'Delete comment' }))
    await vi.waitFor(() => expect(screen.queryByText('Love the booths')).not.toBeInTheDocument())
  })

  it('are read-only for a viewer', async () => {
    server('VIEWER', () => [comment()])
    renderApp('/locations/l1?tab=comments')
    await logIn()

    expect(await screen.findByText('Power is in the basement')).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByRole('combobox', { name: /comment|Reply/ })).not.toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Reply' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Delete/ })).not.toBeInTheDocument()
  })
})
