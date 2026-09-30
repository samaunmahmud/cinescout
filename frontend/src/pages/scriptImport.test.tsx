import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ScriptImport } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const SCRIPT = 'INT. DINER - NIGHT\nRain.\n\nEXT. ROOFTOP - DAWN\nLight.'

const found: ScriptImport = {
  scriptNumbersKept: false,
  scenes: [
    { id: null, sceneNumber: 5, title: 'INT. DINER - NIGHT', characters: 24, truncated: false },
    { id: null, sceneNumber: 6, title: 'EXT. ROOFTOP - DAWN', characters: 20000, truncated: true },
  ],
}

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
}

describe('importing a script', () => {
  it('is offered on the project, previews the scenes and then adds them', async () => {
    let imported = false
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/scenes?page=0&size=24': () =>
        json(pageOf(imported ? [scene({ id: 's5', sceneNumber: 5 }), scene({ id: 's6', sceneNumber: 6, title: 'EXT. ROOFTOP - DAWN' })] : [])),
      'POST /api/projects/p1/scenes/import/preview': () => json(found),
      'POST /api/projects/p1/scenes/import': () => {
        imported = true
        return json({ ...found, scenes: found.scenes.map((s, i) => ({ ...s, id: `s${i + 5}` })) }, 201)
      },
    })
    const { router } = renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('link', { name: 'Import script' }))
    expect(router.state.location.pathname).toBe('/projects/p1/scenes/import')
    expect(screen.getByRole('button', { name: 'Find scenes' })).toBeDisabled()
    await user.click(await screen.findByLabelText('Script'))
    await user.paste(SCRIPT)
    await user.click(screen.getByRole('button', { name: 'Find scenes' }))

    expect(await screen.findByRole('heading', { name: '2 scenes found' })).toBeInTheDocument()
    const [diner, rooftop] = within(screen.getByRole('list', { name: 'Scenes found' })).getAllByRole('listitem')
    expect(diner).toHaveTextContent('Scene 5:INT. DINER - NIGHT24 characters')
    expect(rooftop).toHaveTextContent('EXT. ROOFTOP - DAWN')
    expect(rooftop).toHaveTextContent('20,000 characters, cut short')
    expect(screen.getByText(/numbered in script order, on from the project’s last scene\. 1 scene is too long/)).toBeInTheDocument()
    expect(requests.some((r) => r.path === '/api/projects/p1/scenes/import')).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Import 2 scenes' }))

    expect(await screen.findByRole('link', { name: /Scene 6: EXT. ROOFTOP - DAWN/ })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projects/p1')
    expect(requests.filter((r) => r.method === 'POST').map((r) => [r.path, r.body])).toEqual([
      ['/api/projects/p1/scenes/import/preview', { script: SCRIPT }],
      ['/api/projects/p1/scenes/import', { script: SCRIPT }],
    ])
  })

  it('drops the preview when the script is edited, as it no longer describes it', async () => {
    fakeServer({ ...base, 'POST /api/projects/p1/scenes/import/preview': () => json({ ...found, scriptNumbersKept: true }) })
    renderApp('/projects/p1/scenes/import')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Script'), 'INT. DINER')
    await user.click(screen.getByRole('button', { name: 'Find scenes' }))
    expect(await screen.findByText(/The scenes keep the numbers the script gives them\./)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Script'), ' - NIGHT')

    expect(screen.queryByRole('heading', { name: '2 scenes found' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Import/ })).not.toBeInTheDocument()
  })

  it('says so when the script has no scene headings', async () => {
    fakeServer({ ...base, 'POST /api/projects/p1/scenes/import/preview': () => json({ scenes: [], scriptNumbersKept: false }) })
    renderApp('/projects/p1/scenes/import')
    const user = await logIn()

    await user.type(await screen.findByLabelText('Script'), 'Some notes.')
    await user.click(screen.getByRole('button', { name: 'Find scenes' }))

    expect(await screen.findByText(/No scene headings found/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Import/ })).not.toBeInTheDocument()
  })

  it('reads a chosen text file into the script', async () => {
    fakeServer(base)
    renderApp('/projects/p1/scenes/import')
    const user = await logIn()

    await user.upload(await screen.findByLabelText('Or choose a text file'), new File([SCRIPT], 'night-shift.fountain', { type: 'text/plain' }))

    expect(await screen.findByDisplayValue(/INT\. DINER - NIGHT/)).toBe(screen.getByLabelText('Script'))
    expect(screen.getByRole('button', { name: 'Find scenes' })).toBeEnabled()
  })

  it('shows what the server says is wrong with the script next to it', async () => {
    fakeServer({
      ...base,
      'POST /api/projects/p1/scenes/import/preview': () => json({ ...found, scenes: [found.scenes[0]] }),
      'POST /api/projects/p1/scenes/import': () =>
        problem(400, 'Validation failed', 'The request is invalid', { errors: [{ field: 'script', message: 'No scene headings found.' }] }),
    })
    renderApp('/projects/p1/scenes/import')
    const user = await logIn(userEvent.setup())

    await user.type(await screen.findByLabelText('Script'), 'INT. DINER')
    await user.click(screen.getByRole('button', { name: 'Find scenes' }))
    await user.click(await screen.findByRole('button', { name: 'Import 1 scene' }))

    expect(await screen.findByText('No scene headings found.')).toBeInTheDocument()
    expect(screen.getByLabelText('Script')).toBeInvalid()
  })

  it("is not found for another user's project", async () => {
    fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/projects/p1': () => problem(404, 'Not found', 'Project not found') })
    renderApp('/projects/p1/scenes/import')
    await logIn()

    expect(await screen.findByRole('heading', { name: 'Not found' })).toBeInTheDocument()
  })
})
