import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { fieldErrors, isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { CreateLocationRequest } from '../api/types'
import { useSession } from '../auth/context'
import { Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { sceneLabel } from '../lib/format'
import { parseCoordinates } from '../lib/geo'
import { blankToNull } from '../lib/text'
import { safeHttpUrl } from '../lib/url'
import { NotFoundPage } from './NotFoundPage'
import { usePageTitle } from '../lib/usePageTitle'

/** Adds a venue the user found themselves to a scene. */
export function NewLocationPage() {
  const { sceneId = '' } = useParams()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const scene = useQuery({ queryKey: queryKeys.scene(sceneId), queryFn: () => api.scenes.get(sceneId) })
  usePageTitle('Add a venue')
  const [values, setValues] = useState({ name: '', address: '', sourceUrl: '', coordinates: '', notes: '' })
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  const create = useMutation({
    mutationFn: (body: CreateLocationRequest) => api.locations.create(sceneId, body),
    onSuccess: (location) => {
      queryClient.setQueryData(queryKeys.location(location.id), location)
      queryClient.invalidateQueries({ queryKey: queryKeys.locationList(sceneId) })
      navigate(`/locations/${location.id}`, { replace: true })
    },
  })

  if (scene.isPending) return <Spinner label="Loading scene" />
  if (scene.isError) {
    if (isNotFound(scene.error)) return <NotFoundPage />
    return <ErrorAlert error={scene.error} onRetry={() => scene.refetch()} />
  }

  // Mirrors the server's checks so the obvious mistakes are caught before a round trip.
  const coordinates = parseCoordinates(values.coordinates)
  const url = values.sourceUrl.trim()
  const urlValid = url === '' || safeHttpUrl(url) !== null
  const server = fieldErrors(create.error)
  const coordinatesError = !coordinates.ok
    ? coordinates.error
    : (server.coordinates ?? server.latitude ?? server.longitude)
  const canSubmit = values.name.trim() !== '' && urlValid && coordinates.ok

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit || !coordinates.ok) return
    create.mutate({
      name: values.name.trim(),
      address: blankToNull(values.address),
      latitude: coordinates.value?.latitude ?? null,
      longitude: coordinates.value?.longitude ?? null,
      sourceUrl: blankToNull(url),
      notes: blankToNull(values.notes),
    })
  }

  const scenePath = `/scenes/${sceneId}`
  return (
    <div className="space-y-6">
      <Link to={scenePath} className="inline-flex items-center gap-1 text-sm text-stone-400 hover:text-stone-200">
        ← {sceneLabel(scene.data)}
      </Link>
      <section aria-labelledby="new-location" className="mx-auto max-w-3xl rounded-xl border border-white/[0.07] bg-gradient-to-b from-frame/90 to-reel/90 p-6 shadow-xl shadow-black/40 sm:p-8">
        <h1 id="new-location" className="mb-2 gold-leaf font-display text-5xl leading-none">
          Add a venue
        </h1>
        <p className="mb-4 text-sm text-stone-400">A place you found yourself. It is saved without an AI assessment.</p>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <ErrorAlert error={create.error} />
          <TextField label="Name" required maxLength={200} value={values.name} onChange={set('name')} error={server.name} />
          <TextField label="Address" maxLength={500} value={values.address} onChange={set('address')} error={server.address} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Website"
              type="url"
              maxLength={2048}
              placeholder="https://"
              value={values.sourceUrl}
              onChange={set('sourceUrl')}
              error={urlValid ? server.sourceUrl : 'Enter a full web address starting with http:// or https://.'}
            />
            <TextField
              label="Coordinates"
              placeholder="40.6745, -73.9633"
              hint="Optional. Latitude, longitude, as copied from a map. Without them, the address is looked up."
              value={values.coordinates}
              onChange={set('coordinates')}
              error={coordinatesError || undefined}
            />
          </div>
          <TextArea label="Notes" maxLength={4000} value={values.notes} onChange={set('notes')} error={server.notes} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => navigate(scenePath)} disabled={create.isPending}>
              Cancel
            </Button>
            <Button type="submit" busy={create.isPending} disabled={!canSubmit}>
              Add venue
            </Button>
          </div>
        </form>
      </section>
    </div>
  )
}
