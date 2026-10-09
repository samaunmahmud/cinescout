import { useId } from 'react'

/**
 * A film studio lamp hung from the rig at the top right of a dark panel: a fresnel head on its yoke, barn doors open,
 * throwing a warm yellow beam down and to the left, with a pool of light where it lands. The lamp and its beam are
 * drawn in one frame, rotated together, so the light always leaves the lens. Decoration only: put it first inside a
 * `.hero` (or any positioned, isolated, overflow-hidden dark box); it sits behind the content and takes no clicks.
 *
 * @param aim  where the lamp points, in degrees clockwise from pointing right (135 is down and to the left)
 * @param size `sm` for a small card (a poster), `lg` for a banner
 */
export function StudioLight({ className = '', aim = 142, size = 'lg' }: { className?: string; aim?: number; size?: 'sm' | 'lg' }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  const beam = `beam${id}`
  const core = `core${id}`
  const lens = `lens${id}`
  const soft = `soft${id}`
  const glow = `glow${id}`
  return (
    <div aria-hidden className={`pointer-events-none absolute inset-0 -z-[1] ${className}`}>
      <svg
        viewBox="0 0 600 600"
        preserveAspectRatio="xMaxYMin meet"
        overflow="visible"
        className={`absolute top-0 right-0 h-auto ${size === 'sm' ? 'w-[min(300px,80%)]' : 'w-[min(560px,75%)]'}`}
      >
        <defs>
          <linearGradient id={beam} gradientUnits="userSpaceOnUse" x1="50" y1="0" x2="900" y2="0">
            <stop offset="0" stopColor="#fff2c4" stopOpacity="0.85" />
            <stop offset="0.1" stopColor="#ffd77a" stopOpacity="0.5" />
            <stop offset="0.5" stopColor="#ffc35a" stopOpacity="0.18" />
            <stop offset="1" stopColor="#ffb347" stopOpacity="0" />
          </linearGradient>
          <linearGradient id={core} gradientUnits="userSpaceOnUse" x1="50" y1="0" x2="760" y2="0">
            <stop offset="0" stopColor="#fffbe8" stopOpacity="0.9" />
            <stop offset="0.25" stopColor="#ffe39a" stopOpacity="0.35" />
            <stop offset="1" stopColor="#ffd06a" stopOpacity="0" />
          </linearGradient>
          <radialGradient id={lens} cx="50%" cy="50%" r="50%">
            <stop offset="0" stopColor="#ffffff" />
            <stop offset="0.35" stopColor="#fff1b8" />
            <stop offset="0.75" stopColor="#ffc94d" />
            <stop offset="1" stopColor="#d97706" />
          </radialGradient>
          <filter id={soft} x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="14" />
          </filter>
          <filter id={glow} x="-100%" y="-100%" width="300%" height="300%">
            <feGaussianBlur stdDeviation="7" />
          </filter>
        </defs>

        {/* The rig: a pipe across the top, a drop rod down to the clamp. */}
        <rect x="380" y="-6" width="260" height="14" rx="7" fill="#20252e" stroke="#3a4250" strokeWidth="1.5" />
        <rect x="493" y="6" width="14" height="58" fill="#2a303a" stroke="#3a4250" strokeWidth="1.5" />
        <rect x="486" y="2" width="28" height="12" rx="3" fill="#343b46" />

        <g transform={`translate(500 82) rotate(${aim})`}>
          {/* The light, behind the lamp: a soft wide cone, a brighter core, and the pool where it lands. */}
          <g className="studio-light-breathe" style={{ mixBlendMode: 'screen' }}>
            <polygon points="52,-30 900,-280 900,280 52,30" fill={`url(#${beam})`} filter={`url(#${soft})`} />
            <polygon points="52,-16 760,-120 760,120 52,16" fill={`url(#${core})`} filter={`url(#${soft})`} />
            <ellipse cx="640" cy="0" rx="90" ry="230" fill="#ffd77a" opacity="0.16" filter={`url(#${soft})`} />
          </g>

          {/* The yoke: a U bracket from the clamp round both sides of the head. */}
          <path d="M -6 -48 L 6 -48 L 6 -40 L -6 -40 Z" fill="#3a4250" />
          <path d="M -26 -40 Q 0 -62 26 -40 L 26 40 Q 0 62 -26 40" fill="none" stroke="#2f3640" strokeWidth="7" strokeLinecap="round" />
          <circle cx="0" cy="-40" r="5" fill="#4b5563" />

          {/* The head: rear cap, a finned body, the lens housing. */}
          <rect x="-62" y="-22" width="14" height="44" rx="4" fill="#232831" stroke="#3a4250" strokeWidth="1.5" />
          <rect x="-50" y="-32" width="86" height="64" rx="10" fill="#1b1f27" stroke="#3f4754" strokeWidth="1.5" />
          {[-38, -28, -18, -8].map((x) => (
            <line key={x} x1={x} y1="-30" x2={x} y2="30" stroke="#2c323c" strokeWidth="3" />
          ))}
          <rect x="34" y="-36" width="14" height="72" rx="4" fill="#262b34" stroke="#454d5a" strokeWidth="1.5" />

          {/* The fresnel lens, lit, with its glare. */}
          <circle cx="50" cy="0" r="30" fill="#ffe08a" opacity="0.55" filter={`url(#${glow})`} />
          <ellipse cx="49" cy="0" rx="7" ry="30" fill={`url(#${lens})`} />
          {[-18, -9, 0, 9, 18].map((y) => (
            <line key={y} x1="46" y1={y} x2="52" y2={y} stroke="#f59e0b" strokeOpacity="0.35" strokeWidth="1" />
          ))}

          {/* Barn doors, opened out. */}
          <polygon points="48,-36 88,-58 92,-52 52,-30" fill="#20252e" stroke="#3f4754" strokeWidth="1.5" />
          <polygon points="48,36 88,58 92,52 52,30" fill="#20252e" stroke="#3f4754" strokeWidth="1.5" />
        </g>
      </svg>
    </div>
  )
}
