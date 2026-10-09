import { useQuery } from '@tanstack/react-query'
import { BookMarked, Clapperboard, Film, FolderArchive, MapPin, Plus, Search, UserRound, type LucideIcon } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import { statusLabels } from '../lib/status'

interface Entry {
  id: string
  group: string
  label: string
  detail?: string
  icon: LucideIcon
  to: string
}

/** Pages anyone can jump to, matched where their words start. */
const commands: Omit<Entry, 'id'>[] = [
  { group: 'Go to', label: 'Productions', icon: Clapperboard, to: '/projects' },
  { group: 'Go to', label: 'Archived productions', icon: FolderArchive, to: '/projects?status=ARCHIVED' },
  { group: 'Go to', label: 'My locations', detail: 'Your venue library', icon: BookMarked, to: '/library' },
  { group: 'Go to', label: 'Account', detail: 'Name, password, calendar feed', icon: UserRound, to: '/account' },
  { group: 'Create', label: 'New production', icon: Plus, to: '/projects?new' },
]

/** Whether every typed word starts one of the words of `text` ("cal" finds "calendar feed", "ed" does not). */
function startsWords(text: string, typed: string): boolean {
  const words = text.toLowerCase().split(/[^\p{L}\p{N}]+/u)
  return typed.split(/\s+/).every((part) => words.some((word) => word.startsWith(part)))
}

/** How long typing has to pause before the server is asked. */
const DEBOUNCE_MS = 200

/**
 * Search and jump: pages and actions, then the productions, scenes and venues whose names match, from the server. A
 * modal dialog with a combobox (arrow keys move, Enter opens, Escape closes and returns focus to where it was).
 */
export function CommandPalette({ onClose }: { onClose: () => void }) {
  const { api } = useSession()
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [debounced, setDebounced] = useState('')
  const [active, setActive] = useState(0)
  const listId = useId()
  const input = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    input.current?.focus()
    return () => opener?.focus()
  }, [])
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(text.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [text])

  const results = useQuery({
    queryKey: queryKeys.search(debounced),
    queryFn: () => api.search(debounced),
    enabled: debounced.length >= 2,
    staleTime: 30_000,
  })

  const entries = useMemo<Entry[]>(() => {
    const words = text.trim().toLowerCase()
    const pages: Entry[] = commands
      .filter((command) => !words || startsWords(`${command.label} ${command.detail ?? ''}`, words))
      .map((command) => ({ ...command, id: `command-${command.label}` }))
    // Once something is typed, the person's own productions, scenes and venues come before the page jumps.
    const list: Entry[] = []
    if (words.length >= 2 && results.data) {
      for (const project of results.data.projects) {
        list.push({ id: `project-${project.id}`, group: 'Productions', label: project.title, detail: project.locationArea ?? undefined, icon: Clapperboard, to: `/projects/${project.id}` })
      }
      for (const scene of results.data.scenes) {
        list.push({
          id: `scene-${scene.id}`,
          group: 'Scenes',
          label: scene.sceneNumber != null ? `${scene.sceneNumber}. ${scene.title}` : scene.title,
          detail: scene.projectTitle,
          icon: Film,
          to: `/scenes/${scene.id}`,
        })
      }
      for (const venue of results.data.venues) {
        list.push({
          id: `venue-${venue.id}`,
          group: 'Venues',
          label: venue.name,
          detail: `${statusLabels[venue.status]} · ${venue.projectTitle}`,
          icon: MapPin,
          to: `/locations/${venue.id}`,
        })
      }
    }
    return [...list, ...pages]
  }, [text, results.data])

  const current = Math.min(active, Math.max(0, entries.length - 1))
  const optionId = (entry: Entry) => `${listId}-${entry.id}`

  function open(entry: Entry) {
    onClose()
    navigate(entry.to)
  }

  function onKeyDown(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') setActive((current + 1) % Math.max(entries.length, 1))
    else if (e.key === 'ArrowUp') setActive((current - 1 + entries.length) % Math.max(entries.length, 1))
    else if (e.key === 'Enter' && entries[current]) open(entries[current])
    else if (e.key === 'Escape') onClose()
    else return
    e.preventDefault()
  }

  useEffect(() => {
    if (entries[current]) document.getElementById(optionId(entries[current]))?.scrollIntoView?.({ block: 'nearest' })
  })

  const searching = text.trim().length >= 2 && (results.isFetching || debounced !== text.trim())
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-night/50 px-4 pt-[12vh]" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div role="dialog" aria-modal="true" aria-label="Search and jump" className="w-full max-w-xl overflow-hidden rounded-lg border border-line bg-paper shadow-[var(--shadow-card)]">
        <div className="flex items-center gap-3 border-b-2 border-ink px-4">
          <Search aria-hidden className="size-5 shrink-0 text-cue-ink" />
          <input
            ref={input}
            role="combobox"
            aria-expanded={entries.length > 0}
            aria-controls={listId}
            aria-activedescendant={entries[current] ? optionId(entries[current]) : undefined}
            aria-autocomplete="list"
            aria-label="Search productions, scenes and venues"
            placeholder="Search scenes, venues…"
            value={text}
            onChange={(e) => {
              setText(e.target.value)
              setActive(0)
            }}
            onKeyDown={onKeyDown}
            className="min-w-0 flex-1 bg-transparent py-4 text-[17px] text-ink placeholder:text-subtle focus:outline-none"
          />
          <kbd className="hidden rounded border border-line px-1.5 py-0.5 font-script text-xs text-muted sm:inline">Esc</kbd>
        </div>
        <ul id={listId} role="listbox" aria-label="Results" className="max-h-[55vh] overflow-y-auto py-2">
          {entries.map((entry, index) => {
            const heading = entries[index - 1]?.group !== entry.group ? entry.group : null
            const Icon = entry.icon
            return (
              <li key={entry.id} role="presentation">
                {heading && (
                  <p role="presentation" className="px-4 pt-2 pb-1 font-script text-[11px] font-bold tracking-[0.12em] text-subtle uppercase">
                    {heading}
                  </p>
                )}
                <div
                  id={optionId(entry)}
                  role="option"
                  aria-selected={index === current}
                  onMouseMove={() => setActive(index)}
                  onClick={() => open(entry)}
                  className={`mx-2 flex cursor-pointer items-center gap-3 rounded-md px-3 py-2 ${index === current ? 'bg-night text-white' : 'text-ink'}`}
                >
                  <Icon aria-hidden className={`size-4 shrink-0 ${index === current ? 'text-cue' : 'text-cue-ink'}`} />
                  <span className="min-w-0 flex-1 truncate font-semibold">{entry.label}</span>
                  {entry.detail && <span className={`max-w-[45%] truncate text-sm ${index === current ? 'text-fog' : 'text-muted'}`}>{entry.detail}</span>}
                </div>
              </li>
            )
          })}
        </ul>
        <p role="status" className="border-t border-line px-4 py-2 text-xs text-muted">
          {searching
            ? 'Searching…'
            : text.trim().length >= 2 && results.data && entries.length === 0
              ? 'Nothing matches.'
              : text.trim().length < 2
                ? 'Type at least 2 letters to search your productions. ↑↓ to move, Enter to open.'
                : `${entries.length} ${entries.length === 1 ? 'result' : 'results'}. ↑↓ to move, Enter to open.`}
        </p>
      </div>
    </div>
  )
}
