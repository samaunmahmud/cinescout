import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Location, PermitGuidance } from '../api/types'
import { fakeServer, json } from '../test/fakeServer'
import { ada, location, logIn, pageOf, project, scene } from '../test/fixtures'
import { formatDate } from '../lib/format'
import { renderApp } from '../test/renderApp'

const camden: PermitGuidance = {
  status: 'FOUND',
  applies: true,
  areaName: 'London Borough of Camden',
  office: {
    area: 'Camden',
    name: 'Camden Film Office (FilmFixer)',
    contactUrl: 'https://camdenfilmoffice.co.uk/',
    leadTimeWorkingDays: 5,
    leadTimeText: 'Street filming: under 30 crew 5 working days; over 30 crew 7+ working days dependent on location',
    note: null,
    checklist: ['Public liability insurance certificate', 'Risk assessment for the shoot'],
    listed: true,
  },
  lastReviewed: '2026-10-04',
  sources: [{ name: 'Film London, Borough Film Services contacts', url: 'https://filmlondon.org.uk/resource/borough-film-services-contacts' }],
  editUrl: 'https://github.com/samaunmahmud/cinescout/edit/main/backend/src/main/resources/permits/filming-offices.yml',
}

function server(current: () => Location, extra: Parameters<typeof fakeServer>[0] = {}, role = project()) {
  return fakeServer({
    'GET /api/auth/me': () => json(ada),
    'GET /api/locations/l1': () => json(current()),
    'GET /api/locations/l1/director-responses?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/locations/l1/photos?page=0&size=30': () => json(pageOf([], { size: 30 })),
    'GET /api/locations/l1/availability?page=0&size=100': () => json(pageOf([], { size: 100 })),
    'GET /api/scenes/s1': () => json(scene()),
    'GET /api/projects/p1': () => json(role),
    'GET /api/locations/l1/permit': () => json(camden),
    ...extra,
  })
}

describe('the permit guide', () => {
  it('appears once an editor marks the venue as a public space, with its office, lead time and checklist', async () => {
    let venue = location({ bookingFriction: 'COMMERCIAL' })
    const { requests } = server(() => venue, {
      'PUT /api/locations/l1/booking-route': (req) => {
        venue = { ...venue, bookingFriction: (req.body as { bookingFriction: Location['bookingFriction'] }).bookingFriction }
        return json(venue)
      },
    })
    renderApp('/locations/l1')
    const user = await logIn()

    const route = await screen.findByLabelText(/^Who says yes to filming at/)
    expect(route).toHaveValue('COMMERCIAL')
    expect(screen.queryByRole('region', { name: 'Filming permit' })).toBeNull()
    await user.selectOptions(route, 'PUBLIC')

    const permit = within(await screen.findByRole('region', { name: 'Filming permit' }))
    expect(await permit.findByRole('heading', { name: 'Camden Film Office (FilmFixer)' })).toBeInTheDocument()
    expect(permit.getByText(/Apply at least/)).toHaveTextContent('Apply at least 5 working days ahead')
    expect(permit.getByRole('link', { name: /Contact Camden Film Office/ })).toHaveAttribute('href', 'https://camdenfilmoffice.co.uk/')
    expect(permit.getByText('Public liability insurance certificate')).toBeInTheDocument()
    expect(permit.getByText(new RegExp(`Guide last reviewed ${formatDate('2026-10-04')}`))).toBeInTheDocument()
    expect(permit.getByRole('link', { name: 'Correct this guide' })).toHaveAttribute('href', camden.editUrl)
    expect(requests.find((r) => r.method === 'PUT')?.body).toEqual({ bookingFriction: 'PUBLIC' })
  })

  it('asks for a pin first, and says plainly when the area is not covered', async () => {
    let guide: PermitGuidance = { ...camden, status: 'NEEDS_POSITION', areaName: null, office: null }
    server(() => location({ bookingFriction: 'PUBLIC' }), { 'GET /api/locations/l1/permit': () => json(guide) })
    const { unmount } = renderApp('/locations/l1')
    await logIn()
    expect(await screen.findByText(/Put the venue on the map/)).toBeInTheDocument()
    unmount()

    guide = { ...camden, status: 'OUTSIDE_COVERAGE', areaName: 'Kings County', office: null }
    renderApp('/locations/l1')
    expect(await screen.findByText(/covers the UK so far, and this venue is in Kings County/)).toBeInTheDocument()
  })

  it('shows a viewer the booking route as words', async () => {
    server(() => location({ bookingFriction: 'PUBLIC' }), {}, project({ role: 'VIEWER' }))
    renderApp('/locations/l1')
    await logIn()

    expect(await screen.findByRole('region', { name: 'Filming permit' })).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByLabelText(/^Who says yes to filming at/)).toBeNull())
    const booking = screen.getAllByRole('term').find((term) => term.textContent === 'Booking')
    expect(booking?.nextElementSibling).toHaveTextContent('Public space')
  })
})
