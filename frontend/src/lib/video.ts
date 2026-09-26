/** A YouTube video id: 11 characters. Anything else is never put into a URL. */
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/

export function isVideoId(id: string): boolean {
  return VIDEO_ID.test(id)
}

export function thumbnailUrl(id: string): string {
  return `https://i.ytimg.com/vi/${id}/mqdefault.jpg`
}

/** The privacy-enhanced player: YouTube sets no cookies until the video is played. */
export function embedUrl(id: string): string {
  return `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0&modestbranding=1`
}

export function watchUrl(id: string): string {
  return `https://www.youtube.com/watch?v=${id}`
}

/** A YouTube search, for when the server cannot search (no key, quota spent) or to see more. */
export function searchUrl(query: string): string {
  return `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`
}
