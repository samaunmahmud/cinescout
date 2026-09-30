import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Schedule } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project, schedule, scheduled } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
  'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
}

describe('the schedule of a project', () => {
  it('is a tab that lays the scenes out by shoot day with their confirmed venues and what is missing', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json(schedule) })
    const { router } = renderApp('/projects/p1')
    const user = await logIn()

    await user.click(await screen.findByRole('tab', { name: 'Schedule' }))

    expect(router.state.location.search).toBe('?tab=schedule')
    expect(await screen.findByText('2 shoot days. 2 dated scenes still need a confirmed location. 1 scene has no shoot date.')).toBeInTheDocument()
    const first = within(screen.getByRole('region', { name: /Monday.*October.*2026/ }))
    const [diner, rooftop] = first.getAllByRole('listitem').filter((item) => item.querySelector('a[href^="/scenes/"]'))
    expect(within(diner).getByRole('link', { name: 'INT. DINER - NIGHT' })).toHaveAttribute('href', '/scenes/s1')
    expect(diner).toHaveTextContent('Late-night diner')
    expect(within(diner).getByRole('link', { name: 'Tom’s Diner' })).toHaveAttribute('href', '/locations/l1')
    expect(diner).toHaveTextContent('782 Washington Ave, Brooklyn, NY')
    expect(rooftop).toHaveTextContent(/until .*14.*2026/)
    expect(rooftop).toHaveTextContent('2 candidates, none confirmed')
    expect(within(screen.getByRole('region', { name: /Tuesday.*October.*2026/ })).getByText('Not scouted yet')).toBeInTheDocument()
    const unscheduled = within(screen.getByRole('region', { name: 'Not scheduled yet' }))
    expect(unscheduled.getByRole('link', { name: 'INT. CAR - DAY' })).toHaveAttribute('href', '/scenes/s4')
    expect(unscheduled.getByText('1 candidate, none confirmed')).toBeInTheDocument()
  })

  it('says when every dated scene has its location, and when nothing is dated yet', async () => {
    let current: Schedule = { days: [{ date: '2026-10-12', scenes: [scheduled()] }], unscheduled: [] }
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json(current) })
    const { unmount } = renderApp('/projects/p1?tab=schedule')
    await logIn()
    expect(await screen.findByText('1 shoot day. Every dated scene has a confirmed location.')).toBeInTheDocument()
    unmount()

    current = { days: [], unscheduled: schedule.unscheduled }
    renderApp('/projects/p1?tab=schedule')
    expect(await screen.findByText('No scene has a shoot date yet.')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Not scheduled yet' })).toBeInTheDocument()
  })

  it('explains itself for a project without scenes', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json({ days: [], unscheduled: [] }) })
    renderApp('/projects/p1?tab=schedule')
    await logIn()

    expect(await screen.findByText(/the schedule builds itself/)).toBeInTheDocument()
    expect(screen.getByText('This project has no scenes yet.')).toBeInTheDocument()
  })
})
