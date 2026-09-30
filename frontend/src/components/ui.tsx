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

/** A film reel, turning. With a label it is announced as a status; without one it is decoration in a busy button. */
export function Spinner({ label }: { label?: string }) {
  return (
    <span role={label ? 'status' : undefined} className="inline-flex items-center gap-2.5">
      <svg aria-hidden viewBox="0 0 24 24" className={`animate-reel ${label ? 'size-6 text-amber-300' : 'size-4'}`} fill="currentColor">
        <path
          fillRule="evenodd"
          d="M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22Zm0 3.2a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2Zm-5.2 5.2a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2Zm10.4 0a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2ZM12 14.6a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2Zm0-3.7a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2Z"
        />
      </svg>
      {label && <span className="font-serif text-sm text-stone-400 italic">{label}</span>}
    </span>
  )
}

const inputClass =
  'w-full rounded-md border border-white/10 bg-black/50 px-3 py-2 text-sm text-stone-100 shadow-inner shadow-black/60 placeholder:text-stone-600 transition hover:border-white/20 focus:border-amber-300/80 focus:outline-none focus:ring-2 focus:ring-amber-300/25 aria-invalid:border-velvet-400'

interface FieldProps {
  label: string
  error?: string
  hint?: string
}

function FieldShell({ id, label, error, hint, children }: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-[11px] font-semibold tracking-[0.18em] text-stone-400 uppercase">
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
    <div role="alert" className="flex items-start justify-between gap-4 rounded-lg border border-velvet-600/70 bg-velvet-900/60 px-4 py-3 text-sm text-red-100">
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
