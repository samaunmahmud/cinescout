import { useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import type { Member } from '../api/types'
import { activeMention, insertMention } from '../lib/mentions'

/**
 * A text box that offers the crew's names after an "@": a combobox, so the list is read out as it changes, the
 * arrow keys move through it, Enter or Tab picks a name and Escape closes it. The picked names are reported with
 * `onMention`; which of them are still in the text is worked out when the comment is sent.
 */
export function MentionTextArea({
  label,
  value,
  onChange,
  members,
  onMention,
  rows = 3,
  autoFocus = false,
}: {
  label: string
  value: string
  onChange: (text: string) => void
  members: Member[]
  onMention: (member: Member) => void
  rows?: number
  autoFocus?: boolean
}) {
  const id = useId()
  const box = useRef<HTMLTextAreaElement>(null)
  const [caret, setCaret] = useState(0)
  const [active, setActive] = useState(0)
  const [dismissed, setDismissed] = useState<number | null>(null)
  // Where the caret goes once a picked name is in the text: set right after that render, before any more typing.
  const placeCaret = useRef<number | null>(null)
  useLayoutEffect(() => {
    if (placeCaret.current === null || !box.current) return
    box.current.focus()
    box.current.setSelectionRange(placeCaret.current, placeCaret.current)
    placeCaret.current = null
  })
  const mention = activeMention(value, caret)
  const matches =
    mention && mention.start !== dismissed
      ? members.filter((member) => member.displayName.toLowerCase().startsWith(mention.query.toLowerCase())).slice(0, 6)
      : []
  const open = matches.length > 0
  const highlighted = Math.min(active, matches.length - 1)

  function pick(member: Member) {
    if (!mention) return
    const next = insertMention(value, mention.start, caret, member.displayName)
    onChange(next.text)
    onMention(member)
    setCaret(next.caret)
    setActive(0)
    placeCaret.current = next.caret
  }

  function keyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (!open) return
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const step = e.key === 'ArrowDown' ? 1 : -1
      setActive((highlighted + step + matches.length) % matches.length)
    } else if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault()
      pick(matches[highlighted])
    } else if (e.key === 'Escape') {
      e.preventDefault()
      setDismissed(mention!.start)
    }
  }

  return (
    <div className="relative space-y-1.5">
      <label htmlFor={id} className="block font-script text-[13px] font-bold tracking-[0.08em] text-ink uppercase">
        {label}
      </label>
      <textarea
        ref={box}
        id={id}
        rows={rows}
        maxLength={4000}
        autoFocus={autoFocus}
        value={value}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-activedescendant={open ? `${id}-option-${highlighted}` : undefined}
        aria-describedby={`${id}-hint`}
        onChange={(e) => {
          onChange(e.target.value)
          setCaret(e.target.selectionStart)
          setActive(0)
        }}
        onSelect={(e) => setCaret(e.currentTarget.selectionStart)}
        onKeyDown={keyDown}
        className="block w-full rounded-lg border border-line bg-paper px-3 py-2 text-[15px] text-ink placeholder:text-subtle focus:ring-4 focus:ring-cue/25 focus:outline-none"
      />
      <p id={`${id}-hint`} className="text-xs text-muted">
        Type @ to mention someone on the crew.
      </p>
      <ul
        id={`${id}-list`}
        role="listbox"
        aria-label="Crew to mention"
        hidden={!open}
        className="absolute z-20 mt-1 w-64 overflow-hidden rounded-lg border border-line bg-paper shadow-[var(--shadow-card)]"
      >
        {matches.map((member, index) => (
          <li
            key={member.userId}
            id={`${id}-option-${index}`}
            role="option"
            aria-selected={index === highlighted}
            onMouseDown={(e) => {
              e.preventDefault()
              pick(member)
            }}
            className={`cursor-pointer px-3 py-2 text-sm ${index === highlighted ? 'bg-cue-wash text-ink' : 'text-graphite'}`}
          >
            <span className="font-semibold">{member.displayName}</span> <span className="text-xs text-muted">{member.email}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
