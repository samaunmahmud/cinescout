import { describe, expect, it } from 'vitest'
import { embedUrl, isVideoId, searchUrl, thumbnailUrl } from './video'

describe('video links', () => {
  it('accept only well-formed YouTube ids', () => {
    expect(isVideoId('dQw4w9WgXcQ')).toBe(true)
    expect(isVideoId('abcDEF12_-3')).toBe(true)
    expect(isVideoId('short')).toBe(false)
    expect(isVideoId('dQw4w9WgXcQ"><script>')).toBe(false)
    expect(isVideoId('../../evil1')).toBe(false)
  })

  it('build thumbnails, the no-cookie player and searches', () => {
    expect(thumbnailUrl('dQw4w9WgXcQ')).toBe('https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg')
    expect(embedUrl('dQw4w9WgXcQ')).toMatch(/^https:\/\/www\.youtube-nocookie\.com\/embed\/dQw4w9WgXcQ\?/)
    expect(searchUrl('Joe’s Bar & Grill, Brooklyn')).toBe(
      'https://www.youtube.com/results?search_query=Joe%E2%80%99s%20Bar%20%26%20Grill%2C%20Brooklyn',
    )
  })
})
