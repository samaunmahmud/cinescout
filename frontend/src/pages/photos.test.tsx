import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Photo, ProjectRole } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

const photo = (overrides: Partial<Photo> = {}): Photo => ({
  id: 'ph1',
  locationId: 'l1',
  url: '/api/public/photos/ph1?size=full&exp=1&sig=a',
  thumbUrl: '/api/public/photos/ph1?size=thumb&exp=1&sig=b',
  width: 400,
  height: 300,
  latitude: 51.5072,
  longitude: -0.1276,
  uploadedBy: 'Ada',
  cover: false,
  createdAt: '2026-10-03T10:00:00Z',
  ...overrides,
})

function server(role: ProjectRole, photos: () => Photo[], extra: Parameters<typeof fakeServer>[0] = {}) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(location()),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(project({ role })),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf(photos(), { size: 30 })),
    ...extra,
  })
}

describe('recce photos', () => {
  it('are added from the venue page, and one that says where it was taken can place the pin', async () => {
    let photos: Photo[] = []
    const { requests } = server('EDITOR', () => photos, {
      'POST /api/locations/l1/photos': () => {
        photos = [photo()]
        return json({ photo: photos[0], suggestPin: true }, 201)
      },
      'PUT /api/locations/l1/coordinates': () => json(location({ latitude: 51.5072, longitude: -0.1276 })),
    })
    renderApp('/locations/l1')
    const user = await logIn()

    expect(await screen.findByText(/No recce photos yet/)).toBeInTheDocument()
    await user.upload(screen.getByLabelText('Add photos'), new File([new Uint8Array([0xff, 0xd8, 0xff])], 'IMG_1.jpg', { type: 'image/jpeg' }))

    const offer = await screen.findByText(/IMG_1.jpg was taken at 51.5072, -0.1276/)
    expect(within(await screen.findByRole('list', { name: 'Recce photos' })).getByRole('img', { name: 'Photo 1 by Ada' })).toHaveAttribute(
      'src',
      '/api/public/photos/ph1?size=thumb&exp=1&sig=b',
    )
    await user.click(within(offer.closest('[role="status"]') as HTMLElement).getByRole('button', { name: 'Place the pin' }))
    await vi.waitFor(() => expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ latitude: 51.5072, longitude: -0.1276 }))
    expect(requests.find((r) => r.method === 'POST')?.path).toBe('/api/locations/l1/photos')
  })

  it('says which photos could not be added and why', async () => {
    server('EDITOR', () => [])
    renderApp('/locations/l1')
    await logIn()

    // The picker offers photos only; a browser may still let another file through.
    const input = await screen.findByLabelText('Add photos')
    fireEvent.change(input, { target: { files: [new File(['gif'], 'anim.gif', { type: 'image/gif' })] } })

    expect(await screen.findByRole('alert')).toHaveTextContent('anim.gif: anim.gif is not a JPEG, PNG or HEIC photo.')
  })

  it('puts a chosen photo on the polaroid, and a viewer only looks', async () => {
    let photos = [photo(), photo({ id: 'ph2', uploadedBy: 'Grace', url: '/x', thumbUrl: '/y' })]
    const { requests } = server('EDITOR', () => photos, {
      'PUT /api/locations/l1/cover': () => {
        photos = [photos[0], { ...photos[1], cover: true }]
        return json(location({ coverPhotoId: 'ph2' }))
      },
    })
    renderApp('/locations/l1')
    const user = await logIn()

    await user.click(await screen.findByRole('button', { name: 'Show Photo 2 by Grace on the polaroid' }))
    expect(await screen.findByText('On the polaroid')).toBeInTheDocument()
    expect(requests.find((r) => r.path === '/api/locations/l1/cover')?.body).toEqual({ photoId: 'ph2' })
  })

  it('cannot be added or changed by a viewer', async () => {
    server('VIEWER', () => [photo()])
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByRole('img', { name: 'Photo 1 by Ada' })).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByLabelText('Add photos')).not.toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /on the polaroid/ })).not.toBeInTheDocument()
  })
})
