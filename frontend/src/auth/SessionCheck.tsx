import { Spinner } from '../components/ui'

/** Shown for the moment it takes to ask the server whether the visitor is still logged in. */
export function SessionCheck() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6">
      <span aria-hidden className="gold-leaf animate-flicker font-marquee text-5xl">CineScout</span>
      <Spinner label="Checking your login" />
    </div>
  )
}
