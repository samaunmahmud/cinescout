import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Project, Scene, User } from '../api/types'

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

/** Fills in and submits the login form that a logged-out visit lands on. */
export async function logIn(user = userEvent.setup()) {
  await user.type(await screen.findByLabelText('Email'), ada.email)
  await user.type(screen.getByLabelText('Password'), PASSWORD)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
  return user
}
