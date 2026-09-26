import { Clapperboard, LogOut } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router'
import { useAuth, useSession } from '../auth/context'
import { Button } from './ui'

export function Logo({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <img src="/favicon.svg" alt="" className={size === 'lg' ? 'size-10' : 'size-8'} />
      <span className={`font-display leading-none tracking-wider text-stone-50 ${size === 'lg' ? 'text-4xl' : 'text-2xl'}`}>
        Cine<span className="text-amber-400">Scout</span>
      </span>
    </span>
  )
}

/** The frame around every logged-in page. */
export function Layout() {
  const { user } = useSession()
  const { logOut } = useAuth()
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-30 border-b border-white/[0.06] bg-ink/85 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <Link to="/projects" className="rounded focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-amber-400">
              <Logo />
            </Link>
            <nav aria-label="Main" className="hidden sm:block">
              <NavLink
                to="/projects"
                className={({ isActive }) =>
                  `inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition ${
                    isActive ? 'bg-white/5 text-stone-50' : 'text-stone-400 hover:text-stone-200'
                  }`
                }
              >
                <Clapperboard aria-hidden className="size-4" />
                Productions
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-2 text-sm">
            <span className="hidden items-center gap-2 text-stone-400 sm:flex">
              <span aria-hidden className="flex size-7 items-center justify-center rounded-full bg-amber-500/15 text-xs font-bold text-amber-300 ring-1 ring-amber-400/30">
                {user.displayName.slice(0, 1).toUpperCase()}
              </span>
              {user.displayName}
            </span>
            <Button variant="ghost" onClick={logOut}>
              <LogOut aria-hidden className="size-4" />
              Log out
            </Button>
          </div>
        </div>
        <div aria-hidden className="film-strip opacity-60" />
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="border-t border-white/[0.05] py-6 text-center text-xs text-stone-600">
        CineScout · find the place your scene was written for
      </footer>
    </div>
  )
}
