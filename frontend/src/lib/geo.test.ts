import { describe, expect, it } from 'vitest'
import { osmLink, parseCoordinates } from './geo'

describe('parseCoordinates', () => {
  it('reads the pair as map apps copy it, with a comma or a space', () => {
    expect(parseCoordinates('40.674470, -73.963316')).toEqual({ ok: true, value: { latitude: 40.67447, longitude: -73.963316 } })
    expect(parseCoordinates(' 51.5 -0.12 ')).toEqual({ ok: true, value: { latitude: 51.5, longitude: -0.12 } })
    expect(parseCoordinates('-33,151')).toEqual({ ok: true, value: { latitude: -33, longitude: 151 } })
  })

  it('rounds to the six decimal places the server keeps', () => {
    expect(parseCoordinates('40.71277531, -74.00597229')).toEqual({ ok: true, value: { latitude: 40.712775, longitude: -74.005972 } })
  })

  it('treats blank as no coordinates', () => {
    expect(parseCoordinates('  ')).toEqual({ ok: true, value: null })
  })

  it('rejects anything else, saying what is wrong', () => {
    expect(parseCoordinates('40.6745')).toMatchObject({ ok: false, error: expect.stringMatching(/latitude and longitude/) })
    expect(parseCoordinates('40°40\'N 73°57\'W')).toMatchObject({ ok: false })
    expect(parseCoordinates('91, 10')).toEqual({ ok: false, error: 'Latitude must be between -90 and 90.' })
    expect(parseCoordinates('10, -180.5')).toEqual({ ok: false, error: 'Longitude must be between -180 and 180.' })
  })
})

describe('osmLink', () => {
  it('drops a marker on the spot', () => {
    expect(osmLink({ latitude: 40.67447, longitude: -73.963316 })).toBe(
      'https://www.openstreetmap.org/?mlat=40.67447&mlon=-73.963316#map=17/40.67447/-73.963316',
    )
  })
})
