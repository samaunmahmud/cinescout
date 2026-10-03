import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Crew, Invite, InvitePreview, Member } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, PASSWORD, pageOf, project } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const me: Member = { userId: 'u1', displayName: 'Ada', email: ada.email, role: 'OWNER', joinedAt: '2026-09-01T10:00:00Z' }
const grace: Member = { userId: 'u2', displayName: 'Grace', email: 'grace@example.com', role: 'EDITOR', joinedAt: '2026-09-05T10:00:00Z' }
const crew = (overrides: Partial<Crew> = {}): Crew => ({ members: [me, grace], invites: [], ...overrides })
const invite = (overrides: Partial<Invite> = {}): Invite => ({
  id: 'i1',
  email: 'new@example.com',
  role: 'VIEWER',
  createdAt: '2026-10-01T10:00:00Z',
  expiresAt: '2026-10-08T10:00:00Z',
  token: null,
  ...overrides,
})

describe('the crew page', () => {
  it('lets the owner add an account that exists, and it joins at once', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/members': () => json(crew()),
      'POST /api/projects/p1/members': () =>
        json({ member: { userId: 'u3', displayName: 'Hedy', email: 'hedy@example.com', role: 'VIEWER', joinedAt: '2026-10-03T10:00:00Z' }, invite: null }, 201),
    })
    renderApp('/projects/p1/settings')
    const user = await logIn()

    expect(await screen.findByRole('heading', { level: 1, name: 'Crew' })).toBeInTheDocument()
    const members = within(await screen.findByRole('list', { name: 'Members' }))
    expect(members.getByText('Grace')).toBeInTheDocument()
    expect(members.getByRole('combobox', { name: 'Role of Grace' })).toHaveValue('EDITOR')

    await user.type(screen.getByLabelText('Email'), ' hedy@example.com ')
    await user.selectOptions(screen.getByLabelText('Role'), 'VIEWER')
    await user.click(screen.getByRole('button', { name: 'Add to crew' }))

    expect(await screen.findByText('Hedy joined as viewer.')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ email: 'hedy@example.com', role: 'VIEWER' })
  })

  it('gives the owner a link to pass on for someone without an account, and lists open invites to withdraw', async () => {
    let open: Invite[] = []
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/members': () => json(crew({ invites: open })),
      'POST /api/projects/p1/members': () => {
        open = [invite()]
        return json({ member: null, invite: invite({ token: 'secret-token' }) }, 201)
      },
      'DELETE /api/projects/p1/invites/i1': () => {
        open = []
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/projects/p1/settings')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Email'), 'new@example.com')
    await user.click(screen.getByRole('button', { name: 'Add to crew' }))

    expect(await screen.findByLabelText('Invite link')).toHaveValue(`${window.location.origin}/invite/secret-token`)
    const invites = within(await screen.findByRole('list', { name: 'Open invites' }))
    expect(invites.getByText('new@example.com')).toBeInTheDocument()

    await user.click(invites.getByRole('button', { name: 'Withdraw the invite to new@example.com' }))
    await vi.waitFor(() => expect(screen.queryByRole('list', { name: 'Open invites' })).not.toBeInTheDocument())
    expect(requests.some((r) => r.method === 'DELETE' && r.path === '/api/projects/p1/invites/i1')).toBe(true)
  })

  it('changes a role and hands ownership over', async () => {
    let members = [me, grace]
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ role: members[0].role })),
      'GET /api/projects/p1/members': () => json(crew({ members })),
      'PUT /api/projects/p1/members/u2': () => {
        members = [me, { ...grace, role: 'VIEWER' }]
        return json(members[1])
      },
      'POST /api/projects/p1/transfer': () => {
        members = [{ ...me, role: 'EDITOR' }, { ...grace, role: 'OWNER' }]
        return json(crew({ members }))
      },
    })
    renderApp('/projects/p1/settings')
    const user = await logIn()

    await user.selectOptions(await screen.findByRole('combobox', { name: 'Role of Grace' }), 'VIEWER')
    await vi.waitFor(() => expect(screen.getByRole('combobox', { name: 'Role of Grace' })).toHaveValue('VIEWER'))
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ role: 'VIEWER' })

    await user.click(screen.getByRole('button', { name: 'Make owner' }))
    await user.click(await screen.findByRole('button', { name: 'Hand ownership over' }))

    expect(await screen.findByText(/You are this project’s/)).toHaveTextContent('You are this project’s editor')
    expect(screen.queryByRole('button', { name: 'Add to crew' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Role of Grace' })).not.toBeInTheDocument()
    expect(requests.find((r) => r.path === '/api/projects/p1/transfer')?.body).toEqual({ userId: 'u2' })
  })

  it('lets a member who is not the owner see the crew and leave', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ role: 'VIEWER' })),
      'GET /api/projects/p1/members': () => json(crew({ members: [{ ...grace, role: 'OWNER' }, { ...me, role: 'VIEWER' }] })),
      'DELETE /api/projects/p1/members/u1': () => new Response(null, { status: 204 }),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])),
    })
    const { router } = renderApp('/projects/p1/settings')
    const user = await logIn()

    const members = within(await screen.findByRole('list', { name: 'Members' }))
    expect(members.getByText('Owner')).toBeInTheDocument()
    expect(members.getByText('Viewer')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add to crew' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Leave project' }))
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Leave project' }))

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/projects'))
    expect(requests.some((r) => r.method === 'DELETE')).toBe(true)
  })
})

