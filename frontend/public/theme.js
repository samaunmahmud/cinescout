// Applies the person's light or dark choice before the page draws, so it never flashes the other one.
// With no choice saved, the CSS follows the system setting.
try {
  var theme = localStorage.getItem('cinescout-theme')
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme
} catch {
  // Storage blocked (a private window, say): the system setting applies.
}
