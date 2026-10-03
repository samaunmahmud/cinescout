/*
 * Getting a picked photo ready to upload. JPEG and PNG go as they are (the server strips their metadata). HEIC, which
 * iPhones take and the server cannot read, mostly never arrives: the picker asks for JPEG and PNG only, and iOS then
 * hands over a JPEG itself. One that does arrive (picked from a Mac's Finder, say) is re-drawn as a JPEG with the
 * browser's own decoder (Safari has one); where it was taken is read first, as the re-drawing loses it. A browser
 * that cannot decode HEIC is said so plainly. exifr loads only when a HEIC photo is picked.
 */

export const MAX_PHOTO_BYTES = 10 * 1024 * 1024
export const MAX_PHOTOS = 30

export interface PreparedPhoto {
  blob: Blob
  filename: string
  gps: { latitude: number; longitude: number } | null
}

export function isHeic(file: Pick<File, 'name' | 'type'>): boolean {
  return /^image\/hei[cf]$/i.test(file.type) || /\.hei[cf]$/i.test(file.name)
}

export function isAcceptedType(file: Pick<File, 'name' | 'type'>): boolean {
  return isHeic(file) || /^image\/(jpeg|png)$/i.test(file.type) || /\.(jpe?g|png)$/i.test(file.name)
}

/** "IMG_0042.HEIC" becomes "IMG_0042.jpg". */
export function jpegName(name: string): string {
  return name.replace(/\.[^.]*$/, '') + '.jpg'
}

export async function preparePhoto(file: File): Promise<PreparedPhoto> {
  if (!isAcceptedType(file)) throw new Error(`${file.name} is not a JPEG, PNG or HEIC photo.`)
  if (!isHeic(file)) {
    if (file.size > MAX_PHOTO_BYTES) throw new Error(`${file.name} is larger than 10 MB.`)
    return { blob: file, filename: file.name, gps: null }
  }
  const { default: exifr } = await import('exifr')
  let gps: PreparedPhoto['gps'] = null
  try {
    const found = await exifr.gps(file)
    if (found && Number.isFinite(found.latitude) && Number.isFinite(found.longitude)) gps = { latitude: found.latitude, longitude: found.longitude }
  } catch {
    // No position in it: the photo is still worth keeping.
  }
  const blob = await redrawAsJpeg(file)
  if (blob.size > MAX_PHOTO_BYTES) throw new Error(`${file.name} is larger than 10 MB once converted.`)
  return { blob, filename: jpegName(file.name), gps }
}

/** The picture as a JPEG, decoded by the browser itself (turned upright as the photo says) and drawn on a canvas. */
async function redrawAsJpeg(file: File): Promise<Blob> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new Error(
      `${file.name}: this browser cannot read HEIC photos. Save it as a JPEG first (on a Mac, open it in Preview and choose File, Export), or add it from your phone.`,
    )
  }
  const canvas = document.createElement('canvas')
  canvas.width = bitmap.width
  canvas.height = bitmap.height
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0)
  bitmap.close()
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9))
  if (!blob) throw new Error(`${file.name} could not be converted to JPEG.`)
  return blob
}
