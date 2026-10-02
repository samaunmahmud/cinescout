import { RotateCcw } from 'lucide-react'
import { useRouteError } from 'react-router'
import { linkButton } from '../components/buttonStyles'
import { Button } from '../components/ui'

/**
 * What a page shows when it fails to render: a way on, never the error itself (which is for the developer
 * console, where React has already logged it). A page whose code could not be downloaded, typically because a
 * new version was deployed since the app was opened, is fixed by reloading, so that is what is offered first.
 */
export function CrashPage() {
  const error = useRouteError()
  const outdated = error instanceof Error && /dynamically imported module|Loading chunk|Importing a module script failed/i.test(error.message)
  return (
    <div role="alert" className="mx-auto max-w-xl animate-fade-in space-y-6 px-4 py-24 text-center">
      <p aria-hidden className="font-display font-extrabold text-7xl leading-none text-ink">
        Reel jam
      </p>
      <h1 className="font-extrabold font-display text-5xl leading-none">Something went wrong</h1>
      <p className="text-lg text-muted">
        {outdated
          ? 'CineScout has been updated since this page was opened. Reload to get the new version.'
          : 'This page could not be shown. Reloading usually helps; your work is saved on the server.'}
      </p>
      <div className="flex flex-wrap justify-center gap-2">
        <Button onClick={() => window.location.reload()}>
          <RotateCcw aria-hidden className="size-4" />
          Reload
        </Button>
        <a href="/projects" className={linkButton('secondary')}>
          Back to your projects
        </a>
      </div>
    </div>
  )
}
