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
    <div role="alertdialog" aria-labelledby={titleId} className="space-y-3 rounded-xl border border-stop bg-stop-wash p-5">
      <h2 id={titleId} className="font-display text-2xl leading-none text-stop-ink">
        {title}
      </h2>
      <p className="text-sm text-graphite">{children}</p>
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
