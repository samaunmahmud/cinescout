/**
 * The map's background tiles. The default is OpenStreetMap's own server: free and keyless, but meant for light
 * use only (https://operations.osmfoundation.org/policies/tiles/). A deployment with real traffic should set
 * VITE_MAP_TILE_URL (and its attribution) to a tile provider or its own tile server.
 */
export const tiles = {
  url: import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution:
    import.meta.env.VITE_MAP_TILE_ATTRIBUTION ||
    '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors',
}
