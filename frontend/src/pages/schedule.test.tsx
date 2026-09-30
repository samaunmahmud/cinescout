import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Schedule } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project, scene, schedule, scheduled } from '../test/fixtures'
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

  it('schedules an unscheduled scene in place, and it moves to its day', async () => {
    let current: Schedule = schedule
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/schedule': () => json(current),
      'PUT /api/scenes/s4/shoot-dates': () => {
        const car = { ...schedule.unscheduled[0], shootDateStart: '2026-10-20', shootDateEnd: null }
        current = { days: [schedule.days[0], { date: '2026-10-20', scenes: [...schedule.days[1].scenes, car] }], unscheduled: [] }
        return json(scene({ id: 's4', shootDateStart: '2026-10-20' }))
      },
    })
    renderApp('/projects/p1?tab=schedule')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Schedule INT. CAR - DAY' }))
    const form = within(screen.getByRole('form', { name: 'Shoot dates of INT. CAR - DAY' }))
    await user.type(form.getByLabelText('Last shoot day'), '2026-10-18')
    await user.type(form.getByLabelText('First shoot day'), '2026-10-20')
    expect(form.getByText('The last shoot day cannot be before the first.')).toBeInTheDocument()
    expect(form.getByRole('button', { name: 'Save dates' })).toBeDisabled()
    await user.clear(form.getByLabelText('Last shoot day'))
    await user.click(form.getByRole('button', { name: 'Save dates' }))

    const day = within(await screen.findByRole('region', { name: /Tuesday.*October.*2026/ }))
    expect(await day.findByRole('link', { name: 'INT. CAR - DAY' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Not scheduled yet' })).not.toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ shootDateStart: '2026-10-20', shootDateEnd: null })
  })

  it('opens a dated scene with its dates filled in, to move it', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json(schedule) })
    renderApp('/projects/p1?tab=schedule')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Change the dates of EXT. ROOFTOP - DAWN' }))

    const form = within(screen.getByRole('form', { name: 'Shoot dates of EXT. ROOFTOP - DAWN' }))
    expect(form.getByLabelText('First shoot day')).toHaveValue('2026-10-12')
    expect(form.getByLabelText('Last shoot day')).toHaveValue('2026-10-14')
    await user.click(form.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
  })

  it('fetches the light and weather for confirmed venues that lack them, and shows them', async () => {
    const withoutDay = { ...schedule, days: [{ ...schedule.days[0], scenes: [scheduled({ venues: [{ ...schedule.days[0].scenes[0].venues[0], day: null }] })] }] }
    let current: Schedule = withoutDay
    const { requests } = fakeServer({
      ...base,
      'GET /api/projects/p1/schedule': () => json(current),
      'POST /api/projects/p1/logistics': () => {
        current = { ...withoutDay, days: [{ ...withoutDay.days[0], scenes: [scheduled()] }] }
        return json({ updated: 1, failed: 0, remaining: 0 })
      },
    })
    renderApp('/projects/p1?tab=schedule')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Get light and weather' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Worked out 1 venue.')
    expect(await screen.findByText('Sun 07:04–18:20 · Clear sky, 12–23 °C')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Get light and weather' })).not.toBeInTheDocument()
    expect(requests.filter((r) => r.method === 'POST')).toHaveLength(1)
  })

  it('explains itself for a project without scenes', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json({ days: [], unscheduled: [] }) })
    renderApp('/projects/p1?tab=schedule')
    await logIn()

    expect(await screen.findByText(/the schedule builds itself/)).toBeInTheDocument()
    expect(screen.getByText('This project has no scenes yet.')).toBeInTheDocument()
  })
})
