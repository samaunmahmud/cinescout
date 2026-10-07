import { BookmarkPlus, CheckCircle2, Columns3, Copy, ExternalLink, Mail, Star, Umbrella, XCircle } from 'lucide-react'
import type { Location, LocationStatus } from '../api/types'
import type { MenuAction } from './ActionMenu'

/** Google Maps at the venue's pin, or a search for its address; null when it has neither. */
export function mapsUrl(location: Pick<Location, 'latitude' | 'longitude' | 'address' | 'name'>): string | null {
  if (location.latitude != null && location.longitude != null) {
    return `https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}`
  }
  if (location.address) {
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${location.name}, ${location.address}`)}`
  }
  return null
}

/**
 * The quick actions on a venue card: move it along the workflow, keep it as the scene's backup, save it, find it.
 * `run` takes an action that answers with a sentence saying what it did.
 */
export function venueActions(
  location: Location,
  {
    canEdit,
    setStatus,
    run,
    makeCover,
    saveToLibrary,
  }: {
    canEdit: boolean
    setStatus: (status: LocationStatus) => void
    run: (action: () => Promise<string>) => void
    makeCover: () => Promise<string>
    saveToLibrary: () => Promise<string>
  },
): MenuAction[] {
  const maps = mapsUrl(location)
  return [
    { label: 'Shortlist', icon: Star, onSelect: () => setStatus('SHORTLISTED'), hidden: !canEdit || location.status === 'SHORTLISTED' },
    { label: 'Confirm for this scene', icon: CheckCircle2, onSelect: () => setStatus('CONFIRMED'), hidden: !canEdit || location.status === 'CONFIRMED' },
    { label: 'Pass on it', icon: XCircle, onSelect: () => setStatus('REJECTED'), hidden: !canEdit || location.status === 'REJECTED' },
    { label: 'Make it a cover set', icon: Umbrella, onSelect: () => run(makeCover), hidden: !canEdit || location.status === 'CONFIRMED' },
    { label: 'Draft an email', icon: Mail, to: `/locations/${location.id}?tab=outreach`, hidden: !canEdit },
    { label: 'Save to my library', icon: BookmarkPlus, onSelect: () => run(saveToLibrary) },
    {
      label: 'Copy address',
      icon: Copy,
      onSelect: () =>
        run(async () => {
          await navigator.clipboard.writeText(location.address ?? '')
          return 'Address copied.'
        }),
      hidden: !location.address,
    },
    { label: 'Open in Google Maps', icon: ExternalLink, href: maps ?? undefined, hidden: maps == null },
    { label: 'Compare with the others', icon: Columns3, to: `/scenes/${location.sceneId}/compare` },
  ]
}
