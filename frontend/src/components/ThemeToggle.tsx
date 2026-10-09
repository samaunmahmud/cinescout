import { Moon, Sun } from 'lucide-react'
import { useState } from 'react'
import { chooseTheme, currentTheme, type Theme } from '../lib/theme'

/** Switches between light and dark mode; the button says which one it turns on. */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const [theme, setTheme] = useState<Theme>(() => currentTheme())
  const next: Theme = theme === 'dark' ? 'light' : 'dark'
  return (
    <button
      type="button"
      onClick={() => {
        chooseTheme(next)
        setTheme(next)
      }}
      aria-label={next === 'dark' ? 'Switch to dark mode' : 'Switch to light mode'}
      title={next === 'dark' ? 'Dark mode' : 'Light mode'}
      className={`flex size-10 items-center justify-center rounded-[9px] transition focus-visible:outline-2 focus-visible:outline-cue ${className}`}
    >
      {theme === 'dark' ? <Sun aria-hidden className="size-[18px]" /> : <Moon aria-hidden className="size-[18px]" />}
    </button>
  )
}
