import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Location, Project, Scene, User } from '../api/types'

export const ada: User = { id: 'u1', email: 'ada@example.com', displayName: 'Ada', role: 'USER', createdAt: '2026-09-01T10:00:00Z' }
export const PASSWORD = 'a-long-password'

export function project(overrides: Partial<Project> = {}): Project {
  return {
    id: 'p1',
    title: 'Night Shift',
    description: 'A thriller set in a hospital.',
    locationArea: 'Brooklyn, New York',
    status: 'ACTIVE',
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
    ...overrides,
  }
}

export function scene(overrides: Partial<Scene> = {}): Scene {
  return {
    id: 's1',
    projectId: 'p1',
    sceneNumber: 12,
    title: 'INT. DINER - NIGHT',
    sourceText: 'A near-empty diner. Rain on the windows. Two detectives talk in low voices.',
    shootDateStart: null,
    shootDateEnd: null,
    parseStatus: 'PENDING',
    requirements: null,
    parsedAt: null,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
    ...overrides,
  }
}

export function location(overrides: Partial<Location> = {}): Location {
  return {
    id: 'l1',
    sceneId: 's1',
    name: 'Tom’s Diner',
    address: '782 Washington Ave, Brooklyn, NY',
    latitude: null,
    longitude: null,
    sourceUrl: 'https://www.toms-diner.example/',
    sourceProvider: 'parallel',
    sourceExcerpt: 'A classic Brooklyn diner since 1936.',
    fitReason: 'Neon sign and red booths match the mood.',
    fitScore: 82,
    bookingFriction: 'COMMERCIAL',
    frictionNote: 'Needs the owner’s permission; closed Mondays.',
    footprintWarnings: ['Narrow street: no room for a generator truck', 'Subway noise every 10 minutes'],
    logistics: null,
    logisticsFetchedAt: null,
    status: 'SUGGESTED',
    notes: null,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
    ...overrides,
  }
}

/** Fills in and submits the login form that a logged-out visit lands on. */
export async function logIn(user = userEvent.setup()) {
  await user.type(await screen.findByLabelText('Email'), ada.email)
  await user.type(screen.getByLabelText('Password'), PASSWORD)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
  return user
}
