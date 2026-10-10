import { tiles } from './mapTiles'

const TILE = 256

/** One map tile of a snapshot: its address, and where its top-left corner sits relative to the venue's point. */
export interface SnapshotTile {
  url: string
  left: number
  top: number
}

/**
 * The map tiles that cover a box centred on a point (256 pixels high, `width` wide), at `zoom` (Web Mercator, as
 * the map uses), with each tile's offset from the point in pixels. Two tiles high at most.
 */
export function snapshotTiles(latitude: number, longitude: number, zoom = 16, width = TILE): SnapshotTile[] {
  const scale = TILE * 2 ** zoom
  const sin = Math.sin((Math.max(-85.05, Math.min(85.05, latitude)) * Math.PI) / 180)
  const x = ((longitude + 180) / 360) * scale
  const y = (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * scale
  const span = (from: number, extent: number) => {
    const first = Math.floor((from - extent / 2) / TILE)
    const last = Math.floor((from + extent / 2 - 1) / TILE)
    return Array.from({ length: last - first + 1 }, (_, i) => first + i)
  }
  const count = 2 ** zoom
  return span(y, TILE).flatMap((ty) =>
    span(x, width).map((tx) => ({
      url: tiles.url
        .replace('{s}', 'a')
        .replace('{z}', String(zoom))
        .replace('{x}', String(((tx % count) + count) % count))
        .replace('{y}', String(ty))
        .replace('{r}', ''),
      left: Math.round(tx * TILE - x),
      top: Math.round(ty * TILE - y),
    })),
  )
}
