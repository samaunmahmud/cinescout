import { useMutation } from '@tanstack/react-query'
import { ClipboardCheck } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import type { Location } from '../api/types'
import { useSession } from '../auth/context'
import { useStoreLocation } from '../components/locationHooks'
import { useCanEdit } from '../components/projectRole'
import { Section } from '../components/surfaces'
import { Button, ErrorAlert } from '../components/ui'
import { formatDate } from '../lib/format'
import { changedAnswers, recceAnswerText, recceGroups, type RecceQuestion } from '../lib/recce'

type Draft = Record<string, string | number | boolean | null>

const inputClass =
  'block w-full rounded-lg border border-line bg-paper px-3 py-2 text-[15px] text-ink focus:ring-4 focus:ring-cue/25 focus:outline-none'

/**
 * The tech recce checklist, made to be filled in on a phone at the venue: every question optional, each answer
 * saying who gave it and when. Only the answers changed are sent.
 */
export function RecceSection({ location }: { location: Location }) {
  const { api } = useSession()
  const store = useStoreLocation()
  const canEdit = useCanEdit()
  const initial = () => Object.fromEntries(Object.entries(location.recce).map(([name, entry]) => [name, entry.value])) as Draft
  const [draft, setDraft] = useState<Draft>(initial)
  const [saved, setSaved] = useState(false)
  const changes = changedAnswers(location.recce, draft)
  const dirty = Object.keys(changes).length > 0
  const save = useMutation({
    mutationFn: () => api.locations.answerRecce(location.id, changes),
    onSuccess: (updated) => {
      store(updated)
      setSaved(true)
    },
  })
  const errors = fieldErrors(save.error)

  function set(name: string, value: string | number | boolean | null) {
    setSaved(false)
    setDraft((now) => ({ ...now, [name]: value }))
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (dirty) save.mutate()
  }

  const answered = Object.keys(location.recce).length
  return (
    <Section
      titleId="recce-heading"
      title="Tech recce"
      eyebrow="On the visit"
      icon={ClipboardCheck}
      description={answered > 0 ? `${answered} of ${recceGroups.reduce((n, g) => n + g.questions.length, 0)} answered` : 'Nothing checked yet. Every question is optional.'}
    >
      <form onSubmit={submit} className="space-y-6" noValidate>
        <ErrorAlert error={Object.keys(errors).length > 0 ? null : save.error} />
        {recceGroups.map((group) => (
          <fieldset key={group.title} className="board-card space-y-4 rounded-lg bg-paper p-4">
            <legend className="sr-only">{group.title}</legend>
            <h3 aria-hidden className="font-script text-xs font-bold tracking-[0.08em] text-muted uppercase">
              {group.title}
            </h3>
            {group.questions.map((question) => (
              <Question
                key={question.name}
                question={question}
                value={draft[question.name] ?? null}
                entry={location.recce[question.name]}
                error={errors[question.name]}
                readOnly={!canEdit}
                onChange={(value) => set(question.name, value)}
              />
            ))}
          </fieldset>
        ))}
        {canEdit && (
          <div className="sticky bottom-20 flex items-center justify-end sm:bottom-3 gap-3 rounded-lg border border-line bg-paper px-4 py-3 shadow-[var(--shadow-card)]">
            {saved && !dirty && (
              <span role="status" className="text-sm font-semibold text-go-ink">
                Saved.
              </span>
            )}
            {dirty && <span className="text-sm text-muted">{Object.keys(changes).length} unsaved</span>}
            <Button type="submit" busy={save.isPending} disabled={!dirty}>
              Save the recce
            </Button>
          </div>
        )}
      </form>
    </Section>
  )
}

