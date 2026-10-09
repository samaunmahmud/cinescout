/** A small, stable hash of a string: the same title always gets the same light. */
function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/**
 * A cinematic backdrop for something with no picture: the brand's red-orange and amber light pooling on night, like
 * a lit set seen from the dark. Where the light falls is chosen from `title`, so each production keeps its own.
 */
export function posterBackdrop(title: string): string {
  const h = hash(title)
  const x = 5 + (h % 40)
  const y = (h >>> 8) % 30
  return [
    `radial-gradient(110% 85% at ${x}% ${y}%, hsl(8 82% 52% / 0.85), transparent 62%)`,
    `radial-gradient(90% 75% at ${95 - (x % 20)}% 100%, hsl(36 90% 50% / 0.55), transparent 66%)`,
    '#08090c',
  ].join(', ')
}
