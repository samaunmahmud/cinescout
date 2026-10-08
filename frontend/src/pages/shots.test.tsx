import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Shot } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const shot = (overrides: Partial<Shot>): Shot => ({
  id: 'sh1',
  sceneId: 's1',
  number: 1,
  description: 'Wide over the rooftop',
  size: 'WIDE',
  cameraBearing: 180,
  plannedTime: '13:00:00',
  done: false,
  locationId: 'l1',
  venueName: 'Skyline Terrace',
  venueConfirmed: true,
  sun: {
    sun: { azimuth: 186, elevation: 61, compass: 'S', text: 'Sun from S (186°), 61° high at 13:00' },
    light: 'BACKLIT',
    golden: false,
    text: 'Sun from S (186°), 61° high at 13:00; the camera faces the sun: subject backlit, watch for flare.',
  },
  sunMissing: null,
  ...overrides,
})

describe('the shot list', () => {
  it('lists shots with the sun on each, adds one and moves it up', async () => {
    let list: Shot[] = [shot({}), shot({ id: 'sh2', number: 2, description: 'Insert of the note', size: 'INSERT', cameraBearing: null, plannedTime: null, sun: null, sunMissing: 'NO_TIME' })]
    const { requests } = fakeServer({
      'GET /api/auth/me': () => json(ada),
      'GET /api/projects/p1': () => json(project({ role: 'EDITOR' })),
      'GET /api/scenes/s1': () => json(scene()),
      'GET /api/scenes/s1/locations?page=0&size=24': () => json(pageOf([])),
      'GET /api/scenes/s1/covers?page=0&size=50': () => json(pageOf([], { size: 50 })),
      'GET /api/scenes/s1/shots': () => json(list),
      'POST /api/scenes/s1/shots': (req) => {
        const body = req.body as { description: string }
        list = [...list, shot({ id: 'sh3', number: 3, description: body.description, size: 'CLOSE_UP', cameraBearing: 0, sun: { ...list[0].sun!, light: 'FRONT_LIT', text: 'Sun from S; sun behind the camera.' } })]
        return json(list, 201)
      },
      'PUT /api/shots/sh3/position': () => {
        list = [list[0], { ...list[2], number: 2 }, { ...list[1], number: 3 }]
        return json(list)
      },
    })
    renderApp('/scenes/s1')
    const user = await logIn()

    const shots = await screen.findByRole('list', { name: 'Shots' })
    expect(within(shots).getByText('Into the sun')).toBeInTheDocument()
    expect(within(shots).getByText('Give the shot a time to see the sun.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add a shot' }))
    await user.type(screen.getByLabelText(/What the shot is/), 'Close on Anna')
    await user.selectOptions(screen.getByLabelText('Size'), 'CLOSE_UP')
    await user.selectOptions(screen.getByLabelText('Camera faces'), '0')
    await user.type(screen.getByLabelText('Time'), '13:00')
    await user.click(screen.getByRole('button', { name: 'Add the shot' }))

    expect(await within(screen.getByRole('list', { name: 'Shots' })).findByText('Close on Anna')).toBeInTheDocument()
    expect(requests.find((r) => r.method === 'POST')?.body).toEqual({
      description: 'Close on Anna', size: 'CLOSE_UP', cameraBearing: 0, plannedTime: '13:00', done: false, locationId: null,
    })
    expect(screen.getByText('Sun behind camera')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Move shot 3 earlier' }))
    expect(await screen.findByRole('button', { name: 'Move shot 2 earlier' })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Shots' })).getAllByRole('listitem').map((li) => li.textContent?.includes('Close on Anna'))).toEqual([false, true, false])
  })
})
