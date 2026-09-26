import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'
import { errorMessage } from '../api/errors'
import { buttonBase, variants, type Variant } from './buttonStyles'

export function Button({
  variant = 'primary',
  busy = false,
  className = '',
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; busy?: boolean }) {
  return (
    <button
      type="button"
      {...props}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={`${buttonBase} ${variants[variant]} ${className}`}
    >
      {busy && <Spinner />}
      {children}
    </button>
  )
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span role={label ? 'status' : undefined} className="inline-flex items-center gap-2">
      <span className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />
      {label && <span className="text-sm text-stone-400">{label}</span>}
    </span>
  )
}

const inputClass =
  'w-full rounded-lg border border-white/10 bg-ink/70 px-3 py-2 text-sm text-stone-100 shadow-inner shadow-black/40 placeholder:text-stone-500 transition focus:border-amber-400/80 focus:outline-none focus:ring-2 focus:ring-amber-400/30 aria-invalid:border-red-500'

interface FieldProps {
  label: string
  error?: string
  hint?: string
}

function FieldShell({ id, label, error, hint, children }: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-xs font-semibold tracking-wider text-stone-400 uppercase">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-red-400">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="text-xs text-stone-500">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

export function TextField({ label, error, hint, className = '', ...props }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <input
        id={id}
        className={`${inputClass} ${className}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      />
    </FieldShell>
  )
}

export function TextArea({ label, error, hint, className = '', ...props }: FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <textarea
        id={id}
        rows={4}
        className={`${inputClass} ${className}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      />
    </FieldShell>
  )
}

export function ErrorAlert({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null
  return (
    <div role="alert" className="flex items-start justify-between gap-4 rounded-lg border border-red-900/80 bg-red-950/50 px-4 py-3 text-sm text-red-200">
      <span>{errorMessage(error)}</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className="shrink-0 font-semibold underline hover:text-white">
          Try again
        </button>
      )}
    </div>
  )
}

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'amber' | 'green' | 'red'; children: ReactNode }) {
  const tones = {
    neutral: 'bg-white/5 text-stone-300 ring-white/10',
    amber: 'bg-amber-500/10 text-amber-300 ring-amber-400/25',
    green: 'bg-emerald-500/10 text-emerald-300 ring-emerald-400/25',
    red: 'bg-red-500/10 text-red-300 ring-red-400/25',
  }
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}>{children}</span>
  )
}
