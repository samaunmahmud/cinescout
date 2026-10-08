import type { ShotLightKind, ShotSize, ShotSunMissing } from '../api/types'

export const shotSizes: ShotSize[] = ['WIDE', 'FULL', 'MEDIUM', 'CLOSE_UP', 'EXTREME_CLOSE_UP', 'INSERT', 'AERIAL', 'OTHER']

export const shotSizeLabels: Record<ShotSize, string> = {
  WIDE: 'Wide',
  FULL: 'Full',
  MEDIUM: 'Medium',
  CLOSE_UP: 'Close-up',
  EXTREME_CLOSE_UP: 'Extreme close-up',
  INSERT: 'Insert',
  AERIAL: 'Aerial',
  OTHER: 'Other',
}

/** The eight compass points the camera can face, as bearings. */
export const facings: { bearing: number; label: string }[] = [
  { bearing: 0, label: 'North' },
  { bearing: 45, label: 'North-east' },
  { bearing: 90, label: 'East' },
  { bearing: 135, label: 'South-east' },
  { bearing: 180, label: 'South' },
  { bearing: 225, label: 'South-west' },
  { bearing: 270, label: 'West' },
  { bearing: 315, label: 'North-west' },
]

/** "Faces south-west (225°)", or the exact bearing when it is not one of the eight points. */
export function facingText(bearing: number): string {
  const point = facings.find((facing) => facing.bearing === bearing)
  return point ? `Faces ${point.label.toLowerCase()}` : `Faces ${bearing}°`
}

export const lightLabels: Record<ShotLightKind, string> = {
  BACKLIT: 'Into the sun',
  SIDE_LIT: 'Side light',
  FRONT_LIT: 'Sun behind camera',
  SUN_DOWN: 'Sun down',
}

export const missingSun: Record<ShotSunMissing, string> = {
  NO_VENUE: 'Confirm a venue for the scene, or pick one for the shot, to see the sun.',
  NO_PIN: 'Put the venue on the map to see the sun.',
  NO_DATE: 'Give the scene a shoot date to see the sun.',
  NO_TIME: 'Give the shot a time to see the sun.',
  NO_ZONE: 'Work out the venue’s logistics once (its time zone comes with them) to see the sun.',
}
