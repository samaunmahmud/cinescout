export type Theme = 'light' | 'dark'

const KEY = 'cinescout-theme'

/** The theme showing now: the saved choice, else the system's. */
export function currentTheme(): Theme {
  const chosen = document.documentElement.dataset.theme
  if (chosen === 'light' || chosen === 'dark') return chosen
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** Shows `theme` and remembers it for next time (when the browser lets us). */
export function chooseTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // Storage blocked: it lasts for this visit.
  }
}
