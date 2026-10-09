import { X } from 'lucide-react'
import { useId, useState, type FormEvent, type KeyboardEvent } from 'react'
import { fieldErrors } from '../api/errors'
import type { ScoutFilters } from '../api/types'
import { roundCoordinates } from '../lib/geo'
import { baseText, parseBase } from '../lib/scoutFilters'
import type { MapPin } from './map/types'
import { VenueMap } from './map/VenueMap'
import { UseMyLocation } from './UseMyLocation'
import { Button, ErrorAlert, TextField } from './ui'

/**
 * The scouting filters as a form: a base point (an address, or a spot clicked on the map) and a radius, the most a
 * day may cost, kinds of place to leave out, and whether private property will do. `onSubmit` gets them whole.
 */
export function ScoutFiltersForm({
  initial,
  submitLabel,
  busy,
  error,
  onSubmit,
  onCancel,
}: {
  initial: ScoutFilters
  submitLabel: string
  busy: boolean
  error: unknown
  onSubmit: (filters: ScoutFilters) => void
  onCancel?: () => void
}) {
  const ids = useId()
  const [base, setBase] = useState(baseText(initial))
  const [radius, setRadius] = useState(initial.radiusKm?.toString() ?? '')
  const [budget, setBudget] = useState(initial.maxBudget?.toString() ?? '')
  const [types, setTypes] = useState<string[]>(initial.excludedTypes)
  const [typeDraft, setTypeDraft] = useState('')
  const [includePrivate, setIncludePrivate] = useState(initial.includePrivate !== false)
  const [picking, setPicking] = useState(false)
  const errors = fieldErrors(error)
  const parsedBase = parseBase(base)
  const radiusNumber = radius.trim() === '' ? null : Number(radius)
  const budgetNumber = budget.trim() === '' ? null : Number(budget)
  const radiusProblem =
    radiusNumber != null && (Number.isNaN(radiusNumber) || radiusNumber < 0.2 || radiusNumber > 100)
      ? 'Between 0.2 and 100 km.'
      : radiusNumber != null && !parsedBase.baseAddress && parsedBase.baseLatitude == null
        ? 'A radius needs a base point above.'
        : undefined
  const budgetProblem = budgetNumber != null && (!Number.isInteger(budgetNumber) || budgetNumber < 0) ? 'A whole amount, 0 or more.' : undefined
  const pin: MapPin | null =
    parsedBase.baseLatitude != null && parsedBase.baseLongitude != null
      ? { id: 'base', position: { latitude: parsedBase.baseLatitude, longitude: parsedBase.baseLongitude }, label: 'Base point', tone: 'good' }
      : null

  function addType() {
    const tag = typeDraft.trim()
    if (tag && !types.some((t) => t.toLowerCase() === tag.toLowerCase()) && types.length < 10) setTypes([...types, tag])
    setTypeDraft('')
  }

  function typeKey(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      addType()
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    const pending = typeDraft.trim()
    onSubmit({
      ...parsedBase,
      radiusKm: radiusNumber,
      maxBudget: budgetNumber,
      excludedTypes: pending && !types.includes(pending) ? [...types, pending] : types,
      includePrivate: includePrivate ? null : false,
    })
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorAlert error={error} />
      <div className="grid gap-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <TextField
          label="Base point"
          value={base}
          onChange={(e) => setBase(e.target.value)}
          hint="An address, or a spot picked on the map. Leave empty to search the whole location area."
          error={errors.baseAddress ?? errors.baseLatitude}
          maxLength={300}
        />
        <TextField
          label="Radius (km)"
          inputMode="decimal"
          value={radius}
          onChange={(e) => setRadius(e.target.value)}
          error={radiusProblem ?? errors.radiusKm}
        />
      </div>
      <div className="space-y-2">
        <div className="flex flex-wrap items-start gap-2">
          <UseMyLocation label="Search around where I am" onLocate={(here) => setBase(`${here.latitude}, ${here.longitude}`)} />
          <Button variant="ghost" aria-expanded={picking} onClick={() => setPicking(!picking)}>
            {picking ? 'Hide the map' : 'Pick the base point on a map'}
          </Button>
        </div>
        {picking && (
          <VenueMap
            pins={pin ? [pin] : []}
            label="Map: click to set the base point"
            onPick={(spot) => {
              const rounded = roundCoordinates(spot)
              setBase(`${rounded.latitude}, ${rounded.longitude}`)
            }}
            className="h-64"
          />
        )}
      </div>
      <TextField
        label="Most a shooting day may cost"
        inputMode="numeric"
        value={budget}
        onChange={(e) => setBudget(e.target.value)}
        hint="In the local currency. Venues whose page gives no price are kept."
        error={budgetProblem ?? errors.maxBudget}
      />
      <div className="space-y-1.5">
        <label htmlFor={`${ids}-type`} className="block font-script text-[13px] font-bold tracking-[0.08em] text-ink uppercase">
          Leave out these kinds of place
        </label>
        {types.length > 0 && (
          <ul aria-label="Kinds of place left out" className="flex flex-wrap gap-2">
            {types.map((type) => (
              <li key={type} className="flex items-center gap-1 rounded-full bg-stop-wash py-1 pr-1 pl-3 text-sm font-semibold text-stop-ink">
                {type}
                <button
                  type="button"
                  onClick={() => setTypes(types.filter((t) => t !== type))}
                  aria-label={`Stop leaving out ${type}`}
                  className="rounded-full p-0.5 hover:bg-paper focus-visible:outline-2 focus-visible:outline-ink"
                >
                  <X aria-hidden className="size-3.5" />
                </button>
              </li>
            ))}
          </ul>
        )}
        <div className="flex gap-2">
          <input
            id={`${ids}-type`}
            value={typeDraft}
            maxLength={40}
            onChange={(e) => setTypeDraft(e.target.value)}
            onKeyDown={typeKey}
            placeholder="e.g. church, nightclub"
            aria-describedby={`${ids}-type-hint`}
            className="min-w-0 flex-1 rounded-lg border border-line bg-paper px-3 py-2 text-[15px] text-ink placeholder:text-subtle focus:ring-4 focus:ring-cue/25 focus:outline-none"
          />
          <Button variant="secondary" onClick={addType} disabled={!typeDraft.trim() || types.length >= 10}>
            Add
          </Button>
        </div>
        <p id={`${ids}-type-hint`} className="text-xs text-muted">
          Up to 10. Press Enter to add each one.
        </p>
      </div>
      <label className="flex items-center gap-2 text-[15px] text-graphite">
        <input type="checkbox" checked={includePrivate} onChange={(e) => setIncludePrivate(e.target.checked)} className="size-4 accent-[var(--color-cue)]" />
        Include private property (homes, privately owned places)
      </label>
      <div className="flex flex-wrap justify-end gap-2">
        {onCancel && (
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button type="submit" busy={busy} disabled={!!radiusProblem || !!budgetProblem}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
