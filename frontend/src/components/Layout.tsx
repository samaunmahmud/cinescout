import { Clapperboard, LogOut } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router'
import { useAuth, useSession } from '../auth/context'
import { ClapperMark } from './stickers'

/** The name on the office door: a clapperboard and the word, in heavy type. */
export function Logo({ size = 'md', onDark = true }: { size?: 'md' | 'lg'; onDark?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${onDark ? 'text-white' : 'text-ink'}`}>
      <ClapperMark className={size === 'lg' ? 'h-10 w-11' : 'h-8 w-9'} />
      <span className={`font-display leading-none font-extrabold ${size === 'lg' ? 'text-[1.7rem]' : 'text-[1.35rem]'}`}>CineScout</span>
    </span>
  )
}

/** The frame around every logged-in page. */
export function Layout() {
  const { user } = useSession()
  const { logOut } = useAuth()
  return (
    <div className="flex min-h-dvh flex-col">
      {/* For keyboard and screen reader users: past the header, straight to the page. */}
      <a
        href="#main"
        className="sr-only z-50 rounded-md bg-cue px-4 py-2 font-bold text-ink focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-30 bg-ink text-white">
        <div className="mx-auto flex h-[68px] max-w-6xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <Link to="/projects" className="rounded focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-cue">
              <Logo />
            </Link>
            <nav aria-label="Main" className="hidden sm:block">
              <NavLink
                to="/projects"
                className={({ isActive }) =>
                  `inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm font-bold transition focus-visible:outline-2 focus-visible:outline-cue ${
                    isActive ? 'bg-white text-ink' : 'text-fog hover:text-white'
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
              className="flex items-center gap-2 rounded-lg px-2 py-1 text-fog transition hover:bg-ink-soft hover:text-white focus-visible:outline-2 focus-visible:outline-cue"
            >
              <span aria-hidden className="flex size-8 items-center justify-center rounded-full bg-go-soft font-bold text-go-ink">
                {user.displayName.slice(0, 1).toUpperCase()}
              </span>
              <span className="hidden font-script tracking-[0.04em] uppercase sm:inline">{user.displayName}</span>
            </Link>
            <button
              type="button"
              onClick={logOut}
              className="inline-flex items-center gap-2 rounded-[10px] border-[1.5px] border-ink-line px-3 py-1.5 font-semibold text-white transition hover:border-fog focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cue"
            >
              <LogOut aria-hidden className="size-4" />
              Log out
            </button>
          </div>
        </div>
        <div aria-hidden className="sprockets" />
      </header>
      <main id="main" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 focus:outline-none">
        <Outlet />
      </main>
      {/* The end credits. */}
      <footer className="mt-10 bg-ink py-8 text-center text-ink-muted">
        <p className="font-script text-xs tracking-[0.14em] uppercase">A CineScout production · Location department</p>
        <p className="mt-2 font-marker text-base text-fog">Find the place your scene was written for.</p>
      </footer>
    </div>
  )
}
