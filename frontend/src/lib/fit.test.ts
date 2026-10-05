import { describe, expect, it } from 'vitest'
import { fitBand, frictionLabel } from './fit'

describe('fitBand', () => {
  it('splits scores at 75 and 50', () => {
    expect(fitBand(100)).toBe('good')
    expect(fitBand(75)).toBe('good')
    expect(fitBand(74)).toBe('fair')
    expect(fitBand(50)).toBe('fair')
    expect(fitBand(49)).toBe('poor')
    expect(fitBand(0)).toBe('poor')
  })
})

describe('frictionLabel', () => {
  it('names who has to say yes', () => {
    expect(frictionLabel('PUBLIC')).toBe('Public space')
    expect(frictionLabel('COMMERCIAL')).toBe('Business')
    expect(frictionLabel('PRIVATE')).toBe('Private property')
  })
})
