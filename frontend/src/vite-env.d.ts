/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** A Leaflet tile URL template; defaults to OpenStreetMap's server (see lib/mapTiles.ts). */
  readonly VITE_MAP_TILE_URL?: string
  /** HTML credit for those tiles, required by most providers. */
  readonly VITE_MAP_TILE_ATTRIBUTION?: string
}
