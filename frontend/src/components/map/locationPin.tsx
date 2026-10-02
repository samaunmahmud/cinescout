import { Link } from 'react-router'
import type { Location } from '../../api/types'
import { fitBand } from '../../lib/fit'
import { VenuePicture } from '../VenuePicture'
import type { MapPin, PinTone } from './types'

/** What a pin shows of a venue; a row of a project-wide list has it too. */
export type PinnedVenue = Pick<Location, 'id' | 'name' | 'address' | 'latitude' | 'longitude' | 'fitScore' | 'status' | 'imageUrl'>

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
        {location.imageUrl && <VenuePicture src={location.imageUrl} className="!mb-1 h-24 w-48 rounded" />}
        <Link to={`/locations/${location.id}`} className="font-bold text-ink underline decoration-cue decoration-2 underline-offset-2">
          {location.name}
        </Link>
        {location.address && <p className="!m-0 text-muted">{location.address}</p>}
        <p className="!m-0 text-muted">{location.fitScore == null ? 'Added by hand' : `Fit ${location.fitScore} out of 100`}</p>
      </div>
    ) : undefined,
  }
}
