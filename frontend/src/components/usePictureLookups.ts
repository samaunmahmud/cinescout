import { useEffect, useRef } from 'react'
import type { Location } from '../api/types'
import { useSession } from '../auth/context'
import { useStoreLocation } from './locationHooks'

/**
 * Looks up, one at a time in the background, the pictures of the venues in view that have never been looked up,
 * so a list of freshly scouted venues fills in with pictures. Each venue is asked about once per visit, whatever
 * the answer; a failure (the allowance used up, say) just leaves that venue without one for now.
 */
export function usePictureLookups(locations: Location[] | undefined) {
  const { api } = useSession()
  const store = useStoreLocation()
  const asked = useRef(new Set<string>())
  // Stops the queue when the page is left; a new list of venues only adds to it.
  const left = useRef(false)
  useEffect(() => {
    left.current = false
    return () => {
      left.current = true
    }
  }, [])

  useEffect(() => {
    const waiting = (locations ?? []).filter((l) => l.imageCheckedAt === null && l.sourceUrl !== null && !asked.current.has(l.id))
    if (waiting.length === 0) return
    waiting.forEach((l) => asked.current.add(l.id))
    ;(async () => {
      for (const location of waiting) {
        if (left.current) return
        try {
          store(await api.locations.lookUpImage(location.id))
        } catch {
          // Best effort: no picture this time.
        }
      }
    })()
  }, [locations, api, store])
}
