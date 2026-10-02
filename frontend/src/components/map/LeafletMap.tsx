import { divIcon, latLngBounds, type Map as LeafletMapInstance } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef, useState } from 'react'
import { MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import type { Coordinates } from '../../lib/geo'
import { tiles } from '../../lib/mapTiles'
import './map.css'
import type { MapPin, MapProps, PinTone } from './types'

/** Close enough to see the street and the buildings around a venue. */
const SINGLE_PIN_ZOOM = 16
/** Room around the pins, so an opened popup or the attribution does not push one out of view. */
const PADDING: [number, number] = [56, 56]
/** With nothing to show yet, the whole world. */
const WORLD = { center: [20, 0] as [number, number], zoom: 2 }

const pinClasses: Record<PinTone, string> = {
  good: 'bg-go',
  fair: 'bg-cue',
  poor: 'bg-white',
  neutral: 'bg-tape',
  muted: 'bg-line',
}

// Leaflet's default marker is an image that bundlers lose track of; a styled div needs no asset. A push pin: a
// coloured head with an ink rim, as on a scout's board.
function pinIcon(tone: PinTone) {
  return divIcon({
    className: '',
    html: `<span class="block size-5 rounded-full border-[3px] border-ink shadow-[0_2px_0_#0d1321] ${pinClasses[tone]}"></span>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10],
    popupAnchor: [0, -10],
  })
}

/** The Leaflet map itself. Loaded on demand through `VenueMap`, so Leaflet stays out of the main bundle. */
export default function LeafletMap({ pins, label, onPick, className = 'h-80' }: MapProps) {
  // MapContainer reads its view once, on mount; KeepPinsInView moves it after that.
  const [initial] = useState(() => initialView(pins))
  return (
    <div role="region" aria-label={label} className={`cinescout-map overflow-hidden rounded-lg border-2 border-ink ${onPick ? 'picking' : ''} ${className}`}>
      <MapContainer {...initial} className="size-full" scrollWheelZoom={false}>
        <TileLayer url={tiles.url} attribution={tiles.attribution} />
        {pins.map((pin) => (
          <PinMarker key={pin.id} pin={pin} />
        ))}
        <KeepPinsInView pins={pins} refitOnNewPins={!onPick} />
        {onPick && <PickHandler onPick={onPick} />}
      </MapContainer>
    </div>
  )
}

function PinMarker({ pin }: { pin: MapPin }) {
  const icon = useMemo(() => pinIcon(pin.tone), [pin.tone])
  return (
    <Marker position={[pin.position.latitude, pin.position.longitude]} icon={icon} title={pin.label} keyboard>
      {pin.popup && <Popup>{pin.popup}</Popup>}
    </Marker>
  )
}

function initialView(pins: MapPin[]) {
  if (pins.length === 0) return WORLD
  if (pins.length === 1) return { center: toLatLng(pins[0].position), zoom: SINGLE_PIN_ZOOM }
  return { bounds: boundsOf(pins), boundsOptions: { padding: PADDING } }
}

/**
 * Brings the pins into view when one leaves the view (coordinates typed by hand) and, unless the user is
 * picking a spot, when the set of pins changes (a scouting run adds venues). A picked spot never moves the
 * view: the first pick on a world map would otherwise jump to street level.
 */
function KeepPinsInView({ pins, refitOnNewPins }: { pins: MapPin[]; refitOnNewPins: boolean }) {
  const map = useMap()
  const ids = pins.map((pin) => pin.id).join(' ')
  const previousIds = useRef(ids)

  useEffect(() => {
    if (pins.length === 0) return
    const changed = ids !== previousIds.current
    previousIds.current = ids
    const view = map.getBounds()
    if ((changed && refitOnNewPins) || !pins.every((pin) => view.contains(toLatLng(pin.position)))) fit(map, pins)
  }, [map, pins, ids, refitOnNewPins])

  return null
}

function fit(map: LeafletMapInstance, pins: MapPin[]) {
  if (pins.length === 1) map.setView(toLatLng(pins[0].position), Math.max(map.getZoom(), SINGLE_PIN_ZOOM))
  else map.fitBounds(boundsOf(pins), { padding: PADDING })
}

function PickHandler({ onPick }: { onPick: (spot: Coordinates) => void }) {
  useMapEvents({
    click(event) {
      // A click on a copy of the world past the date line still means a real longitude.
      const { lat, lng } = event.latlng.wrap()
      onPick({ latitude: lat, longitude: lng })
    },
  })
  return null
}

const toLatLng = ({ latitude, longitude }: Coordinates): [number, number] => [latitude, longitude]

const boundsOf = (pins: MapPin[]) => latLngBounds(pins.map((pin) => toLatLng(pin.position)))
