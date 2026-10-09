/**
 * A studio lamp at the top right of a dark panel, throwing a warm yellow beam down across it and a pool of light where
 * it lands. Decoration only: put it first inside a `.hero` (or any positioned, isolated dark box); it sits behind the
 * content and never takes a click.
 */
export function StudioLight({ className = '' }: { className?: string }) {
  return (
    <div aria-hidden className={`pointer-events-none absolute inset-0 -z-[1] overflow-hidden ${className}`}>
      {/* The beam: a cone from the lamp, soft-edged, brightening what it falls on. */}
      <div className="studio-beam-soft absolute -top-[10%] right-[2%] h-[135%] w-[78%]">
        <div className="studio-beam h-full w-full" />
      </div>
      {/* Where it lands. */}
      <div className="studio-pool absolute right-[8%] bottom-[-18%] h-[55%] w-[70%]" />
      {/* The lamp itself: a fresnel head with barn doors, its lens glowing. */}
      <svg viewBox="0 0 120 120" className="studio-lamp absolute -top-3 -right-3 w-24 sm:w-28">
        <defs>
          <radialGradient id="studio-lens" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#fff8dc" />
            <stop offset="45%" stopColor="#ffd666" />
            <stop offset="100%" stopColor="#f59e0b" stopOpacity="0.2" />
          </radialGradient>
        </defs>
        <g transform="rotate(38 60 60)">
          <rect x="34" y="18" width="52" height="58" rx="8" fill="#1b1f27" stroke="#3a4250" strokeWidth="2" />
          <rect x="40" y="10" width="40" height="10" rx="3" fill="#2a303a" />
          <path d="M30 74 L18 96 L42 86 Z" fill="#232831" stroke="#3a4250" strokeWidth="1.5" />
          <path d="M90 74 L102 96 L78 86 Z" fill="#232831" stroke="#3a4250" strokeWidth="1.5" />
          <ellipse cx="60" cy="80" rx="27" ry="9" fill="url(#studio-lens)" />
        </g>
      </svg>
    </div>
  )
}
