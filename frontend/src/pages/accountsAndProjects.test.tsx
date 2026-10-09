import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Project } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { PASSWORD, ada, logIn, project, pageOf, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

describe('logging in', () => {
  it('sends a logged-out visitor to the login page and back to where they were going', async () => {
    const { requests, authRequests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
      'GET /api/projects/p1': () => json(project()),
    })
    const { router } = renderApp('/projects/p1')

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Night Shift' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projects/p1')
    expect(authRequests.find((r) => r.path === '/api/auth/login')?.body).toEqual({ email: ada.email, password: PASSWORD })
    // The session is an HttpOnly cookie the browser sends by itself: the password is sent once, to log in.
    const all = [...requests, ...authRequests]
    expect(all.some((r) => 'Authorization' in r.headers)).toBe(false)
    expect(all.filter((r) => JSON.stringify(r.body ?? '').includes(PASSWORD))).toHaveLength(1)
  })

  it('keeps the user logged in across a reload while the session lasts', async () => {
    fakeServer(
      { 'GET /api/auth/me': () => json(ada), 'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])), 'GET /api/projects/p1': () => json(project()) },
      { loggedIn: true },
    )
    const { router } = renderApp('/projects/p1')
    // While the server is asked, the page waits where it is instead of bouncing through the login page.
    expect(router.state.location.pathname).toBe('/projects/p1')

    expect(await screen.findByRole('heading', { name: 'Night Shift' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Log in' })).toBeNull()
  })

  it('asks the server whether there is a session before showing the login form', async () => {
    fakeServer({ 'GET /api/auth/me': () => json(ada) })
    renderApp('/login')

    expect(screen.getByRole('status')).toHaveTextContent('Checking your login')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByText('Checking your login')).toBeNull()
  })

  it('says so when the email or password is wrong', async () => {
    fakeServer({ 'POST /api/auth/login': () => problem(401, 'Unauthorized', 'The email or password is not right') })
    renderApp('/login')

    await logIn()

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong email or password.')
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('logs out, ending the session on the server', async () => {
    const { authRequests } = fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])) })
    const { router } = renderApp('/login')
    const user = await logIn()
    await screen.findByText(/No projects yet/)

    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(authRequests.filter((r) => r.path === '/api/auth/logout')).toHaveLength(1)
    await act(() => router.navigate('/projects'))
    expect(router.state.location.pathname).toBe('/login')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('goes back to the login page when the session ends on the server', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => problem(401, 'Unauthorized', 'Valid credentials are required'),
    })
    renderApp('/login')

    await logIn()

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })
})

describe('registering', () => {
  async function fillIn(user: ReturnType<typeof userEvent.setup>) {
    await user.type(await screen.findByLabelText('Name'), 'Ada')
    await user.type(screen.getByLabelText('Email'), ` ${ada.email} `)
    await user.type(screen.getByLabelText('Password'), PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
  }

  it('creates the account and logs straight in', async () => {
    const { authRequests } = fakeServer({
      'POST /api/auth/register': () => json(ada, 201),
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])),
    })
    renderApp('/register')

    await fillIn(userEvent.setup())

    expect(await screen.findByRole('heading', { name: 'Your productions' })).toBeInTheDocument()
    expect(authRequests.find((r) => r.path === '/api/auth/register')?.body).toEqual({ displayName: 'Ada', email: ada.email, password: PASSWORD })
    expect(authRequests.find((r) => r.path === '/api/auth/login')?.body).toEqual({ email: ada.email, password: PASSWORD })
  })

  it('shows the server-side validation message next to the field', async () => {
    fakeServer({
      'POST /api/auth/register': () =>
        problem(400, 'Validation failed', 'The request is invalid', { errors: [{ field: 'email', message: 'must be a well-formed email address' }] }),
    })
    renderApp('/register')

    await fillIn(userEvent.setup())

    expect(await screen.findByText('must be a well-formed email address')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows a taken email as the server explains it', async () => {
    fakeServer({ 'POST /api/auth/register': () => problem(409, 'Conflict', 'An account with this email already exists') })
    renderApp('/register')

    await fillIn(userEvent.setup())

    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists')
  })
})

