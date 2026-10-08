import { describe, expect, it } from 'vitest'
import { quoteAmount } from './budget'

describe('quoteAmount', () => {
  it('reads the first amount in a quote and multiplies a day rate by the shoot days', () => {
    expect(quoteAmount('£800 a day', 2)).toBe(1600)
    expect(quoteAmount('£1,200 per day', 3)).toBe(3600)
    expect(quoteAmount('1 500 €/day', 1)).toBe(1500)
    expect(quoteAmount('$950.50 flat for the shoot', 4)).toBe(950.5)
    expect(quoteAmount('800 daily', null)).toBe(800)
    expect(quoteAmount('Ask the manager', 2)).toBeNull()
    expect(quoteAmount(null, 2)).toBeNull()
  })
})
