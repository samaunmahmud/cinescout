import type { ReactNode } from 'react'
import type { Coordinates } from '../../lib/geo'

/** The fit bands, `neutral` for venues nobody has assessed, `muted` for rejected ones. */
export type PinTone = 'good' | 'fair' | 'poor' | 'neutral' | 'muted'

export interface MapPin {
  id: string
  position: Coordinates
  /** The pin's accessible name and hover text. */
  label: string
  tone: PinTone
  /** Shown when the pin is clicked. */
  popup?: ReactNode
}

export interface MapProps {
  pins: MapPin[]
  /** Names the map region for screen readers. */
  label: string
  /** When set, clicking the map picks a spot. */
  onPick?: (spot: Coordinates) => void
  /** Sizing classes; the default is a fixed height. */
  className?: string
}
