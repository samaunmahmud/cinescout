import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, logIn, pageOf, project, schedule } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
  'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
  'GET /api/projects/p1/schedule': () => json(schedule),
}

describe('the call sheet', () => {
  it('is reached from the schedule and lists each day’s scenes with where and whom to call', async () => {
    fakeServer(base)
    const { router } = renderApp('/projects/p1?tab=schedule')
    const user = await logIn()

    await user.click(await screen.findByRole('link', { name: 'Call sheet' }))

    expect(router.state.location.pathname).toBe('/projects/p1/call-sheet')
    const sheet = within(await screen.findByRole('article', { name: 'Call sheet' }))
    expect(sheet.getByRole('heading', { level: 2, name: 'Night Shift' })).toBeInTheDocument()
    expect(sheet.getByText(/Brooklyn, New York · Prepared by Ada/)).toBeInTheDocument()
    const first = within(sheet.getByRole('region', { name: /Monday.*Day 1 of 2/ }))
    const [diner, rooftop] = first.getAllByRole('row').slice(1)
    expect(diner).toHaveTextContent('12INT. DINER - NIGHTNightTom’s Diner782 Washington Ave, Brooklyn, NYTom Miller+1 718 555 0100')
    expect(rooftop).toHaveTextContent(/13EXT\. ROOFTOP - DAWNuntil .*14.*2026—TBC/)
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
})
