import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookMarked, Navigation, MapPin as PinIcon, Search, Tag, X } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { LibraryVenue } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { LocationBadges } from '../components/locationParts'
import { MapSnapshot } from '../components/MapSnapshot'
import { Pager } from '../components/Pager'
import { EmptyState, Eyebrow } from '../components/surfaces'
import { Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { VenuePicture } from '../components/VenuePicture'
import { UseMyLocation } from '../components/UseMyLocation'
import { distanceText, formatCoordinates, type Coordinates } from '../lib/geo'
import { usePageTitle } from '../lib/usePageTitle'

/** The user's own library of venues: search it, filter it by tag, tag and annotate each one. */
export function LibraryPage() {
  const { api } = useSession()
  usePageTitle('My locations')
  const [typed, setTyped] = useState('')
  const [search, setSearch] = useState('')
  const [tag, setTag] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [near, setNear] = useState<Coordinates | null>(null)
  // Search as the user pauses typing, not on every key.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(typed.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [typed])
  const venues = useQuery({
    queryKey: queryKeys.libraryPage(search, tag, page, near && formatCoordinates(near)),
    queryFn: () => api.library.list(search, tag, page, near),
  })
  const tags = useQuery({ queryKey: queryKeys.libraryTags, queryFn: () => api.library.tags() })

  return (
    <div className="space-y-8">
      <header className="space-y-2">
        <Eyebrow icon={BookMarked}>Your own</Eyebrow>
        <h1 className="font-display text-5xl leading-none">My locations</h1>
        <p className="max-w-prose text-muted">
          Venues you saved from any production, to use again. Add one to a scene from that scene’s “Add venue” page, without scouting again.
        </p>
      </header>

      <div className="space-y-3">
        <div className="relative max-w-md">
          <Search aria-hidden className="pointer-events-none absolute top-[2.6rem] left-3 size-4 text-muted" />
          <TextField label="Search your locations" type="search" value={typed} onChange={(e) => setTyped(e.target.value)} className="pl-9" />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {near ? (
            <>
              <p role="status" className="flex items-center gap-1.5 text-sm font-semibold text-ink">
                <Navigation aria-hidden className="size-4 text-cue-ink" />
                Nearest to you first
              </p>
              <Button
                variant="ghost"
                onClick={() => {
                  setNear(null)
                  setPage(0)
                }}
              >
                Newest first instead
              </Button>
            </>
          ) : (
            <UseMyLocation
              label="Nearest to me first"
              onLocate={(here) => {
                setNear(here)
                setPage(0)
              }}
            />
          )}
        </div>
        {tags.data && tags.data.length > 0 && (
          <div role="group" aria-label="Filter by tag" className="flex flex-wrap gap-2">
            {[{ tag: null as string | null, venues: 0 }, ...tags.data].map((option) => {
              const active = (option.tag?.toLowerCase() ?? null) === (tag?.toLowerCase() ?? null)
              return (
                <button
                  key={option.tag ?? '(all)'}
                  type="button"
                  aria-pressed={active}
                  onClick={() => {
                    setTag(option.tag)
                    setPage(0)
                  }}
                  className={`rounded-full px-3 py-1.5 text-sm font-semibold ring-1 transition ring-inset focus-visible:outline-2 focus-visible:outline-ink ${
                    active ? 'bg-cue-wash text-cue-ink ring-cue' : 'bg-ground text-graphite ring-line'
                  }`}
                >
                  {option.tag ?? 'All'}
                  {option.tag && <span className="ml-1 text-xs font-normal text-subtle">{option.venues}</span>}
                </button>
              )
            })}
          </div>
        )}
      </div>

      {venues.isPending ? (
        <Spinner label="Loading your locations" />
      ) : venues.isError ? (
        <ErrorAlert error={venues.error} onRetry={() => venues.refetch()} />
      ) : venues.data.items.length === 0 ? (
        <EmptyState icon={BookMarked}>
          {search || tag
            ? 'Nothing in your library matches.'
            : 'Your library is empty. Open any venue and choose “Save to my library” to keep it here.'}
        </EmptyState>
      ) : (
        <>
          <ul aria-label="Your locations" className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {venues.data.items.map((venue) => (
              <li key={venue.id}>
                <LibraryCard venue={venue} />
              </li>
            ))}
          </ul>
          <Pager data={venues.data} onChange={setPage} label="Library pages" />
        </>
      )}
    </div>
  )
}

function LibraryCard({ venue }: { venue: LibraryVenue }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.libraryList })
    queryClient.invalidateQueries({ queryKey: queryKeys.libraryTags })
  }
  const remove = useMutation({ mutationFn: () => api.library.remove(venue.id), onSuccess: refresh })
  const placed = venue.latitude != null && venue.longitude != null

  return (
    <article aria-labelledby={`library-${venue.id}`} className="board-card flex h-full flex-col overflow-hidden rounded-lg bg-paper">
      <div aria-hidden className="relative h-36 bg-ground">
        {placed ? (
          <MapSnapshot latitude={venue.latitude!} longitude={venue.longitude!} />
        ) : (
          <div className="flex h-full items-center justify-center">
            <PinIcon className="size-6 text-line" />
          </div>
        )}
        {venue.imageUrl && <VenuePicture src={venue.imageUrl} className="absolute inset-0 h-full w-full" />}
      </div>
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="space-y-1">
          <h2 id={`library-${venue.id}`} className="font-display text-2xl leading-tight">
            {venue.name}
          </h2>
          {venue.address && <p className="text-sm text-muted">{venue.address}</p>}
          {venue.distanceKm != null && <p className="text-sm font-semibold text-cue-ink">{distanceText(venue.distanceKm)} from you</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          <LocationBadges location={{ bookingFriction: venue.bookingFriction, fitScore: 0 }} />
          {venue.tags.map((tag) => (
            <span key={tag} className="inline-flex items-center gap-1 rounded-full bg-cue-wash px-2.5 py-0.5 text-xs font-semibold text-cue-deep">
              <Tag aria-hidden className="size-3" />
              {tag}
            </span>
          ))}
        </div>
        {editing ? (
          <EditLibraryVenue venue={venue} onDone={() => setEditing(false)} onSaved={refresh} />
        ) : (
          <>
            {venue.notes && <p className="text-[15px] whitespace-pre-line text-graphite">{venue.notes}</p>}
            {(venue.contactName || venue.contactPhone || venue.contactEmail) && (
              <p className="text-sm text-muted">
                Contact: {[venue.contactName, venue.contactPhone, venue.contactEmail].filter(Boolean).join(' · ')}
              </p>
            )}
            {confirming ? (
              <ConfirmDelete
                title={`Remove ${venue.name} from your library?`}
                confirmLabel="Remove"
                busy={remove.isPending}
                error={remove.error}
                onConfirm={() => remove.mutate()}
                onCancel={() => setConfirming(false)}
              >
                Venues already added to scenes from it stay where they are.
              </ConfirmDelete>
            ) : (
              <div className="mt-auto flex justify-end gap-2 pt-2">
                <Button variant="ghost" onClick={() => setConfirming(true)} aria-label={`Remove ${venue.name}`}>
                  Remove
                </Button>
                <Button variant="secondary" onClick={() => setEditing(true)} aria-label={`Tag and note ${venue.name}`}>
                  Tag and note
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </article>
  )
}

function EditLibraryVenue({ venue, onDone, onSaved }: { venue: LibraryVenue; onDone: () => void; onSaved: () => void }) {
  const { api } = useSession()
  const [name, setName] = useState(venue.name)
  const [tags, setTags] = useState(venue.tags)
  const [draft, setDraft] = useState('')
  const [notes, setNotes] = useState(venue.notes ?? '')
  const save = useMutation({
    mutationFn: () => {
      const pending = draft.trim()
      return api.library.update(venue.id, {
        name: name.trim(),
        tags: pending && !tags.includes(pending) ? [...tags, pending] : tags,
        notes: notes.trim() || null,
      })
    },
    onSuccess: () => {
      onSaved()
      onDone()
    },
  })

  function addTag() {
    const tag = draft.trim()
    if (tag && !tags.some((t) => t.toLowerCase() === tag.toLowerCase()) && tags.length < 10) setTags([...tags, tag])
    setDraft('')
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-3" noValidate>
      <ErrorAlert error={save.error} />
      <TextField label="Name" value={name} maxLength={200} onChange={(e) => setName(e.target.value)} />
      <div className="space-y-1.5">
        {tags.length > 0 && (
          <ul aria-label="Tags" className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <li key={tag} className="flex items-center gap-1 rounded-full bg-cue-wash py-1 pr-1 pl-3 text-sm font-semibold text-cue-deep">
                {tag}
                <button
                  type="button"
                  onClick={() => setTags(tags.filter((t) => t !== tag))}
                  aria-label={`Remove the tag ${tag}`}
                  className="rounded-full p-0.5 hover:bg-paper focus-visible:outline-2 focus-visible:outline-ink"
                >
                  <X aria-hidden className="size-3.5" />
                </button>
              </li>
            ))}
          </ul>
        )}
        <TextField
          label="Add a tag"
          value={draft}
          maxLength={40}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ',') {
              e.preventDefault()
              addTag()
            }
          }}
          hint="Press Enter to add it. Up to 10."
        />
      </div>
      <TextArea label="Your notes" rows={3} maxLength={4000} value={notes} onChange={(e) => setNotes(e.target.value)} hint="Only you see these." />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" busy={save.isPending} disabled={!name.trim()}>
          Save
        </Button>
      </div>
    </form>
  )
}
