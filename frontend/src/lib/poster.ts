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
 * look. Hues stay in the tungsten-to-crimson range, with an occasional cool accent, so cards sit well
 * together on the dark page.
 */
export function posterGradient(title: string): string {
  const h = hash(title)
  const hues = [18, 28, 38, 350, 8, 200, 265]
  const a = hues[h % hues.length]
  const b = hues[(h >>> 8) % hues.length]
  const angle = 110 + ((h >>> 16) % 70)
  return `linear-gradient(${angle}deg, hsl(${a} 70% 32%), hsl(${b} 55% 16%) 60%, hsl(${b} 40% 9%))`
}
