import { createEvent, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { Schedule } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project, scene, schedule, scheduled } from '../test/fixtures'
import { renderApp } from '../test/renderApp'
import { shortDay } from '../lib/format'

const base = {
  'GET /api/auth/me': () => json(ada),
  'GET /api/projects/p1': () => json(project()),
  'GET /api/projects/p1/scenes?page=0&size=24': () => json(pageOf([])),
  'GET /api/projects/p1/moves': () => json({ days: [], warnAfterMinutes: 60, attribution: '' }),
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
    let current: Schedule = { days: [{ date: '2026-10-12', scenes: [scheduled()] }], unscheduled: [], conflicts: [] }
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json(current) })
    const { unmount } = renderApp('/projects/p1?tab=schedule')
    await logIn()
    expect(await screen.findByText('1 shoot day. Every dated scene has a confirmed location.')).toBeInTheDocument()
    unmount()

    current = { days: [], unscheduled: schedule.unscheduled, conflicts: [] }
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
        current = { days: [schedule.days[0], { date: '2026-10-20', scenes: [...schedule.days[1].scenes, car] }], unscheduled: [], conflicts: [] }
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
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ shootDateStart: '2026-10-20', shootDateEnd: null, callTime: null, wrapTime: null })
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

  it('shows which of the cast works on which day', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json(schedule) })
    renderApp('/projects/p1?tab=schedule')
    await logIn()

    const table = within(await screen.findByRole('table', { name: 'Which of the cast works on which shoot day' }))
    const mara = within(table.getByRole('row', { name: /^MARA/ }))
    expect(mara.getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['Works', 'Works', '2'])
  })

  it('explains itself for a project without scenes', async () => {
    fakeServer({ ...base, 'GET /api/projects/p1/schedule': () => json({ days: [], unscheduled: [] }) })
    renderApp('/projects/p1?tab=schedule')
    await logIn()

    expect(await screen.findByText(/the schedule builds itself/)).toBeInTheDocument()
    expect(screen.getByText('This project has no scenes yet.')).toBeInTheDocument()
  })
})

