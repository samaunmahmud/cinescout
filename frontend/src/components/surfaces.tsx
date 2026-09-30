import type { LucideIcon } from 'lucide-react'
import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'

/** A panel: a slightly lifted surface whose top edge catches the light. */
export function Card({ className = '', children }: { className?: string; children: ReactNode }) {
  return (
    <div className={`gilt rounded-xl border border-white/[0.07] bg-gradient-to-b from-frame/95 to-reel/95 shadow-xl shadow-black/50 ${className}`}>
      {children}
    </div>
  )
}

/** The small caps line above a title, like the department on a call sheet. */
export function Eyebrow({ icon: Icon, children }: { icon?: LucideIcon; children: ReactNode }) {
  return (
    <p className="flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.28em] text-amber-300/90 uppercase">
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
      <div className="flex flex-wrap items-end justify-between gap-4 border-b border-amber-300/10 pb-4">
        <div className="space-y-1">
          {eyebrow && <Eyebrow icon={icon}>{eyebrow}</Eyebrow>}
          <h2 id={titleId} className="gold-leaf font-display text-4xl leading-none">
            {title}
          </h2>
          {description && <p className="max-w-prose font-serif text-[15px] text-stone-400 italic">{description}</p>}
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
    // An empty stage: one spotlight, and a word on what belongs in it.
    <div className="relative flex flex-col items-center gap-4 overflow-hidden rounded-xl border border-dashed border-amber-300/15 bg-reel/50 px-6 py-12 text-center text-stone-400">
      <div aria-hidden className="absolute inset-x-0 -top-24 mx-auto h-64 w-72 bg-[radial-gradient(ellipse_at_top,rgb(255_243_196/0.16),transparent_65%)]" />
      {Icon && (
        <span className="relative flex size-14 items-center justify-center rounded-full bg-gradient-to-b from-amber-300/20 to-amber-500/5 text-amber-300 ring-1 ring-amber-300/30">
          <Icon aria-hidden className="size-6" />
        </span>
      )}
      <div className="relative max-w-md font-serif text-[15px] leading-relaxed italic">{children}</div>
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
  useEffect(() => {
    refs.current[selected]?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
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
        role="tablist"
        aria-label={label}
        onKeyDown={onKeyDown}
        className="sticky top-[68px] z-20 -mx-4 flex gap-1 overflow-x-auto overflow-y-hidden border-b border-amber-300/15 bg-ink/90 px-4 backdrop-blur [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
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
              className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-3 font-display text-xl leading-none tracking-wider transition focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-amber-400 ${
                active
                  ? 'border-amber-300 text-amber-100 [text-shadow:0_0_18px_rgb(236_208_120/0.55)]'
                  : 'border-transparent text-stone-500 hover:text-stone-200'
              }`}
            >
              {Icon && <Icon aria-hidden className={`size-4 ${active ? 'text-amber-400' : ''}`} />}
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

/** A clapperboard: the scene number as a slate, with the striped sticks on top. */
export function Slate({ number, className = '' }: { number: number | null; className?: string }) {
  return (
    <div aria-hidden className={`w-20 shrink-0 overflow-hidden rounded-md bg-gradient-to-b from-stone-900 to-black shadow-md shadow-black/60 ring-1 ring-white/15 ${className}`}>
      <div className="clapper h-3 border-b border-black" />
      <div className="px-2 pt-1 pb-1.5 text-center">
        <div className="border-b border-white/10 pb-0.5 text-[8px] font-semibold tracking-[0.3em] text-stone-400">SCENE</div>
        <div className="pt-0.5 font-display text-3xl leading-none text-stone-50">{number ?? '—'}</div>
      </div>
    </div>
  )
}

/** One figure in a row of facts: a label, a value, an optional icon. */
export function Fact({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3 rounded-lg bg-gradient-to-b from-white/[0.05] to-white/[0.015] px-3 py-2.5 ring-1 ring-amber-300/10 ring-inset">
      <Icon aria-hidden className="mt-0.5 size-4 shrink-0 text-amber-400/80" />
      <div className="min-w-0">
        <dt className="text-[11px] font-semibold tracking-wider text-stone-500 uppercase">{label}</dt>
        <dd className="mt-0.5 text-sm text-stone-100">{children}</dd>
      </div>
    </div>
  )
}
