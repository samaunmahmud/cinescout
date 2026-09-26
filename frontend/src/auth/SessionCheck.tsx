import { Spinner } from '../components/ui'

/** Shown for the moment it takes to ask the server whether the visitor is still logged in. */
export function SessionCheck() {
  return (
    <div className="flex min-h-dvh items-center justify-center">
      <Spinner label="Checking your login" />
    </div>
  )
}
