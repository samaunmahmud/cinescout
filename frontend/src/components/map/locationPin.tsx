import { Link } from 'react-router'
import type { Location } from '../../api/types'
import { fitBand } from '../../lib/fit'
import type { MapPin, PinTone } from './types'

/** What a pin shows of a venue; a row of a project-wide list has it too. */
export type PinnedVenue = Pick<Location, 'id' | 'name' | 'address' | 'latitude' | 'longitude' | 'fitScore' | 'status'>

function toneOf(location: PinnedVenue): PinTone {
  if (location.status === 'REJECTED') return 'muted'
  return location.fitScore == null ? 'neutral' : fitBand(location.fitScore)
}

/** A venue as a map pin, or null while it has no coordinates. */
export function locationPin(location: PinnedVenue, { withLink = true } = {}): MapPin | null {
  if (location.latitude == null || location.longitude == null) return null
  const fit = location.fitScore == null ? 'added by hand' : `fit ${location.fitScore}`
  return {
    id: location.id,
    position: { latitude: location.latitude, longitude: location.longitude },
    label: `${location.name}, ${fit}`,
    tone: toneOf(location),
    popup: withLink ? (
      <div className="space-y-1">
        <Link to={`/locations/${location.id}`} className="font-semibold text-amber-300 hover:underline">
          {location.name}
        </Link>
        {location.address && <p className="!m-0 text-stone-400">{location.address}</p>}
        <p className="!m-0 text-stone-400">{location.fitScore == null ? 'Added by hand' : `Fit ${location.fitScore} out of 100`}</p>
      </div>
    ) : undefined,
  }
}
