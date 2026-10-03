import { describe, expect, it } from 'vitest'
import { isAcceptedType, isHeic, jpegName, MAX_PHOTO_BYTES, preparePhoto } from './photoPrep'

describe('getting a photo ready to upload', () => {
  it('knows a HEIC photo by its type or its name', () => {
    expect(isHeic({ name: 'IMG_0042.HEIC', type: '' })).toBe(true)
    expect(isHeic({ name: 'photo', type: 'image/heif' })).toBe(true)
    expect(isHeic({ name: 'a.jpg', type: 'image/jpeg' })).toBe(false)
    expect(isAcceptedType({ name: 'a.png', type: 'image/png' })).toBe(true)
    expect(isAcceptedType({ name: 'a.gif', type: 'image/gif' })).toBe(false)
    expect(jpegName('IMG_0042.HEIC')).toBe('IMG_0042.jpg')
  })

  it('sends a JPEG as it is and refuses what is not a photo or too large', async () => {
    const jpeg = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'a.jpg', { type: 'image/jpeg' })
    expect(await preparePhoto(jpeg)).toEqual({ blob: jpeg, filename: 'a.jpg', gps: null })
    await expect(preparePhoto(new File(['<svg/>'], 'a.svg', { type: 'image/svg+xml' }))).rejects.toThrow('not a JPEG, PNG or HEIC')
    const huge = new File([new Uint8Array(MAX_PHOTO_BYTES + 1)], 'big.jpg', { type: 'image/jpeg' })
    await expect(preparePhoto(huge)).rejects.toThrow('larger than 10 MB')
  })
})

describe('a HEIC photo', () => {
  it('is refused plainly by a browser that cannot decode it', async () => {
    // jsdom, like Chrome on Windows, cannot decode HEIC.
    const heic = new File([new Uint8Array([0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70])], 'IMG_7.HEIC', { type: 'image/heic' })
    await expect(preparePhoto(heic)).rejects.toThrow('IMG_7.HEIC: this browser cannot read HEIC photos. Save it as a JPEG first')
  })
})
