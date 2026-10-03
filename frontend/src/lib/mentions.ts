/*
 * @mentions in a comment: the comment's text says "@Grace Hopper", and the ids of the members mentioned travel with
 * it. These find the mention being typed, put a picked name in, and split a posted comment so its mentions stand out.
 */

/** The "@..." being typed just before the caret: where its "@" is, and what follows it so far. */
export function activeMention(text: string, caret: number): { start: number; query: string } | null {
  const before = text.slice(0, caret)
  const at = before.lastIndexOf('@')
  if (at < 0) return null
  if (at > 0 && !/\s/.test(before[at - 1])) return null
  const query = before.slice(at + 1)
  // A name has spaces in it, but a mention being typed does not span lines, and stops being one after two words.
  if (/[\n@]/.test(query) || query.split(' ').length > 3 || query.length > 40) return null
  return { start: at, query }
}

/** The text with the mention being typed replaced by "@name ", and where the caret goes after it. */
export function insertMention(text: string, start: number, caret: number, name: string): { text: string; caret: number } {
  const inserted = `@${name} `
  const rest = text.slice(caret).replace(/^\S*/, '')
  return { text: text.slice(0, start) + inserted + rest.replace(/^ /, ''), caret: start + inserted.length }
}

/** The members whose "@name" is still in the text, each once. */
export function mentionedIn<T extends { userId: string; displayName: string }>(text: string, members: T[]): T[] {
  const seen = new Set<string>()
  return members.filter((member) => {
    if (seen.has(member.userId) || !text.includes(`@${member.displayName}`)) return false
    seen.add(member.userId)
    return true
  })
}

export type Segment = { text: string; mention: boolean }

/** The text in pieces, the "@name" of each mentioned member as a piece of its own (longest names first). */
export function splitMentions(text: string, names: string[]): Segment[] {
  const sorted = [...new Set(names)].filter(Boolean).sort((a, b) => b.length - a.length)
  if (sorted.length === 0) return [{ text, mention: false }]
  const pattern = new RegExp(`(${sorted.map((name) => `@${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`).join('|')})`, 'g')
  return text
    .split(pattern)
    .filter((piece) => piece !== '')
    .map((piece) => ({ text: piece, mention: sorted.some((name) => piece === `@${name}`) }))
}
