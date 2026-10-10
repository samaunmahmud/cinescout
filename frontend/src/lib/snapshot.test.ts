import { describe, expect, it } from 'vitest'
import { snapshotTiles } from './snapshot'

describe('snapshotTiles', () => {
  it('finds the tile a point is in, at the right offset', () => {
    // 0, 0 is the corner of four tiles at any zoom: each one touches the point.
    const corner = snapshotTiles(0, 0, 1)
    expect(corner.map((t) => t.url)).toEqual([
      'https://tile.openstreetmap.org/1/0/0.png',
      'https://tile.openstreetmap.org/1/1/0.png',
      'https://tile.openstreetmap.org/1/0/1.png',
      'https://tile.openstreetmap.org/1/1/1.png',
    ])
    expect(corner.map((t) => [t.left, t.top])).toEqual([
      [-256, -256],
      [0, -256],
      [-256, 0],
      [0, 0],
    ])
  })

  it('needs one tile for a point in the middle of it, and covers a box round any point', () => {
    // The middle of tile 1/0/0 (the north-west quarter of the world at zoom 1).
    expect(snapshotTiles(66.51326, -90, 1)).toEqual([{ url: 'https://tile.openstreetmap.org/1/0/0.png', left: -128, top: -128 }])
    for (const tile of snapshotTiles(40.676434, -73.959241)) {
      expect(tile.left).toBeGreaterThan(-384)
      expect(tile.left).toBeLessThanOrEqual(128)
      expect(tile.url).toMatch(/^https:\/\/tile\.openstreetmap\.org\/16\/\d+\/\d+\.png$/)
    }
  })

  it('covers a wider box when asked, still two tiles high at most', () => {
    const wide = snapshotTiles(40.676434, -73.959241, 16, 720)
    const lefts = [...new Set(wide.map((t) => t.left))].sort((a, b) => a - b)
    expect(lefts[0]).toBeLessThanOrEqual(-360)
    expect(lefts[lefts.length - 1] + 256).toBeGreaterThanOrEqual(360)
    expect(new Set(wide.map((t) => t.top)).size).toBeLessThanOrEqual(2)
  })
})
