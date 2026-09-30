import { useState } from 'react'

/**
 * A venue's picture from its own web page. Loaded straight from there, without telling that site which page of
 * CineScout asked; a picture that fails to load (moved, blocked) leaves nothing behind rather than a broken image.
 */
export function VenuePicture({ src, className = '' }: { src: string | null; className?: string }) {
  const [failed, setFailed] = useState<string | null>(null)
  if (!src || failed === src) return null
  return (
    <img
      src={src}
      alt=""
      loading="lazy"
      decoding="async"
      referrerPolicy="no-referrer"
      onError={() => setFailed(src)}
      className={`object-cover ${className}`}
    />
  )
}
