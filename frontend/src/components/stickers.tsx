import type { ReactNode } from 'react'

/*
 * The props on the location department's board: tape, stamps and stickers. They are decoration, hidden from
 * screen readers, unless one is told to announce itself; whatever they say is also said in the page's text.
 */

/** The logo: a clapperboard with its sticks open, in cue orange. */
export function ClapperMark({ className = '' }: { className?: string }) {
  return (
    <svg aria-hidden viewBox="0 0 64 56" className={className}>
      <rect x="4" y="22" width="56" height="30" rx="4" className="fill-cue" />
      <g transform="rotate(-14 6 20)">
        <rect x="4" y="9" width="56" height="11" rx="2" fill="#fff" />
        <path d="M12 9h8l-6 11H6zM28 9h8l-6 11h-8zM44 9h8l-6 11h-8z" className="fill-ink" />
      </g>
    </svg>
  )
}

/** A strip of masking tape with a word on it in marker, stuck on slightly crooked. */
export function TapeLabel({
  children,
  tilt = -2,
  announce = false,
  className = '',
}: {
  children: ReactNode
  tilt?: number
  announce?: boolean
  className?: string
}) {
  return (
    <span
      aria-hidden={announce ? undefined : true}
      style={{ transform: `rotate(${tilt}deg)` }}
      className={`tape inline-block px-3.5 py-1 font-marker text-[15px] leading-tight whitespace-nowrap ${className}`}
    >
      {children}
    </span>
  )
}

/**
 * A rubber stamp, e.g. SHORTLISTED. `tone` picks the ink. Decoration by default; pass `announce` when the stamp
 * is the only place the page says it.
 */
export function Stamp({
  children,
  tone = 'cue',
  announce = false,
  className = '',
}: {
  children: ReactNode
  tone?: 'cue' | 'go' | 'stop' | 'ink'
  announce?: boolean
  className?: string
}) {
  const inks = { cue: 'text-cue-ink', go: 'text-go-ink', stop: 'text-stop-ink', ink: 'text-ink' }
  return (
    <span aria-hidden={announce ? undefined : true} className={`stamp bg-white/85 ${inks[tone]} ${className}`}>
      {children}
    </span>
  )
}

/** The red "Quiet on set" sticker. */
export function QuietOnSet({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={`flex size-32 rotate-12 flex-col items-center justify-center rounded-full border-4 border-white bg-stop text-white shadow-[0_6px_0_rgb(0_0_0/0.25)] ${className}`}
    >
      <span className="font-display text-xl leading-none font-extrabold">QUIET</span>
      <span className="font-script text-xs font-bold tracking-[0.12em]">ON SET</span>
    </span>
  )
}

/** An "Admit one" ticket stub, torn at the perforation. */
export function AdmitOne({ className = '' }: { className?: string }) {
  return (
    <span aria-hidden className={`flex -rotate-6 drop-shadow-[0_6px_0_rgb(0_0_0/0.25)] ${className}`}>
      <span className="flex flex-col items-center rounded-l-lg bg-cue px-4 py-3 font-script text-ink">
        <span className="text-[11px] font-bold tracking-[0.14em]">ADMIT</span>
        <span className="font-display text-2xl leading-none font-extrabold">ONE</span>
      </span>
      <span className="rounded-r-lg border-l-2 border-dashed border-ink bg-cue px-3 py-3 font-script text-[11px] font-bold text-ink [writing-mode:vertical-rl]">
        CREW
      </span>
    </span>
  )
}

/** A film reel sticker: five holes round the hub. */
export function FilmReel({ className = '' }: { className?: string }) {
  return (
    <svg aria-hidden viewBox="0 0 74 74" className={className}>
      <circle cx="37" cy="37" r="34" className="fill-ink" stroke="#fff" strokeWidth="4" />
      <circle cx="37" cy="37" r="7" fill="#fff" />
      {[
        [37, 17],
        [56, 31],
        [49, 53],
        [25, 53],
        [18, 31],
      ].map(([cx, cy]) => (
        <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="8" className="fill-ground" />
      ))}
    </svg>
  )
}

/** An envelope sticker with a red seal. */
export function Envelope({ className = '' }: { className?: string }) {
  return (
    <svg aria-hidden viewBox="0 0 64 48" className={className}>
      <rect x="2" y="2" width="60" height="44" rx="4" className="fill-paper stroke-ink" strokeWidth="3" />
      <path d="M4 6l28 20L60 6" fill="none" className="stroke-ink" strokeWidth="3" strokeLinejoin="round" />
      <circle cx="50" cy="36" r="7" className="fill-stop" />
    </svg>
  )
}
