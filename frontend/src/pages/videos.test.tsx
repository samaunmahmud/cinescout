import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { LocationVideos } from '../api/types'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ada, location, locationVideos, logIn, scene, pageOf } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function serverFor(videos: () => Response) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/locations/l1/outreach-drafts?page=0&size=24': () => json(pageOf([])),
    'GET /api/locations/l1/videos': videos,
  })
}

describe("a venue's videos", () => {
  it('show a thumbnail per video, built from its id, and a link to more on YouTube', async () => {
    serverFor(() => json(locationVideos()))
    renderApp('/locations/l1?tab=videos')
    await logIn()

    const list = await screen.findByRole('list', { name: 'Videos of the venue' })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0].querySelector('img')).toHaveAttribute('src', 'https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg')
    expect(within(items[0]).getByRole('link', { name: /Inside Tom’s Diner, Brooklyn/ })).toHaveAttribute(
      'href',
      'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    )
    expect(items[0]).toHaveTextContent('NYC Eats · 2023')
    expect(items[1]).toHaveTextContent('Walks')
    expect(screen.getByRole('link', { name: /More on YouTube/ })).toHaveAttribute(
      'href',
      'https://www.youtube.com/results?search_query=' + encodeURIComponent('Tom’s Diner 782 Washington Ave, Brooklyn, NY'),
    )
  })

  it('play in place with the no-cookie player only when asked', async () => {
    serverFor(() => json(locationVideos()))
    renderApp('/locations/l1?tab=videos')
    const user = await logIn()

    const list = await screen.findByRole('list', { name: 'Videos of the venue' })
    expect(list.querySelector('iframe')).toBeNull()

    await user.click(within(list).getByRole('button', { name: 'Play “Inside Tom’s Diner, Brooklyn”' }))

    const player = list.querySelector('iframe')
    expect(player).toHaveAttribute('title', 'Inside Tom’s Diner, Brooklyn')
    expect(player?.getAttribute('src')).toMatch(/^https:\/\/www\.youtube-nocookie\.com\/embed\/dQw4w9WgXcQ\?/)
    expect(list.querySelectorAll('iframe')).toHaveLength(1)
  })

  it('never put an id that is not a YouTube id into a link', async () => {
    const tampered: LocationVideos = locationVideos({
      videos: [{ id: '"><img src=x onerror=alert(1)>', title: 'Evil', channel: 'X', publishedAt: null }, locationVideos().videos[0]],
    })
    serverFor(() => json(tampered))
    renderApp('/locations/l1?tab=videos')
    await logIn()

    const list = await screen.findByRole('list', { name: 'Videos of the venue' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(1)
    expect(list).not.toHaveTextContent('Evil')
  })

  it('say when none were found', async () => {
    serverFor(() => json(locationVideos({ videos: [] })))
    renderApp('/locations/l1?tab=videos')
    await logIn()

    expect(await screen.findByText('No videos of this venue found.')).toBeInTheDocument()
  })

  it('fall back to a YouTube search when the server cannot search', async () => {
    serverFor(() => problem(503, 'Not available', 'Videos are not configured on this server'))
    renderApp('/locations/l1?tab=videos')
    await logIn()

    expect(await screen.findByText(/Videos are not configured on this server/)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getByRole('link', { name: /More on YouTube/ })).toHaveAttribute(
      'href',
      'https://www.youtube.com/results?search_query=' + encodeURIComponent('Tom’s Diner 782 Washington Ave, Brooklyn, NY'),
    )
  })

  it('offer to try again after another failure', async () => {
    let calls = 0
    serverFor(() => (++calls === 1 ? problem(502, 'Bad gateway', 'The video service rejected our request') : json(locationVideos())))
    renderApp('/locations/l1?tab=videos')
    const user = await logIn()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The video service rejected our request')
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('list', { name: 'Videos of the venue' })).toBeInTheDocument()
  })
})
