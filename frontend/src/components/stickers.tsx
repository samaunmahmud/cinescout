import type { ReactNode } from 'react'

/*
 * Small labels and badges: chips, status pills and a few cinematic marks. They are decoration, hidden from screen
 * readers, unless one is told to announce itself; whatever they say is also said in the page's text. Nothing is
 * set crooked any more: a `tilt` or a rotate class from older callers is ignored.
 */

/** The class list without rotations: the studio look sets nothing at an angle. */
function straight(className: string): string {
  return className
    .split(/\s+/)
    .filter((name) => !/^!?-?rotate-/.test(name) && !/^hover:!?rotate-/.test(name))
    .join(' ')
}

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

/** A small neutral chip with a short word on it (once a strip of masking tape). */
export function TapeLabel({
  children,
  announce = false,
  className = '',
}: {
  children: ReactNode
  /** Ignored: kept so older callers still compile. */
  tilt?: number
  announce?: boolean
  className?: string
}) {
  return (
    <span
      aria-hidden={announce ? undefined : true}
      className={`tape inline-flex items-center gap-1.5 px-2.5 py-1 text-xs leading-tight font-semibold whitespace-nowrap text-graphite ${straight(className)}`}
    >
      {children}
    </span>
  )
}

/**
 * A status pill, e.g. Shortlisted. `tone` picks the colour. Decoration by default; pass `announce` when the pill
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
    <span aria-hidden={announce ? undefined : true} className={`stamp ${inks[tone]} ${straight(className)}`}>
      {children}
    </span>
  )
}

/** The red "Quiet on set" light. */
export function QuietOnSet({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={`flex size-28 flex-col items-center justify-center rounded-full bg-stop text-white shadow-[0_0_0_6px_rgb(229_72_77/0.18),var(--shadow-lift)] ${straight(className)}`}
    >
      <span className="font-display text-lg leading-none font-bold">QUIET</span>
      <span className="text-[11px] font-semibold tracking-[0.14em]">ON SET</span>
    </span>
  )
}

/** An "Admit one" ticket stub, torn at the perforation. */
export function AdmitOne({ className = '' }: { className?: string }) {
  return (
    <span aria-hidden className={`flex drop-shadow-[0_10px_18px_rgb(16_24_40/0.25)] ${straight(className)}`}>
      <span className="flex flex-col items-center rounded-l-lg bg-ink px-4 py-3 text-white">
        <span className="text-[11px] font-semibold tracking-[0.14em] text-cue">ADMIT</span>
        <span className="font-display text-2xl leading-none font-bold">ONE</span>
      </span>
      <span className="rounded-r-lg border-l-2 border-dashed border-ink-line bg-ink px-3 py-3 text-[11px] font-semibold text-fog [writing-mode:vertical-rl]">
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
