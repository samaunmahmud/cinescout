import { BookMarked, Clapperboard, LogOut, Search } from 'lucide-react'
import { useCallback, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { useAuth, useSession } from '../auth/context'
import { AlertBell } from './AlertBell'
import { ThemeToggle } from './ThemeToggle'
import { CommandPalette } from './CommandPalette'
import { useCommandPaletteShortcut } from './useCommandPaletteShortcut'
import { Avatar } from './Avatar'

/** The mark and the name: a clapperboard in an ink tile. `onDark` sets the name in white. */
export function Logo({ size = 'md', onDark = false }: { size?: 'md' | 'lg'; onDark?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${onDark ? 'text-white' : 'text-ink'}`}>
      <span className={`flex items-center justify-center rounded-[9px] bg-brand shadow-[0_6px_16px_-6px_rgb(209_58_33/0.7)] ${size === 'lg' ? 'size-11' : 'size-8'}`}>
        <Clapperboard aria-hidden className={`text-white ${size === 'lg' ? 'size-6' : 'size-[18px]'}`} />
      </span>
      <span className={`font-display leading-none font-bold ${size === 'lg' ? 'text-[1.6rem]' : 'text-lg'}`}>CineScout</span>
    </span>
  )
}

/** The frame around every logged-in page. */
export function Layout() {
  const { user } = useSession()
  const { logOut } = useAuth()
  const [searching, setSearching] = useState(false)
  const openSearch = useCallback(() => setSearching(true), [])
  useCommandPaletteShortcut(openSearch)
  return (
    <div className="flex min-h-dvh flex-col">
      {/* For keyboard and screen reader users: past the header, straight to the page. */}
      <a
        href="#main"
        className="sr-only z-50 rounded-lg bg-night px-4 py-2 font-semibold text-white focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b border-white/10 bg-night/95 text-white backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <Link to="/projects" className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-cue">
              <Logo onDark />
            </Link>
            <nav aria-label="Main" className="hidden items-center gap-1 sm:flex">
              <NavLink
                to="/projects"
                className={({ isActive }) =>
                  `inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-cue ${
                    isActive ? 'bg-white/12 text-white' : 'text-fog hover:bg-white/8 hover:text-white'
                  }`
                }
              >
                <Clapperboard aria-hidden className="size-4" />
                Productions
              </NavLink>
              <NavLink
                to="/library"
                className={({ isActive }) =>
                  `inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-cue ${
                    isActive ? 'bg-white/12 text-white' : 'text-fog hover:bg-white/8 hover:text-white'
                  }`
                }
              >
                <BookMarked aria-hidden className="size-4" />
                My locations
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-2 text-sm">
            <button
              type="button"
              onClick={openSearch}
              aria-label="Search (Ctrl+K)"
              aria-keyshortcuts="Control+K Meta+K"
              className="flex h-10 items-center gap-2.5 rounded-[9px] border border-white/12 bg-white/6 px-3 text-fog transition hover:border-white/25 hover:text-white focus-visible:outline-2 focus-visible:outline-cue lg:min-w-60"
            >
              <Search aria-hidden className="size-4" />
              <span className="hidden lg:inline">Search productions, venues</span>
              <kbd aria-hidden className="ml-auto hidden rounded-md border border-white/15 bg-white/8 px-1.5 py-0.5 font-mono text-[11px] lg:inline">⌘K</kbd>
            </button>
            <ThemeToggle className="text-fog hover:bg-white/8 hover:text-white" />
            <AlertBell />
            <Link
              to="/account"
              aria-label={`Account: ${user.displayName}`}
              className="flex items-center gap-2 rounded-full p-0.5 pr-2 transition hover:bg-white/8 focus-visible:outline-2 focus-visible:outline-cue"
            >
              <Avatar name={user.displayName} size="md" className="!ring-night" />
              <span className="hidden font-medium text-white md:inline">{user.displayName}</span>
            </Link>
            <button
              type="button"
              onClick={logOut}
              className="inline-flex h-10 items-center gap-2 rounded-[9px] px-3 font-semibold text-fog transition hover:bg-white/8 hover:text-white focus-visible:outline-2 focus-visible:outline-cue"
            >
              <LogOut aria-hidden className="size-4" />
              <span className="sr-only sm:not-sr-only">Log out</span>
            </button>
          </div>
        </div>
      </header>
      {searching && <CommandPalette onClose={() => setSearching(false)} />}
      <main id="main" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 focus:outline-none">
        <Outlet />
      </main>
      {/* The end credits. */}
      <footer className="mt-10 border-t border-white/10 bg-night py-8">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 text-sm text-fog">
          <Logo onDark />
          <p>Find the place your scene was written for.</p>
        </div>
      </footer>
    </div>
  )
}
