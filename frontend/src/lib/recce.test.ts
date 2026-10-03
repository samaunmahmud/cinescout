import { describe, expect, it } from 'vitest'
import { changedAnswers, recceAnswerText, recceQuestions } from './recce'

const question = (name: string) => recceQuestions.find((q) => q.name === name)!

describe('tech recce answers', () => {
  it('read in words', () => {
    expect(recceAnswerText(question('threePhase'), false)).toBe('No')
    expect(recceAnswerText(question('ceilingHeightM'), 3.4)).toBe('3.4 m')
    expect(recceAnswerText(question('stairsOrLift'), 'STAIRS_AND_LIFT')).toBe('Stairs and lift')
    expect(recceAnswerText(question('ambientNoise'), 2)).toBe('2 of 5')
    expect(recceAnswerText(question('notes'), undefined)).toBeNull()
  })

  it('are sent only when they changed, a cleared one as null', () => {
    const stored = { sockets: { value: 6, by: 'u1', byName: 'Ada', at: '2026-10-03T10:00:00Z' }, toilets: { value: true, by: 'u1', byName: 'Ada', at: 'x' } }
    expect(changedAnswers(stored, { sockets: 6, toilets: null, notes: '', ceilingHeightM: 3.2 })).toEqual({ toilets: null, ceilingHeightM: 3.2 })
  })
})
