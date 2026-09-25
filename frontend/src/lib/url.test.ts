import { describe, expect, it } from 'vitest'
import { displayHost, safeHttpUrl } from './url'

describe('safeHttpUrl', () => {
  it('keeps http and https links', () => {
    expect(safeHttpUrl('https://example.com/a?b=1')).toBe('https://example.com/a?b=1')
    expect(safeHttpUrl('http://example.com')).toBe('http://example.com/')
  })

  it('refuses anything that could run script or is not a URL', () => {
    expect(safeHttpUrl('javascript:alert(1)')).toBeNull()
    expect(safeHttpUrl(' JavaScript:alert(1)')).toBeNull()
    expect(safeHttpUrl('data:text/html,<script>alert(1)</script>')).toBeNull()
    expect(safeHttpUrl('/relative/path')).toBeNull()
    expect(safeHttpUrl('')).toBeNull()
    expect(safeHttpUrl(null)).toBeNull()
  })
})

describe('displayHost', () => {
  it('drops the scheme, path and a leading www', () => {
    expect(displayHost('https://www.example.com/venues/1')).toBe('example.com')
    expect(displayHost('https://maps.example.org')).toBe('maps.example.org')
  })
})
