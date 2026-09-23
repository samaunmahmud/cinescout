import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import type { SceneRequest } from '../api/types'
import { Button, ErrorAlert, TextArea, TextField } from '../components/ui'

export interface SceneFormValues {
  sceneNumber: string
  title: string
  sourceText: string
  shootDateStart: string
  shootDateEnd: string
}

const emptyScene: SceneFormValues = { sceneNumber: '', title: '', sourceText: '', shootDateStart: '', shootDateEnd: '' }

export function SceneForm({
  initial = emptyScene,
  submitLabel,
  busy,
  error,
  warning,
  onSubmit,
  onCancel,
}: {
  initial?: SceneFormValues
  submitLabel: string
  busy: boolean
  error: unknown
  /** Shown above the buttons when the edit has a side effect worth knowing about; gets the current values. */
  warning?: (values: SceneFormValues) => string | null
  onSubmit: (request: SceneRequest) => void
  onCancel: () => void
}) {
  const [values, setValues] = useState(initial)
  const set = (field: keyof SceneFormValues) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  // Mirrors the server's checks so the obvious mistakes are caught before a round trip.
  const windowBackwards = values.shootDateStart !== '' && values.shootDateEnd !== '' && values.shootDateEnd < values.shootDateStart
  const errors: Record<string, string> = {
    ...fieldErrors(error),
    ...(windowBackwards ? { shootDateEnd: 'The last shoot day cannot be before the first.' } : {}),
  }
  const number = values.sceneNumber.trim()
  const numberValid = number === '' || /^[1-9]\d{0,8}$/.test(number)
  const canSubmit = values.title.trim() !== '' && values.sourceText.trim() !== '' && numberValid && !windowBackwards
  const note = warning?.(values)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    onSubmit({
      sceneNumber: number === '' ? null : Number(number),
      title: values.title.trim(),
      sourceText: values.sourceText,
      shootDateStart: values.shootDateStart || null,
      shootDateEnd: values.shootDateEnd || null,
    })
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorAlert error={error} />
      <div className="grid gap-4 sm:grid-cols-[8rem_1fr]">
        <TextField
          label="Scene number"
          inputMode="numeric"
          value={values.sceneNumber}
          onChange={set('sceneNumber')}
          error={numberValid ? errors.sceneNumber : 'A whole number above zero.'}
        />
        <TextField label="Title" required maxLength={200} value={values.title} onChange={set('title')} error={errors.title} placeholder="INT. DINER - NIGHT" />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First shoot day" type="date" value={values.shootDateStart} onChange={set('shootDateStart')} error={errors.shootDateStart} />
        <TextField label="Last shoot day" type="date" value={values.shootDateEnd} onChange={set('shootDateEnd')} error={errors.shootDateEnd} />
      </div>
      <TextArea
        label="Script"
        required
        maxLength={20000}
        rows={14}
        className="font-mono"
        hint="Paste the scene as written. The AI reads it to work out what kind of location you need."
        value={values.sourceText}
        onChange={set('sourceText')}
        error={errors.sourceText}
      />
      {note && (
        <p role="note" className="rounded-md border border-amber-900 bg-amber-950/40 px-4 py-3 text-sm text-amber-200">
          {note}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" busy={busy} disabled={!canSubmit}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
