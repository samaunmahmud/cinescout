import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { Location, Page, UpdateLocationRequest } from '../api/types'
import { useSession } from '../auth/context'

/** Puts a changed location into the scene's list (every cached page of it) and its own page, whichever are cached. */
export function useStoreLocation() {
  const queryClient = useQueryClient()
  return useCallback(
    (updated: Location) => {
      queryClient.setQueryData(queryKeys.location(updated.id), updated)
      queryClient.setQueriesData<Page<Location>>({ queryKey: queryKeys.locationList(updated.sceneId) }, (page) =>
        page && { ...page, items: page.items.map((l) => (l.id === updated.id ? updated : l)) },
      )
    },
    [queryClient],
  )
}

/** Saves the user's workflow fields (a full replacement: pass both). `onSaved` runs after the caches are updated. */
export function useUpdateLocation(location: Pick<Location, 'id'>, onSaved?: (updated: Location) => void) {
  const { api } = useSession()
  const store = useStoreLocation()
  return useMutation({
    mutationFn: (body: UpdateLocationRequest) => api.locations.update(location.id, body),
    onSuccess: (updated) => {
      store(updated)
      onSaved?.(updated)
    },
  })
}
