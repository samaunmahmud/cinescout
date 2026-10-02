export type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

export const variants: Record<Variant, string> = {
  // Cue orange with an ink outline that presses down: the one action the page is asking for.
  primary: 'btn-cue focus-visible:outline-ink',
  secondary: 'border-2 border-ink bg-white text-ink hover:bg-ground focus-visible:outline-ink',
  danger: 'border-2 border-stop-ink bg-stop text-white hover:bg-stop-ink focus-visible:outline-stop-ink',
  ghost: 'text-graphite hover:bg-ink/5 hover:text-ink focus-visible:outline-ink',
}

export const buttonBase =
  'inline-flex items-center justify-center gap-2 rounded-[10px] px-4 py-2 text-sm font-bold transition focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50'

/** Button styling for a router Link that acts as a button. */
export const linkButton = (variant: Variant = 'primary') => `${buttonBase} ${variants[variant]}`
