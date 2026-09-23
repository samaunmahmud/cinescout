import { Link, Outlet } from 'react-router'
import { useSession, useAuth } from '../auth/context'
import { Button } from './ui'

export function Logo() {
  return (
    <span className="inline-flex items-center gap-2 text-lg font-bold tracking-tight">
      <img src="/favicon.svg" alt="" className="size-7" />
      CineScout
    </span>
  )
}

/** The frame around every logged-in page. */
export function Layout() {
  const { user } = useSession()
  const { logOut } = useAuth()
  return (
    <div className="min-h-dvh">
      <header className="border-b border-stone-800 bg-stone-950/80 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/projects" className="rounded focus-visible:outline-2 focus-visible:outline-amber-400">
            <Logo />
          </Link>
          <div className="flex items-center gap-3 text-sm">
            <span className="hidden text-stone-400 sm:inline">{user.displayName}</span>
            <Button variant="ghost" onClick={logOut}>
              Log out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
