export interface Coordinates {
  latitude: number
  longitude: number
}

export type ParsedCoordinates = { ok: true; value: Coordinates | null } | { ok: false; error: string }

const NUMBER = String.raw`[-+]?\d+(?:\.\d+)?`
const PAIR = new RegExp(String.raw`^\s*(${NUMBER})\s*(?:,|\s)\s*(${NUMBER})\s*$`)

/** The server keeps six decimal places (about 10 cm) and rejects more. */
const round6 = (n: number) => Math.round(n * 1e6) / 1e6

/**
 * Reads "latitude, longitude" in decimal degrees, the form map apps copy ("40.674470, -73.963316"); a space
 * works as the separator too. Blank means no coordinates.
 */
export function parseCoordinates(text: string): ParsedCoordinates {
  if (text.trim() === '') return { ok: true, value: null }
  const match = PAIR.exec(text)
  if (!match) return { ok: false, error: 'Enter latitude and longitude in decimal degrees, e.g. 40.6745, -73.9633.' }
  const latitude = Number(match[1])
  const longitude = Number(match[2])
  if (Math.abs(latitude) > 90) return { ok: false, error: 'Latitude must be between -90 and 90.' }
  if (Math.abs(longitude) > 180) return { ok: false, error: 'Longitude must be between -180 and 180.' }
  return { ok: true, value: { latitude: round6(latitude), longitude: round6(longitude) } }
}

export function formatCoordinates({ latitude, longitude }: Coordinates): string {
  return `${latitude}, ${longitude}`
}

/** A link to the spot on OpenStreetMap, for checking a pin. */
export function osmLink({ latitude, longitude }: Coordinates): string {
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`
}
