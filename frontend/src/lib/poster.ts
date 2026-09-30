/** A small, stable hash of a string: the same title always gets the same poster colours. */
function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/**
 * A warm, cinematic gradient for a project card, chosen from the title so each production keeps its own
 * look. The hues are the ones film posters live in, deep and saturated, so the posters sit well together
 * on the dark page.
 */
export function posterGradient(title: string): string {
  const h = hash(title)
  // Film-poster palettes: noir gold, desert sunset, crimson drama, midnight blue, teal thriller, violet dusk.
  const hues = [38, 22, 350, 222, 190, 268, 8]
  const a = hues[h % hues.length]
  const b = hues[(h >>> 8) % hues.length]
  const angle = 150 + ((h >>> 16) % 60)
  return `linear-gradient(${angle}deg, hsl(${a} 72% 38%), hsl(${b} 60% 20%) 55%, hsl(${b} 45% 8%))`
}
