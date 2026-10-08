import { useEffect } from 'react'

/** Opens the palette from ⌘K / Ctrl+K anywhere, and from the header's search button (which calls `open`). */
export function useCommandPaletteShortcut(open: () => void) {
  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        open()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])
}
