import { useMutation } from '@tanstack/react-query'
import { useId } from 'react'
import type { BookingFriction, Location } from '../api/types'
import { useSession } from '../auth/context'
import { frictionLabel } from '../lib/fit'
import { useStoreLocation } from './locationHooks'
import { useCanEdit } from './projectRole'

const routes: BookingFriction[] = ['PUBLIC', 'COMMERCIAL', 'PRIVATE']

/** Who has to say yes to filming at the venue: read for a viewer, chosen by an editor (over what scouting assessed). */
export function BookingRoute({ location }: { location: Location }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const store = useStoreLocation()
  const id = useId()
  const save = useMutation({
    mutationFn: (route: BookingFriction | null) => api.locations.setBookingRoute(location.id, route),
    onSuccess: store,
  })
  if (!canEdit) return <>{location.bookingFriction ? frictionLabel(location.bookingFriction) : 'Unknown'}</>
  return (
    <>
      <label htmlFor={id} className="sr-only">
        Who says yes to filming at {location.name}
      </label>
      <select
        id={id}
        value={location.bookingFriction ?? ''}
        disabled={save.isPending}
        onChange={(e) => save.mutate((e.target.value || null) as BookingFriction | null)}
        className="-ml-1 w-full rounded-md border border-line bg-white px-1 py-0.5 font-semibold text-ink focus:border-ink focus:ring-2 focus:ring-cue/25 focus:outline-none disabled:opacity-50"
      >
        <option value="">Unknown</option>
        {routes.map((route) => (
          <option key={route} value={route}>
            {frictionLabel(route)}
          </option>
        ))}
      </select>
      {save.isError && (
        <span role="alert" className="block text-xs text-stop-ink">
          Not saved. Try again.
        </span>
      )}
    </>
  )
}
