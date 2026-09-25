/**
 * The URL if it is a plain http(s) link, else null. Source URLs of scouted venues come from web search, so
 * they are checked before being rendered as links: a javascript: or data: URL must never become an href.
 */
export function safeHttpUrl(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}

/** "example.com" for "https://www.example.com/venues/1": short enough to show as a link's text. */
export function displayHost(url: string): string {
  return new URL(url).hostname.replace(/^www\./, '')
}
