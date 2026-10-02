import { lazy, Suspense } from 'react'
import type { MapProps } from './types'

const LeafletMap = lazy(() => import('./LeafletMap'))

/** A map of venue pins. Leaflet is fetched the first time a map is shown. */
export function VenueMap(props: MapProps) {
  const className = props.className ?? 'h-80'
  return (
    <Suspense fallback={<div aria-hidden className={`animate-pulse rounded-lg border-2 border-line bg-white ${className}`} />}>
      <LeafletMap {...props} />
    </Suspense>
  )
}
