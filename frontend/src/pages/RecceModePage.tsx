import { useMutation, useQuery } from '@tanstack/react-query'
import { Check, ChevronLeft, Crosshair, LocateFixed, Smartphone } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Location } from '../api/types'
import { useSession } from '../auth/context'
import { useStoreLocation } from '../components/locationHooks'
import { ProjectRoleProvider } from '../components/ProjectRoleProvider'
import { useProjectRole } from '../components/projectRole'
import { Eyebrow, Section } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { currentPosition, PositionError } from '../lib/currentPosition'
import { distanceKm, distanceText, formatCoordinates, type Coordinates } from '../lib/geo'
import { usePageTitle } from '../lib/usePageTitle'
import { NotFoundPage } from './NotFoundPage'
import { PhotosSection } from './PhotosSection'
import { RecceSection } from './RecceSection'

/** A pin this close to where you stand is the same spot: saving it again would only discard the logistics. */
const SAME_SPOT_KM = 0.05
/** Further than this, moving the pin is worth a second thought. */
const FAR_KM = 0.3

/**
 * Recce mode: the venue page cut down to what a scout does standing there, on a phone. Pin the venue where you are
 * in one tap, take photos with the camera, fill in the tech recce.
 */
export function RecceModePage() {
  const { locationId = '' } = useParams()
  const { api } = useSession()
  const location = useQuery({ queryKey: queryKeys.location(locationId), queryFn: () => api.locations.get(locationId) })
  usePageTitle(location.data ? `Recce: ${location.data.name}` : 'Recce')

  if (location.isPending) return <Spinner label="Loading the venue" />
  if (location.isError) {
    if (isNotFound(location.error)) return <NotFoundPage />
    return <ErrorAlert error={location.error} onRetry={() => location.refetch()} />
  }
  return <RecceMode location={location.data} />
}

function RecceMode({ location }: { location: Location }) {
  const { api } = useSession()
  const scene = useQuery({ queryKey: queryKeys.scene(location.sceneId), queryFn: () => api.scenes.get(location.sceneId) })
  const role = useProjectRole(scene.data?.projectId)
  const venuePath = `/locations/${location.id}`

  return (
    <ProjectRoleProvider role={role}>
      <div className="mx-auto max-w-2xl space-y-8">
        <Link to={venuePath} className="inline-flex items-center gap-1 font-script text-sm font-bold tracking-[0.06em] text-muted uppercase hover:text-ink">
          <ChevronLeft aria-hidden className="size-4" />
          Back to the venue
        </Link>
        <header className="space-y-2">
          <Eyebrow icon={Smartphone}>Recce mode</Eyebrow>
          <h1 className="font-display text-4xl leading-none sm:text-5xl">{location.name}</h1>
          <p className="text-muted">Standing there? Pin it, shoot it, check it. Everything saves as you go.</p>
        </header>
        {role === 'VIEWER' ? (
          <p role="note" className="rounded-lg border border-line bg-white p-4 text-[15px]">
            You can look at this venue but not record a recce: ask the project’s owner to make you an editor.
          </p>
        ) : (
          <>
            <PinHere location={location} />
            <PhotosSection location={location} camera />
            <RecceSection location={location} />
            <div className="flex justify-end">
              <Link to={venuePath} className="btn-cue inline-flex items-center gap-2 rounded-lg px-5 py-3 font-bold">
                <Check aria-hidden className="size-5" />
                Done
              </Link>
            </div>
          </>
        )}
      </div>
    </ProjectRoleProvider>
  )
}

/** Step one: the venue's pin set to where the scout stands, asking first when that would move it far. */
function PinHere({ location }: { location: Location }) {
  const { api } = useSession()
  const store = useStoreLocation()
  const current = location.latitude != null && location.longitude != null ? { latitude: location.latitude, longitude: location.longitude } : null
  const [finding, setFinding] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const [pending, setPending] = useState<{ here: Coordinates; awayKm: number } | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: (here: Coordinates) => api.locations.updateCoordinates(location.id, here),
    onSuccess: (updated) => {
      store(updated)
      setPending(null)
      setDone(`Pinned at ${formatCoordinates({ latitude: updated.latitude!, longitude: updated.longitude! })}.`)
    },
  })

  async function pinHere() {
    setProblem(null)
    setDone(null)
    setFinding(true)
    try {
      const here = await currentPosition()
      const awayKm = current ? distanceKm(current, here) : null
      if (awayKm != null && awayKm < SAME_SPOT_KM) setDone('The pin is already where you are.')
      else if (awayKm != null && awayKm > FAR_KM) setPending({ here, awayKm })
      else save.mutate(here)
    } catch (error) {
      setProblem(error instanceof PositionError ? error.message : 'Your location could not be found just now.')
    } finally {
      setFinding(false)
    }
  }

  return (
    <Section titleId="pin-here-heading" title="Pin it here" eyebrow="Step one" icon={Crosshair} description={current ? 'The venue has a pin.' : 'The venue has no pin yet.'}>
      <div className="space-y-3">
        <Button className="w-full justify-center py-3 text-base sm:w-auto" busy={finding || save.isPending} onClick={pinHere}>
          {!(finding || save.isPending) && <LocateFixed aria-hidden className="size-5" />}
          {finding ? 'Finding you…' : 'I’m at the venue: pin it here'}
        </Button>
        {location.logistics && !pending && (
          <p className="text-sm text-muted">Moving the pin discards the logistics report, which was worked out for the old spot.</p>
        )}
        {pending && (
          <div role="alertdialog" aria-label="Move the pin?" className="space-y-3 rounded-lg border-2 border-cue bg-cue-wash p-4">
            <p className="text-[15px] text-ink">
              The pin is {distanceText(pending.awayKm)} from where you are. Move it here?
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setPending(null)}>
                Keep the old pin
              </Button>
              <Button variant="secondary" busy={save.isPending} onClick={() => save.mutate(pending.here)}>
                Move it here
              </Button>
            </div>
          </div>
        )}
        {problem && (
          <p role="alert" className="text-sm text-stop-ink">
            {problem}
          </p>
        )}
        <ErrorAlert error={save.error} />
        {done && (
          <p role="status" className="flex items-center gap-1.5 text-sm font-semibold text-go-ink">
            <Check aria-hidden className="size-4" />
            {done}
          </p>
        )}
      </div>
    </Section>
  )
}
