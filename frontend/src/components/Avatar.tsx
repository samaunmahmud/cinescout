import { initialsOf } from '../lib/avatar'

/** Colours for avatars: each dark enough for white initials (AA), told apart by lightness as well as hue. */
const palettes = ['#1f3a5f', '#7a2e8e', '#0f6b53', '#9a3412', '#334155', '#1d4ed8', '#9f1239', '#3f6212']

/** The same name always gets the same colour. */
function colourFor(name: string): string {
  let hash = 0
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) | 0
  return palettes[Math.abs(hash) % palettes.length]
}

/** A person's initials on their colour. Decorative unless `label` is given. */
export function Avatar({ name, size = 'md', label, className = '' }: { name: string; size?: 'sm' | 'md' | 'lg'; label?: string; className?: string }) {
  const sizes = { sm: 'size-7 text-[11px]', md: 'size-9 text-[13px]', lg: 'size-12 text-base' }
  return (
    <span
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      title={name}
      style={{ backgroundColor: colourFor(name) }}
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white ring-2 ring-white ${sizes[size]} ${className}`}
    >
      {initialsOf(name)}
    </span>
  )
}

/** A few people as overlapping avatars, the rest as "+3". Reads as "Ada, Grace and 3 more". */
export function AvatarStack({ names, max = 4, size = 'sm' }: { names: string[]; max?: number; size?: 'sm' | 'md' }) {
  const shown = names.slice(0, max)
  const more = names.length - shown.length
  const label = names.length <= max ? names.join(', ') : `${shown.join(', ')} and ${more} more`
  return (
    <span role="img" aria-label={label} className="inline-flex items-center">
      {shown.map((name, index) => (
        <Avatar key={`${name}-${index}`} name={name} size={size} className={index > 0 ? '-ml-2' : ''} />
      ))}
      {more > 0 && (
        <span aria-hidden className={`-ml-2 inline-flex items-center justify-center rounded-full bg-tape font-semibold text-graphite ring-2 ring-white ${size === 'sm' ? 'size-7 text-[11px]' : 'size-9 text-[13px]'}`}>
          +{more}
        </span>
      )}
    </span>
  )
}
