import type { ReactNode } from 'react'
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
  return (
    <div role="alertdialog" aria-labelledby="confirm-delete-title" className="space-y-3 rounded-lg border border-red-900 bg-red-950/40 p-5">
      <h2 id="confirm-delete-title" className="font-semibold">
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
