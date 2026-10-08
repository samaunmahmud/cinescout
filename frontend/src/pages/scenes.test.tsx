import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Scene, SceneRequirements } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, project, scene, pageOf } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const requirements: SceneRequirements = {
  settingType: 'Late-night diner',
  visualMood: 'Lonely, neon-lit',
  lightingNeeds: 'Practical neon and overheads',
  timeOfDay: 'Night',
  acousticSensitivity: 'HIGH',
  estimatedCastAndCrewSize: 18,
}

describe('the scenes of a project', () => {
  it('are listed in the order the server gives, with what the AI found', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () =>
        json(
          pageOf([
            scene({ parseStatus: 'PARSED', requirements, shootDateStart: '2026-10-12', shootDateEnd: '2026-10-12' }),
            scene({ id: 's2', sceneNumber: null, title: 'Montage', parseStatus: 'FAILED' }),
          ]),
        ),
    })
    renderApp('/projects/p1')
    await logIn()

    const list = await screen.findByRole('list')
    const [first, second] = within(list).getAllByRole('link')
    expect(first).toHaveAttribute('href', '/scenes/s1')
    expect(first).toHaveTextContent('Scene 12: INT. DINER - NIGHT')
    expect(first).toHaveTextContent('Late-night diner')
    expect(first).toHaveTextContent('Requirements ready')
    expect(second).toHaveTextContent('Montage')
    expect(second).toHaveTextContent('Not scheduled')
    expect(second).toHaveTextContent('Analysis failed')
  })

  it('can be added: blank optional fields go as null and the new scene opens', async () => {
    const created = scene({ id: 's9', sceneNumber: 3, title: 'EXT. ROOFTOP - DAWN' })
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
      'POST /api/projects/p1/scenes': () => json(created, 201),
      'GET /api/scenes/s9/locations?page=0&size=24': () => json(pageOf([])),
      'GET /api/scenes/s9/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
      'GET /api/scenes/s9/shots': () => json([]),
    })
    const { router } = renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('link', { name: 'Add scene' }))
    await user.type(await screen.findByLabelText('Scene number'), '3')
    await user.type(screen.getByLabelText('Title'), ' EXT. ROOFTOP - DAWN ')
    await user.type(screen.getByLabelText('Script'), 'The city wakes up below.')
    await user.click(screen.getByRole('button', { name: 'Add scene' }))

    expect(await screen.findByRole('heading', { name: 'Scene 3: EXT. ROOFTOP - DAWN' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/scenes/s9')
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      sceneNumber: 3,
      title: 'EXT. ROOFTOP - DAWN',
      sourceText: 'The city wakes up below.',
      shootDateStart: null,
      shootDateEnd: null,
    })
  })

  it('catch a backwards shoot window and a bad scene number before sending anything', async () => {
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
    })
    renderApp('/projects/p1/scenes/new')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Title'), 'Scene')
    await user.type(screen.getByLabelText('Script'), 'Words.')
    await user.type(screen.getByLabelText('First shoot day'), '2026-10-14')
    await user.type(screen.getByLabelText('Last shoot day'), '2026-10-12')
    await user.type(screen.getByLabelText('Scene number'), '0')

    expect(screen.getByText('The last shoot day cannot be before the first.')).toBeInTheDocument()
    expect(screen.getByText('A whole number above zero.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add scene' })).toBeDisabled()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)
  })

  it('show a duplicate scene number as the server explains it', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'POST /api/projects/p1/scenes': () => problem(409, 'Conflict', 'A scene with that number already exists in this project'),
    })
    renderApp('/projects/p1/scenes/new')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Scene number'), '12')
    await user.type(screen.getByLabelText('Title'), 'Again')
    await user.type(screen.getByLabelText('Script'), 'Words.')
    await user.click(screen.getByRole('button', { name: 'Add scene' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('A scene with that number already exists in this project')
  })
})