describe('a viewer on the project page', () => {
  it('is offered the crew but not editing, archiving or deleting the project', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ role: 'VIEWER' })),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    })
    renderApp('/projects/p1')
    await logIn()

    expect(await screen.findByRole('heading', { level: 1, name: 'Night Shift' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Crew' })).toHaveAttribute('href', '/projects/p1/settings')
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })
})

describe('an invite link', () => {
  const preview = (overrides: Partial<InvitePreview> = {}): InvitePreview => ({
    projectId: 'p1',
    projectTitle: 'Night Shift',
    invitedBy: 'Grace',
    email: ada.email,
    role: 'EDITOR',
    expiresAt: '2026-10-08T10:00:00Z',
    state: 'OPEN',
    ...overrides,
  })

  it('brings a visitor without an account back to the invite after registering, and joins the project', async () => {
    const { requests } = fakeServer({
      'POST /api/auth/register': () => json(ada, 201),
      'GET /api/auth/me': () => json(ada),
      'GET /api/invites/tok': () => json(preview()),
      'POST /api/invites/tok/accept': () => json(preview({ state: 'ACCEPTED' })),
      'GET /api/projects/p1': () => json(project({ role: 'EDITOR' })),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    })
    const { router } = renderApp('/invite/tok')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Create an account' }))
    await user.type(await screen.findByLabelText('Name'), 'Ada')
    await user.type(screen.getByLabelText('Email'), ada.email)
    await user.type(screen.getByLabelText('Password'), PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Night Shift' })).toBeInTheDocument()
    expect(screen.getByText(/invited/)).toHaveTextContent('Grace invited ada@example.com to join as an editor')
    await user.click(screen.getByRole('button', { name: 'Join the crew' }))

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/projects/p1'))
    expect(requests.some((r) => r.path === '/api/invites/tok/accept')).toBe(true)
  })

  it('warns an account with another email, and says so plainly when the link is dead', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/invites/tok': () => json(preview({ email: 'someone@example.com' })),
      'GET /api/invites/gone': () => problem(404, 'Not Found', 'This invite link is not valid'),
    })
    const { router } = renderApp('/invite/tok')
    await logIn()

    expect(await screen.findByText(/You are logged in as ada@example.com/)).toBeInTheDocument()

    await router.navigate('/invite/gone')
    expect(await screen.findByRole('heading', { name: 'This link does not work' })).toBeInTheDocument()
  })
})