describe('the stripboard', () => {
  afterEach(() => localStorage.clear())

  /** The schedule as the server holds it, moved along by each PUT of shoot dates. */
  function board(role: 'OWNER' | 'VIEWER' = 'OWNER') {
    let current: Schedule = schedule
    const all = [...schedule.days.flatMap((day) => day.scenes), ...schedule.unscheduled]
    const moved = (id: string, body: { shootDateStart: string | null; shootDateEnd: string | null }) => {
      const strip = { ...all.find((s) => s.id === id)!, ...body }
      const rest = (list: typeof all) => list.filter((s) => s.id !== id)
      const days = current.days.map((day) => ({ ...day, scenes: rest(day.scenes) }))
      if (body.shootDateStart) {
        const day = days.find((d) => d.date === body.shootDateStart)
        if (day) day.scenes.push(strip)
        else days.push({ date: body.shootDateStart, scenes: [strip] })
      }
      current = {
        days: days.filter((day) => day.scenes.length > 0).sort((a, b) => a.date.localeCompare(b.date)),
        unscheduled: body.shootDateStart ? rest(current.unscheduled) : [...rest(current.unscheduled), strip],
        conflicts: [],
      }
      return json(scene({ id, ...body }))
    }
    return fakeServer({
      ...base,
      'GET /api/projects/p1': () => json(project({ role })),
      'GET /api/projects/p1/schedule': () => json(current),
      'PUT /api/scenes/s1/shoot-dates': (request) => moved('s1', request.body as never),
      'PUT /api/scenes/s2/shoot-dates': (request) => moved('s2', request.body as never),
      'PUT /api/scenes/s4/shoot-dates': (request) => moved('s4', request.body as never),
    })
  }

  it('shows each scene as a strip in the board colours under its shoot day, and remembers the view', async () => {
    board()
    const { unmount } = renderApp('/projects/p1?tab=schedule')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Stripboard' }))

    const day1 = within(screen.getByRole('region', { name: /^Day 1/ }))
    const [diner, rooftop] = day1.getAllByRole('listitem')
    expect(diner).toHaveClass('bg-strip-int-night')
    expect(diner).toHaveTextContent('INT. NIGHT')
    expect(diner).toHaveTextContent('Tom’s Diner')
    expect(diner).toHaveTextContent('2 cast')
    expect(diner).toHaveTextContent('3/8 page')
    expect(screen.getByRole('heading', { name: /^Day 1/ })).toHaveTextContent('2 scenes · 6/8 page')
    expect(within(diner).getByRole('link', { name: 'INT. DINER - NIGHT' })).toHaveAttribute('href', '/scenes/s1')
    expect(rooftop).toHaveClass('bg-strip-ext-day')
    expect(rooftop).toHaveTextContent('3 days')
    expect(rooftop).toHaveTextContent('No confirmed location')
    expect(within(screen.getByRole('region', { name: /^Day 2/ })).getByRole('listitem')).toHaveClass('bg-strip-unknown')
    expect(within(screen.getByRole('region', { name: /^Not scheduled/ })).getByRole('listitem')).toHaveTextContent('INT. CAR - DAY')
    expect(screen.queryByRole('table', { name: /cast works/ })).not.toBeInTheDocument()
    unmount()

    renderApp('/projects/p1?tab=schedule')
    expect(await screen.findByRole('button', { name: 'Stripboard' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('moves a strip with its Move to list, keeping a scene over several days the same length', async () => {
    const { requests } = board()
    renderApp('/projects/p1?tab=schedule')
    const user = await logIn()
    await user.click(await screen.findByRole('button', { name: 'Stripboard' }))

    await user.selectOptions(screen.getByLabelText('Move to for EXT. ROOFTOP - DAWN'), 'New day · ' + shortDay('2026-10-21'))

    expect(await within(await screen.findByRole('region', { name: /^Day 3/ })).findByText('EXT. ROOFTOP - DAWN')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')).toMatchObject({
      path: '/api/scenes/s2/shoot-dates',
      body: { shootDateStart: '2026-10-21', shootDateEnd: '2026-10-23', callTime: null, wrapTime: null },
    })

    await user.selectOptions(screen.getByLabelText('Move to for INT. DINER - NIGHT'), 'Not scheduled')
    expect(await within(screen.getByRole('region', { name: /^Not scheduled/ })).findByText('INT. DINER - NIGHT')).toBeInTheDocument()
    expect(requests.filter((r) => r.method === 'PUT')[1].body).toMatchObject({ shootDateStart: null, shootDateEnd: null })
  })

  it('takes a strip dragged onto another day', async () => {
    const { requests } = board()
    renderApp('/projects/p1?tab=schedule')
    const user = await logIn()
    await user.click(await screen.findByRole('button', { name: 'Stripboard' }))

    const car = within(screen.getByRole('region', { name: /^Not scheduled/ })).getByRole('listitem')
    const day2 = screen.getByRole('region', { name: /^Day 2/ })
    const data = dataTransfer()
    fireEvent(car, Object.assign(createEvent.dragStart(car), { dataTransfer: data }))
    fireEvent(day2, Object.assign(createEvent.dragOver(day2), { dataTransfer: data }))
    expect(day2).toHaveClass('ring-cue')
    fireEvent(day2, Object.assign(createEvent.drop(day2), { dataTransfer: data }))

    expect(await within(screen.getByRole('region', { name: /^Day 2/ })).findByText('INT. CAR - DAY')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'PUT')).toMatchObject({ path: '/api/scenes/s4/shoot-dates', body: { shootDateStart: '2026-10-20' } })
  })

  it('is read only for a viewer', async () => {
    board('VIEWER')
    renderApp('/projects/p1?tab=schedule')
    const user = await logIn()
    await user.click(await screen.findByRole('button', { name: 'Stripboard' }))

    expect(await screen.findByRole('region', { name: /^Day 1/ })).toBeInTheDocument()
    expect(screen.queryByLabelText(/^Move to/)).not.toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: /^Day 1/ })).getAllByRole('listitem')[0]).toHaveAttribute('draggable', 'false')
  })
})


/** Enough of a DataTransfer for drag and drop in jsdom, which has none. */
function dataTransfer() {
  const store = new Map<string, string>()
  return {
    get types() {
      return [...store.keys()]
    },
    setData: (type: string, value: string) => store.set(type, value),
    getData: (type: string) => store.get(type) ?? '',
    dropEffect: 'none',
    effectAllowed: 'all',
  }
}
