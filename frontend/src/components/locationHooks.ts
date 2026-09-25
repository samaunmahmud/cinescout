import { useMutation, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../api/queryKeys'
import type { Location, UpdateLocationRequest } from '../api/types'
import { useSession } from '../auth/context'

/** Puts a changed location into both the scene's list and its own page, whichever are cached. */
export function useStoreLocation() {
  const queryClient = useQueryClient()
  return (updated: Location) => {
    queryClient.setQueryData(queryKeys.location(updated.id), updated)
    queryClient.setQueryData<Location[]>(queryKeys.locationList(updated.sceneId), (list) =>
      list?.map((l) => (l.id === updated.id ? updated : l)),
    )
  }
}

/** Saves the user's workflow fields (a full replacement: pass both). */
export function useUpdateLocation(location: Location) {
  const { api } = useSession()
  const store = useStoreLocation()
  return useMutation({
    mutationFn: (body: UpdateLocationRequest) => api.locations.update(location.id, body),
    onSuccess: store,
  })
}
