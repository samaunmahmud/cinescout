export type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

export const variants: Record<Variant, string> = {
  // Gold leaf: the one thing on the page that shines, for the main action.
  primary: 'btn-gold focus-visible:outline-amber-200',
  secondary:
    'bg-gradient-to-b from-gate to-frame text-stone-100 ring-1 ring-amber-300/20 ring-inset shadow-sm shadow-black/40 hover:from-stone-700/70 hover:ring-amber-300/45 focus-visible:outline-amber-300',
  danger: 'bg-gradient-to-b from-velvet-500 to-velvet-600 text-white ring-1 ring-velvet-400/60 ring-inset hover:from-velvet-400 hover:to-velvet-500 focus-visible:outline-red-300',
  ghost: 'text-stone-300 hover:bg-white/5 hover:text-stone-100 focus-visible:outline-stone-400',
}

export const buttonBase =
  'inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold tracking-wide transition focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50'

/** Button styling for a router Link that acts as a button. */
export const linkButton = (variant: Variant = 'primary') => `${buttonBase} ${variants[variant]}`