function Question({
  question,
  value,
  entry,
  error,
  readOnly,
  onChange,
}: {
  question: RecceQuestion
  value: string | number | boolean | null
  entry: Location['recce'][string] | undefined
  error: string | undefined
  readOnly: boolean
  onChange: (value: string | number | boolean | null) => void
}) {
  const id = useId()
  const credit = entry && (
    <p className="text-xs text-subtle">
      {entry.byName ?? 'A former member'} · {formatDate(entry.at.slice(0, 10))}
    </p>
  )
  if (readOnly) {
    return (
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="text-sm font-semibold text-graphite">{question.label}</span>
        <span className="text-right">
          <span className="block text-[15px] text-ink">{recceAnswerText(question, entry?.value) ?? '—'}</span>
          {credit}
        </span>
      </div>
    )
  }
  const describedBy = [error ? `${id}-error` : null, question.hint ? `${id}-hint` : null].filter(Boolean).join(' ') || undefined
  const label = (
    <span className="block font-script text-[13px] font-bold tracking-[0.08em] text-ink uppercase">{question.label}</span>
  )
  let control
  if (question.kind === 'yesNo' || question.kind === 'scale') {
    const options: { value: boolean | number | null; text: string }[] =
      question.kind === 'yesNo'
        ? [
            { value: true, text: 'Yes' },
            { value: false, text: 'No' },
            { value: null, text: 'Not checked' },
          ]
        : [
            ...Array.from({ length: (question.max ?? 5) - (question.min ?? 1) + 1 }, (_, i) => ({ value: (question.min ?? 1) + i, text: String((question.min ?? 1) + i) })),
            { value: null, text: 'Not checked' },
          ]
    return (
      <fieldset aria-describedby={describedBy} className="space-y-1.5">
        <legend>{label}</legend>
        <div className="flex flex-wrap gap-2">
          {options.map((option) => {
            const picked = value === option.value
            return (
              <label
                key={option.text}
                className={`flex min-w-11 cursor-pointer items-center justify-center rounded-lg border-2 px-3 py-2 text-sm font-bold has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-cue/40 ${
                  picked ? 'border-ink bg-cue text-night' : 'border-line bg-paper text-graphite hover:border-ink'
                }`}
              >
                <input type="radio" name={id} checked={picked} onChange={() => onChange(option.value)} className="sr-only" />
                {option.text}
              </label>
            )
          })}
        </div>
        {question.hint && <p id={`${id}-hint`} className="text-xs text-muted">{question.hint}</p>}
        {error && <p id={`${id}-error`} className="text-sm text-stop-ink">{error}</p>}
        {credit}
      </fieldset>
    )
  }
  if (question.kind === 'choice') {
    control = (
      <select id={id} value={(value as string | null) ?? ''} onChange={(e) => onChange(e.target.value || null)} aria-describedby={describedBy} className={inputClass}>
        <option value="">Not checked</option>
        {question.choices!.map((choice) => (
          <option key={choice.value} value={choice.value}>
            {choice.label}
          </option>
        ))}
      </select>
    )
  } else if (question.kind === 'text') {
    control = (
      <textarea
        id={id}
        rows={question.name === 'notes' ? 4 : 2}
        maxLength={question.maxLength}
        value={(value as string | null) ?? ''}
        onChange={(e) => onChange(e.target.value)}
        aria-describedby={describedBy}
        aria-invalid={error ? true : undefined}
        className={inputClass}
      />
    )
  } else {
    control = (
      <input
        id={id}
        type="number"
        inputMode={question.kind === 'decimal' ? 'decimal' : 'numeric'}
        min={question.min}
        max={question.max}
        step={question.kind === 'decimal' ? 0.1 : 1}
        value={value === null || value === undefined ? '' : String(value)}
        onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
        aria-describedby={describedBy}
        aria-invalid={error ? true : undefined}
        className={`${inputClass} max-w-40`}
      />
    )
  }
  return (
    <div className="space-y-1.5">
      <label htmlFor={id}>{label}</label>
      {control}
      {question.hint && <p id={`${id}-hint`} className="text-xs text-muted">{question.hint}</p>}
      {error && <p id={`${id}-error`} className="text-sm text-stop-ink">{error}</p>}
      {credit}
    </div>
  )
}
