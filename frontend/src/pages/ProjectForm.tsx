import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import { useSession } from '../auth/context'
import { UseMyLocation } from '../components/UseMyLocation'
import { Button, ErrorAlert, TextArea, TextField } from '../components/ui'

export interface ProjectFormValues {
  title: string
  description: string
  locationArea: string
}

export function ProjectForm({
  initial = { title: '', description: '', locationArea: '' },
  submitLabel,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  initial?: ProjectFormValues
  submitLabel: string
  busy: boolean
  error: unknown
  onSubmit: (values: ProjectFormValues) => void
  onCancel: () => void
}) {
  const { api } = useSession()
  const [values, setValues] = useState(initial)
  const errors = fieldErrors(error)
  const set = (field: keyof ProjectFormValues) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  function submit(e: FormEvent) {
    e.preventDefault()
    onSubmit(values)
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorAlert error={error} />
      <TextField label="Title" required maxLength={200} value={values.title} onChange={set('title')} error={errors.title} autoFocus />
      <TextField
        label="Location area"
        maxLength={200}
        placeholder="Brooklyn, New York"
        hint="Where this production's scenes are scouted. You can set it later."
        value={values.locationArea}
        onChange={set('locationArea')}
        error={errors.locationArea}
      />
      <UseMyLocation
        label="Use where I am now"
        onLocate={async ({ latitude, longitude }) => {
          const place = await api.placeHere(latitude, longitude)
          setValues((current) => ({ ...current, locationArea: place.name.slice(0, 200) }))
        }}
      />
      <TextArea label="Description" maxLength={2000} value={values.description} onChange={set('description')} error={errors.description} />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" busy={busy} disabled={!values.title.trim()}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
