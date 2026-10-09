/** "Maya Chen" -> "MC", "Hana (director)" -> "HD"; one word -> its first letter. Only letters count. */
export function initialsOf(name: string): string {
  const words = name
    .split(/\s+/)
    .map((word) => word.replace(/[^\p{L}]/gu, ''))
    .filter(Boolean)
  if (words.length === 0) return '?'
  return (words.length === 1 ? words[0].slice(0, 1) : words[0].slice(0, 1) + words[words.length - 1].slice(0, 1)).toUpperCase()
}
