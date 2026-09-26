import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Location, LocationVideos, LogisticsReport, OutreachDraft, Project, Scene, User } from '../api/types'

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

/**
 * Based on a real report for a Brooklyn venue (2026-09-25). The live map service was down then, so the
 * surroundings are made up in the shape the server sends.
 */
export function logisticsReport(overrides: Partial<LogisticsReport> = {}): LogisticsReport {
  return {
    version: 1,
    generatedAt: '2026-09-25T22:30:19.412560Z',
    position: { latitude: 40.67447, longitude: -73.963316, geocoded: true },
    timeZone: 'America/New_York',
    shootWindow: { start: '2026-09-28', end: '2026-09-29', assumed: false, truncated: false },
    solar: {
      timeOfDay: 'Night',
      sceneLight: 'NIGHT',
      days: [
        {
          date: '2026-09-28',
          sunrise: '2026-09-28T06:49:00-04:00',
          sunset: '2026-09-28T18:43:00-04:00',
          solarNoon: '2026-09-28T12:46:00-04:00',
          daylightMinutes: 713,
          goldenHours: [
            { start: '2026-09-28T06:33:00-04:00', end: '2026-09-28T07:26:00-04:00' },
            { start: '2026-09-28T18:07:00-04:00', end: '2026-09-28T19:00:00-04:00' },
          ],
          blueHours: [
            { start: '2026-09-28T06:22:00-04:00', end: '2026-09-28T06:33:00-04:00' },
            { start: '2026-09-28T19:00:00-04:00', end: '2026-09-28T19:10:00-04:00' },
          ],
          sceneWindows: [
            { start: '2026-09-28T00:00:00-04:00', end: '2026-09-28T06:22:00-04:00' },
            { start: '2026-09-28T19:10:00-04:00', end: '2026-09-29T00:00:00-04:00' },
          ],
        },
      ],
    },
    weather: {
      status: 'OK',
      message: null,
      days: [
        {
          date: '2026-09-28',
          basis: 'FORECAST',
          referenceDate: '2026-09-28',
          summary: 'Rain',
          temperatureMaxC: 16.4,
          temperatureMinC: 14.3,
          precipitationMm: 7.0,
          precipitationProbabilityPercent: 89,
          windSpeedMaxKmh: 27.4,
          windGustsMaxKmh: 53.6,
          cloudCoverPercent: 100,
          warnings: ['Rain likely: plan cover for cast, crew and equipment'],
        },
      ],
    },
    environment: {
      status: 'OK',
      message: null,
      acousticSensitivity: 'HIGH',
      noiseRisk: 'HIGH',
      noiseSources: [
        { kind: 'MAJOR_ROAD', name: 'Atlantic Avenue', distanceMeters: 120, level: 'HIGH', advice: 'Traffic noise all day: record sound early or late' },
      ],
      nearbyServices: [
        { kind: 'HOSPITAL', name: 'Interfaith Medical Center', distanceMeters: 1900, latitude: 40.6786, longitude: -73.9446 },
        { kind: 'PARKING', name: null, distanceMeters: 240, latitude: null, longitude: null },
      ],
    },
    notes: ["The coordinates were looked up from the venue's address; check the pin on a map and correct it if it is wrong."],
    attribution: ['Geocoding by Nominatim, map data © OpenStreetMap contributors (ODbL)', 'Weather data by Open-Meteo.com (CC BY 4.0)'],
    ...overrides,
  }
}

export function draft(overrides: Partial<OutreachDraft> = {}): OutreachDraft {
  return {
    id: 'd1',
    locationId: 'l1',
    recipientName: 'Tom Miller',
    recipientEmail: 'tom@toms-diner.example',
    subject: 'Filming request: Night Shift at Tom’s Diner',
    body: 'Dear Tom,\n\nWe are making a thriller called Night Shift and would love to film one night scene in your diner.\n\nBest,\nAda',
    tone: 'PROFESSIONAL',
    generatedBy: 'ibm/granite-3-8b-instruct',
    status: 'DRAFT',
    sentAt: null,
    createdAt: '2026-09-20T10:00:00Z',
    updatedAt: '2026-09-20T10:00:00Z',
    ...overrides,
  }
}

/** Fills in and submits the login form that a logged-out visit lands on. */
export function locationVideos(overrides: Partial<LocationVideos> = {}): LocationVideos {
  return {
    query: 'Tom’s Diner 782 Washington Ave, Brooklyn, NY',
    fetchedAt: '2026-09-20T10:00:00Z',
    videos: [
      { id: 'dQw4w9WgXcQ', title: 'Inside Tom’s Diner, Brooklyn', channel: 'NYC Eats', publishedAt: '2023-04-01T12:00:00Z' },
      { id: 'abcDEF12_-3', title: 'Prospect Heights walk', channel: 'Walks', publishedAt: null },
    ],
    ...overrides,
  }
}

export async function logIn(user = userEvent.setup()) {
  await user.type(await screen.findByLabelText('Email'), ada.email)
  await user.type(screen.getByLabelText('Password'), PASSWORD)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
  return user
}