describe('searching the scenes of a project', () => {
  it('asks the server for the scenes that mention the words, keeps them in the address and can show all again', async () => {
    const all = [scene(), scene({ id: 's2', sceneNumber: 13, title: 'EXT. PIER - DAWN' })]
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf(all)),
      'GET /api/projects/p1/scenes?page=2&size=24': () => json(pageOf(all, { page: 0 })),
      'GET /api/projects/p1/scenes?q=red%20booths%20%26%20rain&page=0&size=24': () => json(pageOf([all[0]])),
    })
    const { router } = renderApp('/projects/p1?page=3')
    const user = await logIn()

    await user.type(await screen.findByRole('searchbox', { name: 'Search scenes' }), ' red booths & rain {Enter}')

    await vi.waitFor(() => expect(router.state.location.search).toBe('?q=red+booths+%26+rain'))
    await vi.waitFor(() => expect(screen.queryByRole('link', { name: /EXT. PIER - DAWN/ })).not.toBeInTheDocument())
    expect(screen.getByRole('link', { name: /INT. DINER - NIGHT/ })).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Search scenes' })).toHaveValue('red booths & rain')

    await user.click(screen.getByRole('button', { name: 'Show all' }))

    expect(await screen.findByRole('link', { name: /EXT. PIER - DAWN/ })).toBeInTheDocument()
    expect(router.state.location.search).toBe('')
    expect(screen.getByRole('searchbox', { name: 'Search scenes' })).toHaveValue('')
  })

  it('says when nothing matches, keeping the box to search again', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?q=spaceship&page=0&size=24': () => json(pageOf([])),
    })
    renderApp('/projects/p1?q=spaceship')
    await logIn()

    expect(await screen.findByText('No scene mentions “spaceship”.')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Search scenes' })).toHaveValue('spaceship')
  })

  it('is not offered for a project with at most one scene', async () => {
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([scene()])),
    })
    renderApp('/projects/p1')
    await logIn()

    expect(await screen.findByRole('link', { name: /INT. DINER - NIGHT/ })).toBeInTheDocument()
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
  })
})

describe('analysing every scene of a project', () => {
  it('is offered while a scene is waiting, reports the run and shows what the AI found', async () => {
    let analysed = false
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () =>
        json(pageOf(analysed ? [scene({ parseStatus: 'PARSED', requirements }), scene({ id: 's2', sceneNumber: 13, parseStatus: 'FAILED' })] : [scene(), scene({ id: 's2', sceneNumber: 13 })])),
      'POST /api/projects/p1/scenes/parse': () => {
        analysed = true
        return json({ parsed: 1, failed: 1, remaining: 4 })
      },
    })
    renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Analyse scenes' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Analysed 1 scene. 1 scene could not be analysed; open it to try again. 4 scenes are still waiting: analyse again to go on.',
    )
    expect(await screen.findByText('Late-night diner')).toBeInTheDocument()
    // More are waiting on other pages, so the button stays although none on this page is.
    expect(screen.getByRole('button', { name: 'Analyse scenes' })).toBeInTheDocument()
    expect(requests.filter((r) => r.method === 'POST')).toHaveLength(1)
  })

  it('is not offered once every scene in view has been analysed, and explains a failure', async () => {
    let scenes = [scene({ parseStatus: 'PARSED', requirements })]
    fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf(scenes)),
      'POST /api/projects/p1/scenes/parse': () => problem(503, 'Not available', 'Scouting is not configured on this server'),
    })
    const { unmount } = renderApp('/projects/p1')
    const user = await logIn()
    expect(await screen.findByText('Late-night diner')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Analyse scenes' })).not.toBeInTheDocument()
    unmount()

    scenes = [scene()]
    renderApp('/projects/p1')
    await user.click(await screen.findByRole('button', { name: 'Analyse scenes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Scouting is not configured on this server')
  })
})

