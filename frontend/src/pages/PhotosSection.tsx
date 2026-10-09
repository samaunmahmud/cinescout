import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Camera, ImagePlus, MapPin as PinIcon } from 'lucide-react'
import { useId, useState, type ChangeEvent } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { Location, Photo } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { useStoreLocation } from '../components/locationHooks'
import { useCanEdit } from '../components/projectRole'
import { Stamp } from '../components/stickers'
import { EmptyState, Section } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { formatCoordinates, roundCoordinates } from '../lib/geo'
import { MAX_PHOTOS, preparePhoto } from '../lib/photoPrep'

interface PinSuggestion {
  filename: string
  latitude: number
  longitude: number
}

/**
 * Photos from the recce: added by the crew (HEIC as JPEG, see `preparePhoto`), shown as a gallery, one picked
 * for the venue's polaroid. A photo that says where it was taken can place a venue that has no pin yet. With
 * `camera`, a "Take a photo" button opens the phone's camera straight away (recce mode).
 */
export function PhotosSection({ location, camera = false }: { location: Location; camera?: boolean }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const store = useStoreLocation()
  const canEdit = useCanEdit()
  const inputId = useId()
  const cameraId = useId()
  const photos = useQuery({ queryKey: queryKeys.photos(location.id), queryFn: () => api.photos.list(location.id) })
  const [progress, setProgress] = useState<string | null>(null)
  const [failures, setFailures] = useState<string[]>([])
  const [suggestion, setSuggestion] = useState<PinSuggestion | null>(null)
  const count = photos.data?.totalItems ?? 0

  const placePin = useMutation({
    mutationFn: (spot: PinSuggestion) => api.locations.updateCoordinates(location.id, roundCoordinates(spot)),
    onSuccess: (updated) => {
      store(updated)
      setSuggestion(null)
    },
  })

  async function upload(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []).slice(0, Math.max(0, MAX_PHOTOS - count))
    e.target.value = ''
    if (files.length === 0) return
    setFailures([])
    const problems: string[] = []
    let offer: PinSuggestion | null = null
    for (const [index, file] of files.entries()) {
      setProgress(`Adding photo ${index + 1} of ${files.length}…`)
      try {
        const prepared = await preparePhoto(file)
        const result = await api.photos.upload(location.id, prepared.blob, prepared.filename, prepared.gps)
        const { latitude, longitude } = result.photo
        if (result.suggestPin && !offer && latitude != null && longitude != null) offer = { filename: file.name, latitude, longitude }
      } catch (error) {
        problems.push(error instanceof Error ? `${file.name}: ${'detail' in error && error.detail ? error.detail : error.message}` : file.name)
      }
    }
    setProgress(null)
    setFailures(problems)
    if (offer && (location.latitude == null || location.longitude == null)) setSuggestion(offer)
    queryClient.invalidateQueries({ queryKey: queryKeys.photos(location.id) })
  }

  return (
    <Section
      titleId="photos-heading"
      title="Recce photos"
      eyebrow="From the crew"
      icon={Camera}
      description={count > 0 ? `${count} of ${MAX_PHOTOS}` : undefined}
      actions={
        canEdit &&
        count < MAX_PHOTOS && (
          <>
            <input
              id={inputId}
              type="file"
              multiple
              // JPEG and PNG only: asked for those, iOS converts its HEIC photos to JPEG itself.
              accept="image/jpeg,image/png"
              onChange={upload}
              disabled={progress !== null}
              className="peer sr-only"
            />
            {camera && (
              <>
                <input
                  id={cameraId}
                  type="file"
                  accept="image/jpeg,image/png"
                  capture="environment"
                  onChange={upload}
                  disabled={progress !== null}
                  className="peer/camera sr-only"
                />
                <label
                  htmlFor={cameraId}
                  className="btn-cue inline-flex cursor-pointer items-center gap-2 rounded-lg px-4 py-2 text-sm font-bold peer-focus-visible/camera:ring-4 peer-focus-visible/camera:ring-cue/40 peer-disabled/camera:cursor-wait peer-disabled/camera:opacity-60"
                >
                  <Camera aria-hidden className="size-4" />
                  Take a photo
                </label>
              </>
            )}
            <label
              htmlFor={inputId}
              className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-line bg-white px-4 py-2 text-sm font-bold text-ink shadow-[var(--shadow-card)] peer-focus-visible:ring-4 peer-focus-visible:ring-cue/40 peer-disabled:cursor-wait peer-disabled:opacity-60"
            >
              <ImagePlus aria-hidden className="size-4" />
              Add photos
            </label>
          </>
        )
      }
    >
      {progress && <Spinner label={progress} />}
      {failures.length > 0 && (
        <div role="alert" className="space-y-1 rounded-lg border-2 border-stop bg-stop-wash px-4 py-3 text-sm text-stop-ink">
          <p className="font-semibold">Some photos were not added:</p>
          <ul className="list-disc pl-5">
            {failures.map((failure) => (
              <li key={failure}>{failure}</li>
            ))}
          </ul>
        </div>
      )}
      {suggestion && (
        <div role="status" className="space-y-3 rounded-lg border-2 border-cue bg-cue-wash p-4">
          <p className="flex items-start gap-2 text-[15px] text-ink">
            <PinIcon aria-hidden className="mt-0.5 size-4 shrink-0 text-cue-ink" />
            {suggestion.filename} was taken at {formatCoordinates(roundCoordinates(suggestion))}. This venue has no pin yet: put it there?
          </p>
          <ErrorAlert error={placePin.error} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setSuggestion(null)}>
              Not now
            </Button>
            <Button variant="secondary" busy={placePin.isPending} onClick={() => placePin.mutate(suggestion)}>
              Place the pin
            </Button>
          </div>
        </div>
      )}
      {photos.isPending ? (
        <Spinner label="Loading photos" />
      ) : photos.isError ? (
        <ErrorAlert error={photos.error} onRetry={() => photos.refetch()} />
      ) : photos.data.items.length === 0 ? (
        <EmptyState icon={Camera}>
          No recce photos yet.{canEdit ? ' Add the ones you took on the visit: JPEG, PNG or HEIC, up to 10 MB each.' : ''}
        </EmptyState>
      ) : (
        <ul aria-label="Recce photos" className="grid grid-cols-2 gap-4 sm:grid-cols-3">
          {photos.data.items.map((photo, index) => (
            <li key={photo.id}>
              <PhotoCard photo={photo} number={index + 1} location={location} />
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}

function PhotoCard({ photo, number, location }: { photo: Photo; number: number; location: Location }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const store = useStoreLocation()
  const canEdit = useCanEdit()
  const [confirming, setConfirming] = useState(false)
  const label = `Photo ${number}${photo.uploadedBy ? ` by ${photo.uploadedBy}` : ''}`
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.photos(location.id) })
  const cover = useMutation({
    mutationFn: (photoId: string | null) => api.photos.setCover(location.id, photoId),
    onSuccess: (updated) => {
      store(updated)
      refresh()
    },
  })
  const remove = useMutation({
    mutationFn: () => api.photos.remove(photo.id),
    onSuccess: () => {
      if (photo.cover) queryClient.invalidateQueries({ queryKey: queryKeys.location(location.id) })
      refresh()
    },
  })

  return (
    <figure className="space-y-2">
      <a href={photo.url} target="_blank" rel="noreferrer" className="polaroid relative block focus-visible:ring-4 focus-visible:ring-cue/40 focus-visible:outline-none">
        <img src={photo.thumbUrl} alt={label} loading="lazy" className="aspect-[4/3] w-full bg-ground object-cover" />
        {photo.cover && (
          <Stamp announce tone="go" className="absolute top-2 right-2 text-xs">
            On the polaroid
          </Stamp>
        )}
      </a>
      <figcaption className="text-xs text-muted">{label}</figcaption>
      {canEdit &&
        (confirming ? (
          <ConfirmDelete
            title="Delete this photo?"
            confirmLabel="Delete photo"
            busy={remove.isPending}
            error={remove.error}
            onConfirm={() => remove.mutate()}
            onCancel={() => setConfirming(false)}
          >
            It is gone for everyone on the crew.
          </ConfirmDelete>
        ) : (
          <div className="flex flex-wrap gap-1">
            <Button
              variant="ghost"
              className="!px-2 !py-1 text-xs"
              busy={cover.isPending}
              onClick={() => cover.mutate(photo.cover ? null : photo.id)}
              aria-label={photo.cover ? `Stop showing ${label} on the polaroid` : `Show ${label} on the polaroid`}
            >
              {photo.cover ? 'Use the web picture' : 'Use on the polaroid'}
            </Button>
            <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setConfirming(true)} aria-label={`Delete ${label}`}>
              Delete
            </Button>
          </div>
        ))}
      <ErrorAlert error={cover.error} />
    </figure>
  )
}
