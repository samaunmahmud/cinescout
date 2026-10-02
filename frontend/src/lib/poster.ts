/** A small, stable hash of a string: the same title always gets the same colours. */
function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** The colours of a production's card: a panel, the text on it, and its film strip's holes. */
export interface PosterColours {
  panel: string
  text: string
  holes: string
}

// The board's own colours, each with the text that reads on it (AA): ink, cue orange, teal, tape, highlighter.
const palettes: PosterColours[] = [
  { panel: 'bg-ink', text: 'text-white', holes: 'bg-white' },
  { panel: 'bg-cue', text: 'text-ink', holes: 'bg-ink' },
  { panel: 'bg-go', text: 'text-ink', holes: 'bg-ink' },
  { panel: 'bg-tape', text: 'text-ink', holes: 'bg-ink' },
  { panel: 'bg-highlight', text: 'text-ink', holes: 'bg-ink' },
]

/** The colours for a production, chosen from its title so each one keeps its own look. */
export function posterColours(title: string): PosterColours {
  return palettes[hash(title) % palettes.length]
}

/** A small tilt for a card pinned to the board, -0.8 to 0.8 degrees, stable per title. */
export function posterTilt(title: string): number {
  return (((hash(title) >>> 8) % 17) - 8) / 10
}
