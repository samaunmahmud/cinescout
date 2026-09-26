import { useId, type ReactNode } from 'react'
import { Button, ErrorAlert } from './ui'

/** An inline "are you sure?" panel for an irreversible delete. */
export function ConfirmDelete({
  title,
  children,
  confirmLabel,
  busy,
  error,
  onConfirm,
  onCancel,
}: {
  title: string
  children: ReactNode
  confirmLabel: string
  busy: boolean
  error: unknown
  onConfirm: () => void
  onCancel: () => void
}) {
  // Unique per panel: a page can show more than one (a list of locations, say).
  const titleId = useId()
  return (
    <div role="alertdialog" aria-labelledby={titleId} className="space-y-3 rounded-xl border border-red-900/70 bg-red-950/40 p-5">
      <h2 id={titleId} className="font-display text-2xl leading-none text-red-100">
        {title}
      </h2>
      <p className="text-sm text-stone-300">{children}</p>
      <ErrorAlert error={error} />
      <div className="flex gap-2">
        <Button variant="danger" busy={busy} onClick={onConfirm}>
          {confirmLabel}
        </Button>
        <Button variant="ghost" disabled={busy} onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  )
}
