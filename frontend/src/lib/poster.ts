/** A small, stable hash of a string: the same title always gets the same look. */
function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/**
 * A cinematic backdrop for a production with no venue picture yet: two pools of coloured light on ink, like a
 * lit set seen from the dark, in hues chosen from its title so each production keeps its own look.
 */
export function posterBackdrop(title: string): string {
  const hue = hash(title) % 360
  const second = (hue + 40) % 360
  return [
    `radial-gradient(110% 85% at 15% 0%, hsl(${hue} 80% 52% / 0.85), transparent 62%)`,
    `radial-gradient(90% 75% at 95% 100%, hsl(${second} 85% 48% / 0.7), transparent 66%)`,
    '#0e1116',
  ].join(', ')
}
