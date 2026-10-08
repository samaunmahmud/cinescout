import { roundCoordinates, type Coordinates } from './geo'

/** Why the browser could not say where we are, in words a person can act on. */
export class PositionError extends Error {}

/**
 * The browser's current position, rounded to what the server keeps. Asks the person the first time; fails with a
 * {@link PositionError} saying what to do when the browser cannot or will not tell.
 */
export function currentPosition(geolocation: Geolocation | undefined = globalThis.navigator?.geolocation): Promise<Coordinates> {
  if (!geolocation) return Promise.reject(new PositionError('This browser cannot share its location. Type the place instead.'))
  if (globalThis.isSecureContext === false) {
    return Promise.reject(new PositionError('Your location can only be shared over a secure (https) connection.'))
  }
  return new Promise((resolve, reject) =>
    geolocation.getCurrentPosition(
      (position) => resolve(roundCoordinates({ latitude: position.coords.latitude, longitude: position.coords.longitude })),
      (error) =>
        reject(
          new PositionError(
            error.code === 1
              ? 'Location is blocked for this site. Allow it in the browser’s site settings, then try again.'
              : error.code === 3
                ? 'Finding your location took too long. Try again, ideally near a window or with Wi-Fi on.'
                : 'Your location could not be found just now. Try again, or type the place.',
          ),
        ),
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    ),
  )
}
