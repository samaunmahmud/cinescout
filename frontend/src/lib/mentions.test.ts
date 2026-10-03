import { describe, expect, it } from 'vitest'
import { activeMention, insertMention, mentionedIn, splitMentions } from './mentions'

describe('mentions', () => {
  it('finds the mention being typed, only after a space or at the start', () => {
    expect(activeMention('Ask @Gra', 8)).toEqual({ start: 4, query: 'Gra' })
    expect(activeMention('@', 1)).toEqual({ start: 0, query: '' })
    expect(activeMention('Ask @Grace Hop', 14)).toEqual({ start: 4, query: 'Grace Hop' })
    expect(activeMention('mail ada@example.com', 20)).toBeNull()
    expect(activeMention('Ask @Grace\nand', 14)).toBeNull()
    expect(activeMention('No mention here', 15)).toBeNull()
  })

  it('puts the picked name in place of what was typed, the caret after it', () => {
    expect(insertMention('Ask @Gra about it', 4, 8, 'Grace Hopper')).toEqual({ text: 'Ask @Grace Hopper about it', caret: 18 })
    expect(insertMention('@', 0, 1, 'Ada')).toEqual({ text: '@Ada ', caret: 5 })
  })

  it('keeps the members still named in the text, each once', () => {
    const grace = { userId: 'u2', displayName: 'Grace' }
    const hedy = { userId: 'u3', displayName: 'Hedy' }
    expect(mentionedIn('@Grace and @Grace', [grace, hedy, grace])).toEqual([grace])
  })

  it('splits out each mention, the longest name first', () => {
    expect(splitMentions('Hi @Grace Hopper and @Grace!', ['Grace', 'Grace Hopper'])).toEqual([
      { text: 'Hi ', mention: false },
      { text: '@Grace Hopper', mention: true },
      { text: ' and ', mention: false },
      { text: '@Grace', mention: true },
      { text: '!', mention: false },
    ])
    expect(splitMentions('No one (here)', [])).toEqual([{ text: 'No one (here)', mention: false }])
  })
})
