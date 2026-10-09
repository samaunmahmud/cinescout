import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookMarked, ChevronDown } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import { Button, ErrorAlert, Spinner, TextField } from './ui'

/**
 * Adds a venue from the user's library to a scene, as it is: no search and no AI call. Folded until opened, so the
 * library is fetched only when wanted.
 */
export function LibraryPicker({ sceneId }: { sceneId: string }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const venues = useQuery({
    queryKey: queryKeys.libraryPage(search.trim(), null, 0),
    queryFn: () => api.library.list(search, null, 0),
    enabled: open,
  })
  const add = useMutation({
    mutationFn: (libraryVenueId: string) => api.library.addToScene(sceneId, libraryVenueId),
    onSuccess: (location) => {
      queryClient.setQueryData(queryKeys.location(location.id), location)
      queryClient.invalidateQueries({ queryKey: queryKeys.locationList(sceneId) })
      navigate(`/locations/${location.id}`, { replace: true })
    },
  })

  return (
    <section aria-labelledby="from-library" className="mx-auto max-w-3xl rounded-lg border-2 border-dashed border-ink/40 bg-paper p-5">
      <h2 id="from-library" className="font-display text-2xl leading-none">
        <button
          type="button"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
          className="flex w-full items-center gap-2 rounded text-left focus-visible:ring-4 focus-visible:ring-cue/40 focus-visible:outline-none"
        >
          <BookMarked aria-hidden className="size-5 text-cue-ink" />
          Add from your library
          <ChevronDown aria-hidden className={`ml-auto size-5 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      </h2>
      {open && (
        <div className="space-y-3 pt-4">
          <TextField label="Search your locations" type="search" value={search} onChange={(e) => setSearch(e.target.value)} />
          <ErrorAlert error={add.error} />
          {venues.isPending ? (
            <Spinner label="Loading your locations" />
          ) : venues.isError ? (
            <ErrorAlert error={venues.error} onRetry={() => venues.refetch()} />
          ) : venues.data.items.length === 0 ? (
            <p className="text-sm text-muted">
              {search.trim() ? 'Nothing in your library matches.' : 'Your library is empty.'}{' '}
              <Link to="/library" className="font-semibold text-cue-ink underline">
                Open your library
              </Link>
            </p>
          ) : (
            <ul aria-label="Venues in your library" className="divide-y divide-line-soft rounded-lg border border-line bg-white">
              {venues.data.items.map((venue) => (
                <li key={venue.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                  <div className="min-w-0">
                    <p className="font-semibold text-ink">{venue.name}</p>
                    <p className="truncate text-sm text-muted">{[venue.address, ...venue.tags].filter(Boolean).join(' · ')}</p>
                  </div>
                  <Button
                    variant="secondary"
                    busy={add.isPending && add.variables === venue.id}
                    disabled={add.isPending}
                    onClick={() => add.mutate(venue.id)}
                    aria-label={`Add ${venue.name} to this scene`}
                  >
                    Add
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
