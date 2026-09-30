import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, pageOf } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const base = { 'GET /api/auth/me': () => json(ada) }

describe('the account page', () => {
  it('is reached from the header and renames the account everywhere at once', async () => {
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])),
      'PUT /api/account': (req) => json({ ...ada, displayName: (req.body as { displayName: string }).displayName }),
    })
    const { router } = renderApp('/projects')
    const user = await logIn()

    await user.click(await screen.findByRole('link', { name: 'Account: Ada' }))
    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/account'))
    expect(await screen.findByText('ada@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save name' })).toBeDisabled()

    const name = screen.getByLabelText('Display name')
    await user.clear(name)
    await user.type(name, ' Ada Lovelace ')
    await user.click(screen.getByRole('button', { name: 'Save name' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Name saved.')
    expect(screen.getByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Account: Ada Lovelace' })).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ displayName: 'Ada Lovelace' })
  })

  it('changes the password once the new one is long enough and typed the same twice', async () => {
    const { requests } = fakeServer({ ...base, 'PUT /api/account/password': () => new Response(null, { status: 204 }) })
    renderApp('/account')
    const user = await logIn()
    const form = within(await screen.findByRole('region', { name: 'Password' }))
    const submit = form.getByRole('button', { name: 'Change password' })

    await user.type(form.getByLabelText('Current password'), 'old-password')
    await user.type(form.getByLabelText('New password'), 'short')
    expect(form.getAllByText('At least 8 characters.')).toHaveLength(1)
    expect(form.getByLabelText('New password')).toBeInvalid()
    await user.type(form.getByLabelText('New password'), '-but-longer')
    await user.type(form.getByLabelText('New password again'), 'short-but-longe')
    expect(form.getByText('The two passwords are not the same.')).toBeInTheDocument()
    expect(submit).toBeDisabled()
    await user.type(form.getByLabelText('New password again'), 'r')
    await user.click(submit)

    expect(await form.findByRole('status')).toHaveTextContent('Password changed. Your other devices were logged out.')
    expect(form.getByLabelText('Current password')).toHaveValue('')
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ currentPassword: 'old-password', newPassword: 'short-but-longer' })
  })

  it('shows a wrong current password on its field', async () => {
    fakeServer({
      ...base,
      'PUT /api/account/password': () =>
        problem(400, 'Validation failed', 'The request is invalid', { errors: [{ field: 'currentPassword', message: "That is not the account's password" }] }),
    })
    renderApp('/account')
    const user = await logIn()
    const form = within(await screen.findByRole('region', { name: 'Password' }))

    await user.type(form.getByLabelText('Current password'), 'wrong-one')
    await user.type(form.getByLabelText('New password'), 'long-enough-1')
    await user.type(form.getByLabelText('New password again'), 'long-enough-1')
    await user.click(form.getByRole('button', { name: 'Change password' }))

    expect(await form.findByText("That is not the account's password")).toBeInTheDocument()
    expect(form.getByLabelText('Current password')).toBeInvalid()
    expect(form.getByLabelText('New password')).toHaveValue('long-enough-1')
  })

  it('deletes the account after the password is given again, and ends at the login page', async () => {
    let attempts = 0
    const { requests } = fakeServer({
      ...base,
      'POST /api/account/delete': () =>
        attempts++ === 0
          ? problem(400, 'Validation failed', 'The request is invalid', { errors: [{ field: 'password', message: "That is not the account's password" }] })
          : new Response(null, { status: 204 }),
    })
    const { router } = renderApp('/account')
    const user = await logIn()
    const panel = within(await screen.findByRole('region', { name: 'Delete account' }))

    expect(panel.queryByLabelText('Your password, to confirm')).not.toBeInTheDocument()
    await user.click(panel.getByRole('button', { name: 'Delete account…' }))
    expect(panel.getByRole('button', { name: 'Delete my account for good' })).toBeDisabled()
    await user.type(panel.getByLabelText('Your password, to confirm'), 'wrong')
    await user.click(panel.getByRole('button', { name: 'Delete my account for good' }))
    expect(await panel.findByText("That is not the account's password")).toBeInTheDocument()

    await user.clear(panel.getByLabelText('Your password, to confirm'))
    await user.type(panel.getByLabelText('Your password, to confirm'), 'right-password')
    await user.click(panel.getByRole('button', { name: 'Delete my account for good' }))

    expect(await screen.findByRole('heading', { name: /log in|welcome|sign in/i })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(requests.filter((r) => r.path === '/api/account/delete').map((r) => r.body)).toEqual([{ password: 'wrong' }, { password: 'right-password' }])
  })
})
