export type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

export const variants: Record<Variant, string> = {
  primary: 'bg-amber-500 text-stone-950 hover:bg-amber-400 focus-visible:outline-amber-300',
  secondary: 'bg-stone-800 text-stone-100 hover:bg-stone-700 focus-visible:outline-stone-400',
  danger: 'bg-red-600 text-white hover:bg-red-500 focus-visible:outline-red-300',
  ghost: 'text-stone-300 hover:bg-stone-800 hover:text-stone-100 focus-visible:outline-stone-400',
}

export const buttonBase =
  'inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50'

/** Button styling for a router Link that acts as a button. */
export const linkButton = (variant: Variant = 'primary') => `${buttonBase} ${variants[variant]}`