describe('projects', () => {
  it('lists active projects and switches to archived ones', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([project(), project({ id: 'p2', title: 'Day Break', locationArea: null, description: null })])),
      'GET /api/projects?status=ARCHIVED&page=0&size=24': () => json(pageOf([project({ id: 'p3', title: 'Old Film', status: 'ARCHIVED' })])),
    })
    renderApp('/login')
    const user = await logIn()

    const list = await screen.findByRole('list')
    expect(within(list).getByRole('link', { name: /Night Shift/ })).toHaveAttribute('href', '/projects/p1')
    expect(within(list).getByText('No area yet')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Archived' }))
    expect(await screen.findByRole('link', { name: /Old Film/ })).toBeInTheDocument()
    expect(screen.queryByText('Night Shift')).not.toBeInTheDocument()
  })

  it('lets a keyboard user skip past the header to the page', async () => {
    fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])) }, { loggedIn: true })
    renderApp('/projects')
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: 'Your productions' })
    await user.tab()

    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveFocus()
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main')
  })

  it('names the browser tab after the page', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([project()])),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    })
    renderApp('/projects')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(document.title).toBe('Log in · CineScout')
    const user = await logIn()

    await screen.findByRole('link', { name: /Night Shift/ })
    expect(document.title).toBe('Projects · CineScout')
    await user.click(screen.getByRole('link', { name: /Night Shift/ }))
    await waitFor(() => expect(document.title).toBe('Night Shift · CineScout'))
  })

  it('introduces itself to a new user in three acts, with the way in', async () => {
    fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])) })
    renderApp('/projects')
    const user = await logIn()

    const intro = within(await screen.findByRole('region', { name: 'Your first production' }))
    expect(intro.getAllByRole('listitem').map((act) => within(act).getByRole('heading').textContent)).toEqual([
      'Bring the script',
      'Find the places',
      'Lock them in',
    ])
    await user.click(intro.getByRole('button', { name: 'Create your first project' }))

    expect(screen.getByRole('heading', { name: 'New project' })).toBeInTheDocument()
    expect(intro.queryByRole('button', { name: 'Create your first project' })).not.toBeInTheDocument()
  })

  it('sets up a sample production with its script cut into scenes, for a new user to try', async () => {
    const created = project({ id: 'p9', title: 'The Night Ferry' })
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])),
      'POST /api/projects': () => json(created, 201),
      'POST /api/projects/p9/scenes/import': () => json({ scenes: [], scriptNumbersKept: true }, 201),
      'GET /api/projects/p9': () => json(created),
      'GET /api/projects/p9/scenes?page=0&size=24': () => json(pageOf([scene({ projectId: 'p9', title: 'INT. ALL-NIGHT DINER - NIGHT', sceneNumber: 1 })])),
    })
    const { router } = renderApp('/projects')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Try a sample script' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'The Night Ferry' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projects/p9')
    const imported = requests.find((r) => r.path === '/api/projects/p9/scenes/import')!.body as { script: string }
    expect(imported.script).toContain('1 INT. ALL-NIGHT DINER - NIGHT 1')
    expect(requests.find((r) => r.path === '/api/projects' && r.method === 'POST')?.body).toMatchObject({ title: 'The Night Ferry', locationArea: 'Brooklyn, New York' })
  })

  it('shows on each poster how many scenes have their location locked', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () =>
        json(pageOf([project(), project({ id: 'p2', title: 'Paper Moons', sceneCount: 0, confirmedSceneCount: 0 }), project({ id: 'p3', title: 'Solo', sceneCount: 1, confirmedSceneCount: 1 })])),
    })
    renderApp('/projects')
    await logIn()

    const night = await screen.findByRole('link', { name: /Night Shift/ })
    expect(night).toHaveTextContent('12 scenes · 3 locked')
    expect(within(night).getByRole('progressbar', { name: 'Scenes with a confirmed location' })).toHaveAttribute('aria-valuenow', '3')
    const empty = screen.getByRole('link', { name: /Paper Moons/ })
    expect(empty).toHaveTextContent('No scenes yet')
    expect(within(empty).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Solo/ })).toHaveTextContent('1 scene · 1 locked')
  })

  it('puts a picture of one of its venues on a poster when there is one', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () =>
        json(pageOf([project({ posterImageUrl: 'https://cdn.example/roof.jpg' }), project({ id: 'p2', title: 'Paper Moons' })])),
    })
    renderApp('/projects')
    await logIn()

    const pictured = await screen.findByRole('link', { name: /Night Shift/ })
    expect(pictured.querySelector('img')).toHaveAttribute('src', 'https://cdn.example/roof.jpg')
    expect(screen.getByRole('link', { name: /Paper Moons/ }).querySelector('img')).toBeNull()
  })

  it('creates a project, sending blank optional fields as null, and opens it', async () => {
    const created = project({ id: 'p9', title: 'New Film', description: null, locationArea: null })
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])),
      'POST /api/projects': () => json(created, 201),
      'GET /api/projects/p9/scenes?page=0&size=24': () => json(pageOf([])),
    })
    const { router } = renderApp('/login')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'New project' }))
    await user.type(screen.getByLabelText('Title'), '  New Film  ')
    await user.type(screen.getByLabelText('Location area'), '   ')
    await user.click(screen.getByRole('button', { name: 'Create project' }))

    expect(await screen.findByRole('heading', { name: 'New Film' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projects/p9')
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({ title: 'New Film', description: null, locationArea: null })
  })

  it('edits a project with a full replacement that keeps its status', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
      'GET /api/projects/p1': () => json(project({ status: 'ARCHIVED' })),
      'PUT /api/projects/p1': (req) => json(project(req.body as Partial<Project>)),
    })
    renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const title = screen.getByLabelText('Title')
    await user.clear(title)
    await user.type(title, 'Night Shift II')
    await user.clear(screen.getByLabelText('Description'))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('heading', { name: 'Night Shift II' })).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({
      title: 'Night Shift II',
      description: null,
      locationArea: 'Brooklyn, New York',
      status: 'ARCHIVED',
    })
  })

  it('archives and restores a project', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
      'GET /api/projects/p1': () => json(project()),
      'PUT /api/projects/p1': (req) => json(project(req.body as Partial<Project>)),
    })
    renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Archive' }))
    expect(await screen.findByText('Archived')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Restore' }))
    await waitFor(() => expect(screen.queryByText('Archived')).not.toBeInTheDocument())
  })

  it('deletes a project only after confirmation', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
      'GET /api/projects/p1': () => json(project()),
      'DELETE /api/projects/p1': () => new Response(null, { status: 204 }),
      'GET /api/projects?status=ACTIVE&page=0&size=24': () => json(pageOf([])),
    })
    const { router } = renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(requests.some((r) => r.method === 'DELETE')).toBe(false)
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete project' }))

    expect(await screen.findByText(/No projects yet/)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projects')
  })

  it("shows another user's project as not found", async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => problem(404, 'Not Found', 'Project not found'),
    })
    renderApp('/projects/p1')
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Not found' })).toBeInTheDocument()
  })
})