describe('a scene', () => {
  function serverFor(current: Scene, extra: Parameters<typeof fakeServer>[0] = {}) {
    return fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project()),
      'GET /api/scenes/s1': () => json(current),
      'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
      'GET /api/scenes/s1/shots': () => json([]),
      'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf([])),
      ...extra,
    })
  }

  it('shows its script, shoot window and speaking parts', async () => {
    serverFor(scene({ shootDateStart: '2026-10-12', shootDateEnd: '2026-10-14', characters: ['MARA', 'JONES'] }))
    renderApp('/scenes/s1')
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Scene 12: INT. DINER - NIGHT' })).toBeInTheDocument()
    expect(screen.getByText(/Rain on the windows/)).toBeInTheDocument()
    expect(screen.getByText(/12.*2026 – .*14.*2026/)).toBeInTheDocument()
    expect(screen.getByText('MARA, JONES')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Night Shift' })).toHaveAttribute('href', '/projects/p1')
  })

  it('is analysed on request and shows the extracted requirements', async () => {
    const { requests } = serverFor(scene(), {
      'POST /api/scenes/s1/parse': () => json(scene({ parseStatus: 'PARSED', requirements, parsedAt: '2026-09-23T10:00:00Z' })),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    expect(await screen.findByText(/Not analysed yet/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Analyse script' }))

    const panel = screen.getByRole('region', { name: 'Location requirements' })
    expect(await within(panel).findByText('Late-night diner')).toBeInTheDocument()
    expect(within(panel).getByText('High: needs a quiet location')).toBeInTheDocument()
    expect(within(panel).getByText('About 18 people')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Analyse again' })).toBeInTheDocument()
    expect(requests.filter((r) => r.path === '/api/scenes/s1/parse')).toHaveLength(1)
  })

  it('explains when scouting is not configured on the server', async () => {
    serverFor(scene(), {
      'POST /api/scenes/s1/parse': () => problem(503, 'Not available', 'Scouting is not configured on this server'),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Analyse script' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Scouting is not configured on this server')
  })

  it('shows a failed analysis, which the server records on the scene', async () => {
    let current = scene()
    serverFor(scene(), {
      'GET /api/scenes/s1': () => json(current),
      'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
      'GET /api/scenes/s1/shots': () => json([]),
      'POST /api/scenes/s1/parse': () => {
        current = scene({ parseStatus: 'FAILED' })
        return problem(502, 'Bad gateway', 'The AI service returned an unusable answer; try again', { retryable: true })
      },
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Analyse script' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The AI service returned an unusable answer; try again')
    expect(await screen.findByText('Analysis failed')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('warns that editing the script discards its requirements, and saves a full replacement', async () => {
    const parsed = scene({ parseStatus: 'PARSED', requirements, shootDateStart: '2026-10-12' })
    const { requests } = serverFor(parsed, {
      'PUT /api/scenes/s1': (req) => json({ ...parsed, ...(req.body as object), parseStatus: 'PENDING', requirements: null }),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Script'), ' Thunder.')
    expect(screen.getByRole('note')).toHaveTextContent('Changing the script discards the requirements')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Not analysed')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({
      sceneNumber: 12,
      title: 'INT. DINER - NIGHT',
      sourceText: `${parsed.sourceText} Thunder.`,
      shootDateStart: '2026-10-12',
      shootDateEnd: null,
    })
  })

  it('is deleted after confirmation, returning to its project', async () => {
    const { requests } = serverFor(scene(), {
      'DELETE /api/scenes/s1': () => new Response(null, { status: 204 }),
      'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
    })
    const { router } = renderApp('/scenes/s1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Delete' }))
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Delete scene' }))

    expect(await screen.findByText(/No scenes yet/)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projects/p1')
    expect(requests.filter((r) => r.method === 'DELETE')).toHaveLength(1)
  })

  it("is not found when it is another user's", async () => {
    fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/scenes/s1': () => problem(404, 'Not found', 'Scene not found') })
    renderApp('/scenes/s1')
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Not found' })).toBeInTheDocument()
  })
})
