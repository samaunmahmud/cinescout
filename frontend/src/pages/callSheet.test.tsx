import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, pageOf, project, schedule } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
  'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
  'GET /api/projects/p1/moves': () => json({ days: [], warnAfterMinutes: 60, attribution: '' }),
  'GET /api/projects/p1/schedule': () => json(schedule),
  'GET /api/projects/p1/call-sheet-link': () => problem(404, 'Not found', 'Not shared'),
}

describe('the call sheet', () => {
  it('is reached from the schedule and lists each day’s scenes with where and whom to call', async () => {
    fakeServer(base)
    const { router } = renderApp('/projects/p1?tab=schedule')
    const user = await logIn()

    await user.click(await screen.findByRole('link', { name: 'Call sheet' }))

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/projects/p1/call-sheet'))
    const sheet = within(await screen.findByRole('article', { name: 'Call sheet' }))
    expect(sheet.getByRole('heading', { level: 2, name: 'Night Shift' })).toBeInTheDocument()
    expect(sheet.getByText(/Brooklyn, New York · Prepared by Ada/)).toBeInTheDocument()
    const first = within(sheet.getByRole('region', { name: /Monday.*Day 1 of 2/ }))
    const [diner, rooftop] = first.getAllByRole('row').slice(1)
    expect(diner).toHaveTextContent('12INT. DINER - NIGHTCast: MARA, DET. JONESNightTom’s Diner782 Washington Ave, Brooklyn, NYSun 07:04–18:20 · Clear sky, 12–23 °C! Strong wind: secure lights and flagsTom Miller+1 718 555 0100')
    expect(rooftop).toHaveTextContent(/13EXT\. ROOFTOP - DAWNCast: MARA, DET\. JONESuntil .*14.*2026—TBC/)
    expect(within(sheet.getByRole('region', { name: 'To be scheduled' })).getByText('INT. CAR - DAY')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Night Shift' })).toHaveAttribute('href', '/projects/p1?tab=schedule')
  })

  it('prints on request', async () => {
    fakeServer(base)
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    renderApp('/projects/p1/call-sheet')
    const user = await logIn()

    await screen.findByRole('article', { name: 'Call sheet' })
    await user.click(screen.getByRole('button', { name: 'Print' }))

    expect(print).toHaveBeenCalledTimes(1)
    print.mockRestore()
  })

  it('says so when nothing is dated, and is not found for another user’s project', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json({ days: [], unscheduled: [] }) })
    const { unmount } = renderApp('/projects/p1/call-sheet')
    await logIn()
    expect(await screen.findByText(/No scene has a shoot date yet/)).toBeInTheDocument()
    unmount()

    fakeServer({ 'GET /api/auth/me': () => json(ada), 'GET /api/projects/p1': () => problem(404, 'Not found', 'Project not found') }, { loggedIn: true })
    renderApp('/projects/p1/call-sheet')
    expect(await screen.findByRole('heading', { name: 'Not found' })).toBeInTheDocument()
  })

  it('is shared with the crew by a link, which can be copied and stopped', async () => {
    let token: string | null = null
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/call-sheet-link': () => (token ? json({ token }) : problem(404, 'Not found', 'Not shared')),
      'POST /api/projects/p1/call-sheet-link': () => {
        token = 'A'.repeat(43)
        return json({ token })
      },
      'DELETE /api/projects/p1/call-sheet-link': () => {
        token = null
        return new Response(null, { status: 204 })
      },
    })
    renderApp('/projects/p1/call-sheet')
    const user = await logIn()
    const writeText = vi.spyOn(navigator.clipboard, 'writeText')

    await user.click(await screen.findByRole('button', { name: 'Make a link' }))
    const field = await screen.findByLabelText('Call sheet link')
    expect(field).toHaveValue(`${window.location.origin}/call-sheet/${'A'.repeat(43)}`)
    await user.click(screen.getByRole('button', { name: 'Copy link' }))
    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/call-sheet/${'A'.repeat(43)}`)
    expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Stop sharing' }))
    expect(await screen.findByRole('button', { name: 'Make a link' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Call sheet link')).not.toBeInTheDocument()
    expect(requests.filter((r) => r.path === '/api/projects/p1/call-sheet-link').map((r) => r.method)).toEqual(['GET', 'POST', 'DELETE'])
  })
})

describe('a shared call sheet', () => {
  it('opens without a login and shows only the sheet', async () => {
    fakeServer({
      'GET /api/public/call-sheets/tok': () =>
        json({ projectTitle: 'Night Shift', locationArea: 'Brooklyn, New York', preparedBy: 'Ada', schedule }),
    })
    renderApp('/call-sheet/tok')

    const sheet = within(await screen.findByRole('article', { name: 'Call sheet' }))
    expect(sheet.getByRole('heading', { level: 2, name: 'Night Shift' })).toBeInTheDocument()
    expect(sheet.getByText(/Prepared by Ada/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument()
    expect(document.title).toBe('Call sheet: Night Shift · CineScout')
  })

  it('opens for someone who is logged in too', async () => {
    fakeServer(
      {
        'GET /api/auth/me': () => json(ada),
        'GET /api/public/call-sheets/tok': () => json({ projectTitle: 'Night Shift', locationArea: null, preparedBy: 'Ada', schedule }),
      },
      { loggedIn: true },
    )
    renderApp('/call-sheet/tok')

    expect(await screen.findByRole('article', { name: 'Call sheet' })).toBeInTheDocument()
  })

  it('says so when the link is no longer shared', async () => {
    fakeServer({ 'GET /api/public/call-sheets/old': () => problem(404, 'Not found', 'This call sheet is not shared, or no longer is') })
    renderApp('/call-sheet/old')

    expect(await screen.findByRole('heading', { name: 'Not shared' })).toBeInTheDocument()
  })
})
