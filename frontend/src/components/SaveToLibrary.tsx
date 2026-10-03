import { useMutation, useQueryClient } from '@tanstack/react-query'
import { BookmarkPlus } from 'lucide-react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import { useSession } from '../auth/context'
import { Button, ErrorAlert } from './ui'

/** Keeps the venue in the user's own library; saving it twice finds the first copy. Anyone on the crew may. */
export function SaveToLibrary({ locationId }: { locationId: string }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const save = useMutation({
    mutationFn: () => api.library.save(locationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.libraryList })
      queryClient.invalidateQueries({ queryKey: queryKeys.libraryTags })
    },
  })
  if (save.isSuccess) {
    return (
      <span role="status" className="text-sm font-semibold text-go-ink">
        In <Link to="/library" className="underline decoration-2 underline-offset-2 hover:text-ink">your library</Link>
      </span>
    )
  }
  return (
    <>
      <Button variant="ghost" busy={save.isPending} onClick={() => save.mutate()}>
        {!save.isPending && <BookmarkPlus aria-hidden className="size-4" />}
        Save to my library
      </Button>
      <ErrorAlert error={save.error} />
    </>
  )
}
