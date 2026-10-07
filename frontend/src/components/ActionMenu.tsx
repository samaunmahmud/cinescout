import { MoreHorizontal, type LucideIcon } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'

/** One entry of an {@link ActionMenu}: an action, a page in the app (`to`) or a page elsewhere (`href`, new tab). */
export interface MenuAction {
  label: string
  icon?: LucideIcon
  onSelect?: () => void
  to?: string
  href?: string
  /** Shown in red, for actions that remove something. */
  danger?: boolean
  /** Left out of the menu. */
  hidden?: boolean
}

/** The menu's width (w-60). */
const MENU_WIDTH_PX = 240
/** The least room left between the menu and the screen's edge. */
const EDGE_PX = 16

/**
 * A "⋯" button that opens a short menu of quick actions (the WAI-ARIA menu button pattern): arrow keys, Home and End
 * move between the items, Escape closes it and returns to the button, Tab or a click elsewhere closes it.
 */
export function ActionMenu({ label, actions, className = '' }: { label: string; actions: MenuAction[]; className?: string }) {
  const [open, setOpen] = useState(false)
  // Where the menu's left edge sits, from the button's: under the button's right edge, moved in to stay on screen.
  const [offset, setOffset] = useState(0)
  const menuId = useId()
  const button = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLDivElement>(null)
  const wrapper = useRef<HTMLDivElement>(null)
  const shown = actions.filter((action) => !action.hidden)

  const items = () => [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]

  useEffect(() => {
    if (!open) return
    items()[0]?.focus()
    const close = (e: MouseEvent) => {
      if (wrapper.current && !wrapper.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  function onKeyDown(e: KeyboardEvent) {
    const all = items()
    const at = all.indexOf(document.activeElement as HTMLElement)
    const go = (index: number) => all[(index + all.length) % all.length]?.focus()
    if (e.key === 'ArrowDown') go(at + 1)
    else if (e.key === 'ArrowUp') go(at - 1)
    else if (e.key === 'Home') go(0)
    else if (e.key === 'End') go(all.length - 1)
    else if (e.key === 'Escape') {
      setOpen(false)
      button.current?.focus()
    } else if (e.key === 'Tab') {
      setOpen(false)
      return
    } else return
    e.preventDefault()
  }

  if (shown.length === 0) return null
  const itemClass = (danger?: boolean) =>
    `flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm font-semibold transition hover:bg-ground focus-visible:bg-ground focus-visible:outline-none ${
      danger ? 'text-stop-ink' : 'text-ink'
    }`

  return (
    // Raised while open: a transformed parent starts its own stacking layer, which later rows would otherwise cover.
    <div ref={wrapper} className={`relative ${open ? 'z-40' : ''} ${className}`}>
      <button
        ref={button}
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => {
          const at = button.current?.getBoundingClientRect()
          if (at) {
            const left = Math.max(EDGE_PX, Math.min(at.right - MENU_WIDTH_PX, window.innerWidth - EDGE_PX - MENU_WIDTH_PX))
            setOffset(left - at.left)
          }
          setOpen(!open)
        }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown' && !open) {
            e.preventDefault()
            setOpen(true)
          }
        }}
        className="flex size-9 items-center justify-center rounded-lg border-2 border-transparent text-graphite transition hover:border-ink hover:bg-white hover:text-ink focus-visible:ring-4 focus-visible:ring-cue/30 focus-visible:outline-none aria-expanded:border-ink aria-expanded:bg-white aria-expanded:text-ink"
      >
        <MoreHorizontal aria-hidden className="size-5" />
      </button>
      {open && (
        <div
          ref={menu}
          id={menuId}
          role="menu"
          aria-label={label}
          onKeyDown={onKeyDown}
          style={{ left: offset }}
          className={`absolute z-30 mt-1 w-60 overflow-hidden rounded-lg border-2 border-ink bg-white py-1 shadow-[0_4px_0_var(--color-ink)]`}
        >
          {shown.map((action) => {
            const Icon = action.icon
            const content = (
              <>
                {Icon && <Icon aria-hidden className={`size-4 shrink-0 ${action.danger ? 'text-stop-ink' : 'text-cue-ink'}`} />}
                {action.label}
              </>
            )
            const done = () => setOpen(false)
            if (action.to) {
              return (
                <Link key={action.label} role="menuitem" tabIndex={-1} to={action.to} onClick={done} className={itemClass(action.danger)}>
                  {content}
                </Link>
              )
            }
            if (action.href) {
              return (
                <a
                  key={action.label}
                  role="menuitem"
                  tabIndex={-1}
                  href={action.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={done}
                  className={itemClass(action.danger)}
                >
                  {content}
                  <span className="sr-only"> (opens in a new tab)</span>
                </a>
              )
            }
            return (
              <button
                key={action.label}
                type="button"
                role="menuitem"
                tabIndex={-1}
                onClick={() => {
                  done()
                  button.current?.focus()
                  action.onSelect?.()
                }}
                className={itemClass(action.danger)}
              >
                {content}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
