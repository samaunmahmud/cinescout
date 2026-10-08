import { LocateFixed } from 'lucide-react'
import { useState } from 'react'
import { errorMessage } from '../api/errors'
import type { Coordinates } from '../lib/geo'
import { currentPosition, PositionError } from '../lib/currentPosition'
import { Button } from './ui'

/**
 * A "Use my current location" button: asks the browser where we are and hands the position on. `onLocate` may be
 * async (to name the spot, say); whatever goes wrong is said under the button.
 */
export function UseMyLocation({
  onLocate,
  label = 'Use my current location',
}: {
  onLocate: (position: Coordinates) => void | Promise<void>
  label?: string
}) {
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  async function locate() {
    setBusy(true)
    setProblem(null)
    try {
      await onLocate(await currentPosition())
    } catch (error) {
      setProblem(error instanceof PositionError ? error.message : errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-1">
      <Button variant="ghost" busy={busy} onClick={locate}>
        {!busy && <LocateFixed aria-hidden className="size-4" />}
        {busy ? 'Finding you…' : label}
      </Button>
      {problem && (
        <p role="alert" className="text-sm text-stop-ink">
          {problem}
        </p>
      )}
    </div>
  )
}
