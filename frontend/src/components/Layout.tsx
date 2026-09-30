import { Clapperboard, LogOut } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router'
import { useAuth, useSession } from '../auth/context'
import { Button } from './ui'

/** The name as it would be over the door: deco lettering in gold. */
export function Logo({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <img src="/favicon.svg" alt="" className={size === 'lg' ? 'size-11' : 'size-8'} />
      <span className={`gold-leaf font-marquee leading-none ${size === 'lg' ? 'text-4xl' : 'text-[1.45rem]'}`}>CineScout</span>
    </span>
  )
}

/** The frame around every logged-in page. */
export function Layout() {
  const { user } = useSession()
  const { logOut } = useAuth()
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-30 border-b border-amber-300/15 bg-black/85 backdrop-blur-md">
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
                    isActive ? 'bg-amber-300/10 text-amber-100 ring-1 ring-amber-300/20 ring-inset' : 'text-stone-400 hover:text-stone-200'
                  }`
                }
              >
                <Clapperboard aria-hidden className="size-4" />
                Productions
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-2 text-sm">
            <Link
              to="/account"
              aria-label={`Account: ${user.displayName}`}
              className="flex items-center gap-2 rounded-md px-2 py-1 text-stone-400 transition hover:bg-white/5 hover:text-stone-200 focus-visible:outline-2 focus-visible:outline-amber-400"
            >
              <span aria-hidden className="flex size-7 items-center justify-center rounded-full bg-amber-500/15 text-xs font-bold text-amber-300 ring-1 ring-amber-400/30">
                {user.displayName.slice(0, 1).toUpperCase()}
              </span>
              <span className="hidden sm:inline">{user.displayName}</span>
            </Link>
            <Button variant="ghost" onClick={logOut}>
              <LogOut aria-hidden className="size-4" />
              Log out
            </Button>
          </div>
        </div>
        <div aria-hidden className="bulbs opacity-80" />
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-10">
        <Outlet />
      </main>
      {/* The end credits. */}
      <footer className="mt-10 border-t border-amber-300/10 bg-black/60 py-8 text-center">
        <div aria-hidden className="deco-rule mx-auto mb-4 max-w-xs text-xs">◆</div>
        <p className="billing text-[11px] text-stone-500">A CineScout production · Location scouting for film and television</p>
        <p className="mt-3 font-serif text-sm text-stone-600 italic">Find the place your scene was written for.</p>
      </footer>
    </div>
  )
}
