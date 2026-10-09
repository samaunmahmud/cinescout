import { describe, expect, it } from 'vitest'
import { initialsOf } from './avatar'

describe('initialsOf', () => {
  it('takes the first and last word, letters only', () => {
    expect(initialsOf('Maya Chen')).toBe('MC')
    expect(initialsOf('Hana (director)')).toBe('HD')
    expect(initialsOf('Amélie Laurent')).toBe('AL')
    expect(initialsOf('june')).toBe('J')
    expect(initialsOf('  ')).toBe('?')
  })
})
