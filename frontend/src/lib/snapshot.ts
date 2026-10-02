import { tiles } from './mapTiles'

const TILE = 256

/** One map tile of a snapshot: its address, and where its top-left corner sits relative to the venue's point. */
export interface SnapshotTile {
  url: string
  left: number
  top: number
}

/**
 * The map tiles that cover a box of up to 256 by 256 pixels centred on a point, at `zoom` (Web Mercator, as the
 * map uses), with each tile's offset from the point in pixels. Two by two at most.
 */
export function snapshotTiles(latitude: number, longitude: number, zoom = 16): SnapshotTile[] {
  const scale = TILE * 2 ** zoom
  const sin = Math.sin((Math.max(-85.05, Math.min(85.05, latitude)) * Math.PI) / 180)
  const x = ((longitude + 180) / 360) * scale
  const y = (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * scale
  const span = (from: number) => {
    const first = Math.floor((from - TILE / 2) / TILE)
    const last = Math.floor((from + TILE / 2 - 1) / TILE)
    return first === last ? [first] : [first, last]
  }
  const count = 2 ** zoom
  return span(y).flatMap((ty) =>
    span(x).map((tx) => ({
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
