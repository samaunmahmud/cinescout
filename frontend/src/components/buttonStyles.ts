export type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

export const variants: Record<Variant, string> = {
  // Tungsten: the one warm light in the room, for the main action.
  primary:
    'bg-gradient-to-b from-amber-400 to-amber-500 text-stone-950 shadow-[0_0_0_1px_rgb(251_191_36/0.4),0_8px_24px_-8px_rgb(245_158_11/0.55)] hover:from-amber-300 hover:to-amber-400 focus-visible:outline-amber-300',
  secondary: 'bg-gate text-stone-100 ring-1 ring-white/10 ring-inset hover:bg-stone-700/80 focus-visible:outline-stone-400',
  danger: 'bg-red-600 text-white hover:bg-red-500 focus-visible:outline-red-300',
  ghost: 'text-stone-300 hover:bg-white/5 hover:text-stone-100 focus-visible:outline-stone-400',
}

export const buttonBase =
  'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50'

/** Button styling for a router Link that acts as a button. */
export const linkButton = (variant: Variant = 'primary') => `${buttonBase} ${variants[variant]}`
