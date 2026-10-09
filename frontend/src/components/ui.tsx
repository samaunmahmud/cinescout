import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
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
      <svg aria-hidden viewBox="0 0 24 24" className={`animate-reel ${label ? 'size-6 text-cue-ink' : 'size-4'}`} fill="currentColor">
        <path
          fillRule="evenodd"
          d="M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22Zm0 3.2a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2Zm-5.2 5.2a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2Zm10.4 0a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2ZM12 14.6a2.6 2.6 0 1 1 0 5.2 2.6 2.6 0 0 1 0-5.2Zm0-3.7a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2Z"
        />
      </svg>
      {label && <span className="text-sm text-muted">{label}</span>}
    </span>
  )
}

const inputClass =
  'w-full rounded-[9px] border border-line bg-paper px-3 py-2 text-[15px] text-ink shadow-[var(--shadow-card)] placeholder:text-subtle transition hover:border-subtle focus:border-ink focus:outline-none focus:ring-4 focus:ring-ink/10 aria-invalid:border-stop-ink'

interface FieldProps {
  label: string
  error?: string
  hint?: string
}

function FieldShell({ id, label, error, hint, children }: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-sm font-medium text-graphite">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm font-semibold text-stop-ink">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="text-xs text-muted">
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

export function SelectField({ label, error, hint, className = '', children, ...props }: FieldProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId()
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <select
        id={id}
        className={`${inputClass} ${className}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      >
        {children}
      </select>
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
    <div role="alert" className="flex items-start justify-between gap-4 rounded-[10px] border border-stop/40 bg-stop-wash px-4 py-3 text-sm font-medium text-stop-ink">
      <span>{errorMessage(error)}</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className="shrink-0 font-semibold underline hover:text-ink">
          Try again
        </button>
      )}
    </div>
  )
}

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'cue' | 'green' | 'red'; children: ReactNode }) {
  const tones = {
    neutral: 'bg-tape text-graphite ring-line',
    cue: 'bg-cue-wash text-cue-deep ring-cue-soft',
    green: 'bg-go-wash text-go-ink ring-go-soft',
    red: 'bg-stop-wash text-stop-ink ring-stop/30',
  }
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${tones[tone]}`}>{children}</span>
  )
}
