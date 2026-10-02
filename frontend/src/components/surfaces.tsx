import type { LucideIcon } from 'lucide-react'
import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'

/** A panel on the board: white card with an ink outline and a hard shadow. */
export function Card({ className = '', children }: { className?: string; children: ReactNode }) {
  return (
    <div className={`board-card rounded-lg bg-white ${className}`}>
      {children}
    </div>
  )
}

/** The typed line above a title, like the department on a call sheet. */
export function Eyebrow({ icon: Icon, onDark = false, children }: { icon?: LucideIcon; onDark?: boolean; children: ReactNode }) {
  return (
    <p className={`flex items-center gap-1.5 font-script text-[13px] font-bold tracking-[0.1em] uppercase ${onDark ? 'text-cue' : 'text-cue-ink'}`}>
      {Icon && <Icon aria-hidden className="size-3.5" />}
      {children}
    </p>
  )
}

/**
 * A titled section of a page. `titleId` is the heading's id, for the section's `aria-labelledby`; the title
 * stays a real heading so screen readers can jump between sections.
 */
export function Section({
  titleId,
  title,
  eyebrow,
  icon,
  description,
  actions,
  children,
  className = '',
}: {
  titleId: string
  title: string
  eyebrow?: string
  icon?: LucideIcon
  description?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section aria-labelledby={titleId} className={`animate-fade-in space-y-4 ${className}`}>
      <div className="flex flex-wrap items-end justify-between gap-4 border-b-2 border-ink pb-4">
        <div className="space-y-1">
          {eyebrow && <Eyebrow icon={icon}>{eyebrow}</Eyebrow>}
          <h2 id={titleId} className="font-display text-[2rem] leading-none font-extrabold">
            {title}
          </h2>
          {description && <p className="max-w-prose pt-1 text-[15px] text-muted">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
      {children}
    </section>
  )
}

/** What a section shows before there is anything in it: what it is for, and what to do. */
export function EmptyState({ icon: Icon, children }: { icon?: LucideIcon; children: ReactNode }) {
  return (
    // An empty spot on the board, waiting for something to be pinned to it.
    <div className="relative flex flex-col items-center gap-4 rounded-lg border-2 border-dashed border-line bg-white/60 px-6 py-12 text-center text-muted">
      {Icon && (
        <span className="relative flex size-14 -rotate-6 items-center justify-center rounded-full border-2 border-ink bg-tape text-ink">
          <Icon aria-hidden className="size-6" />
        </span>
      )}
      <div className="relative max-w-md text-[15px] leading-relaxed">{children}</div>
    </div>
  )
}

export interface TabItem<K extends string> {
  key: K
  label: string
  icon?: LucideIcon
}

/**
 * Tabs that follow the ARIA pattern: arrow keys, Home and End move between tabs, and only the selected tab
 * is in the tab order. The caller renders the selected panel inside `children`.
 */
export function Tabs<K extends string>({
  label,
  items,
  selected,
  onSelect,
  children,
}: {
  label: string
  items: TabItem<K>[]
  selected: K
  onSelect: (key: K) => void
  children: ReactNode
}) {
  const base = useId()
  const refs = useRef<Record<string, HTMLButtonElement | null>>({})
  const tabId = (key: K) => `${base}-tab-${key}`
  const panelId = `${base}-panel`

  // On a narrow screen the tabs scroll sideways: keep the selected one in view (opening a link to the last tab, say).
  // Only the strip moves: scrollIntoView would also scroll the page down to a strip below the fold.
  const strip = useRef<HTMLDivElement | null>(null)
  useEffect(() => {
    const tab = refs.current[selected]
    const box = strip.current
    if (!tab || !box) return
    const left = tab.getBoundingClientRect().left - box.getBoundingClientRect().left + box.scrollLeft
    if (left < box.scrollLeft) box.scrollLeft = left
    else if (left + tab.offsetWidth > box.scrollLeft + box.clientWidth) box.scrollLeft = left + tab.offsetWidth - box.clientWidth
  }, [selected])

  function onKeyDown(event: KeyboardEvent) {
    const index = items.findIndex((item) => item.key === selected)
    const next =
      event.key === 'ArrowRight' ? (index + 1) % items.length
      : event.key === 'ArrowLeft' ? (index - 1 + items.length) % items.length
      : event.key === 'Home' ? 0
      : event.key === 'End' ? items.length - 1
      : -1
    if (next < 0) return
    event.preventDefault()
    onSelect(items[next].key)
    refs.current[items[next].key]?.focus()
  }

  return (
    <div className="space-y-6">
      <div
        ref={strip}
        role="tablist"
        aria-label={label}
        onKeyDown={onKeyDown}
        className="sticky top-[82px] z-20 -mx-4 flex gap-1 overflow-x-auto overflow-y-hidden bg-ground/90 px-4 py-2 backdrop-blur [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {items.map(({ key, label: text, icon: Icon }) => {
          const active = key === selected
          return (
            <button
              key={key}
              ref={(element) => {
                refs.current[key] = element
              }}
              id={tabId(key)}
              type="button"
              role="tab"
              aria-selected={active}
              aria-controls={panelId}
              tabIndex={active ? 0 : -1}
              onClick={() => onSelect(key)}
              className={`flex shrink-0 items-center gap-2 rounded-full border-2 px-4 py-2 text-[15px] font-bold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink ${
                active ? 'border-ink bg-ink text-white' : 'border-transparent text-graphite hover:border-ink hover:bg-white'
              }`}
            >
              {Icon && <Icon aria-hidden className={`size-4 ${active ? 'text-cue' : ''}`} />}
              {text}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={panelId} aria-labelledby={tabId(selected)} tabIndex={0} className="focus-visible:outline-none">
        {children}
      </div>
    </div>
  )
}

/** A clapperboard: the scene number chalked on a slate, with the striped sticks on top. */
export function Slate({ number, className = '' }: { number: number | null; className?: string }) {
  return (
    <div aria-hidden className={`w-20 shrink-0 overflow-hidden rounded-md border-2 border-ink bg-ink ${className}`}>
      <div className="clapper h-3.5 border-b-2 border-ink [background-size:auto]" />
      <div className="px-2 pt-1 pb-1.5 text-center">
        <div className="border-b border-ink-line pb-0.5 font-script text-[9px] font-bold tracking-[0.2em] text-ink-muted">SCENE</div>
        <div className="pt-1 font-marker text-3xl leading-none text-paper">{number ?? '—'}</div>
      </div>
    </div>
  )
}

/** One figure in a row of facts: a label, a value, an optional icon. */
export function Fact({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border-[1.5px] border-line bg-white px-3 py-2.5">
      <Icon aria-hidden className="mt-0.5 size-4 shrink-0 text-cue-ink" />
      <div className="min-w-0">
        <dt className="font-script text-[12px] font-bold tracking-[0.08em] text-muted uppercase">{label}</dt>
        <dd className="mt-0.5 text-sm font-semibold text-ink">{children}</dd>
      </div>
    </div>
  )
}
