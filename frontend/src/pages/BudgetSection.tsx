import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Wallet } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { fieldErrors } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Budget, BudgetCategory, BudgetItem, BudgetItemRequest, BudgetStatus } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { useCanEdit } from '../components/projectRole'
import { EmptyState, Section } from '../components/surfaces'
import { Button, ErrorAlert, SelectField, Spinner, TextField } from '../components/ui'
import { budgetCategories, budgetStatusLabels, budgetStatuses, categoryLabels, formatMoney, quoteAmount } from '../lib/budget'

/**
 * The production's budget: what it may spend, every line (a venue's days, a permit, a deposit...) as an estimate,
 * committed or paid, and what is left. Confirmed venues with no line yet are offered with their quote filled in.
 */
export function BudgetSection({ projectId }: { projectId: string }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const canEdit = useCanEdit()
  const budget = useQuery({ queryKey: queryKeys.budget(projectId), queryFn: () => api.budget.get(projectId) })
  const store = (updated: Budget) => queryClient.setQueryData(queryKeys.budget(projectId), updated)
  const [adding, setAdding] = useState(false)

  if (budget.isPending) return <Spinner label="Loading the budget" />
  if (budget.isError) return <ErrorAlert error={budget.error} onRetry={() => budget.refetch()} />
  const data = budget.data
  const money = (amount: number) => formatMoney(amount, data.currency)

  return (
    <Section
      titleId="budget-heading"
      title="Budget"
      eyebrow="What it costs"
      icon={Wallet}
      description="Every line in one currency: an estimate until it is agreed, then committed, then paid."
      actions={
        canEdit &&
        !adding && (
          <Button variant="secondary" onClick={() => setAdding(true)}>
            <Plus aria-hidden className="size-4" />
            Add a line
          </Button>
        )
      }
    >
      <div className="space-y-6">
        <Summary projectId={projectId} budget={data} onSaved={store} />

        {adding && (
          <LineForm
            projectId={projectId}
            currency={data.currency}
            submitLabel="Add the line"
            onCancel={() => setAdding(false)}
            onSaved={(updated) => {
              store(updated)
              setAdding(false)
            }}
          />
        )}

        {canEdit && data.unbudgetedVenues.length > 0 && (
          <div className="space-y-2 rounded-lg border-2 border-cue bg-cue-wash p-4">
            <h3 className="font-script text-xs font-bold tracking-[0.1em] text-cue-ink uppercase">Confirmed venues not in the budget yet</h3>
            <ul aria-label="Confirmed venues not in the budget yet" className="divide-y divide-cue/30">
              {data.unbudgetedVenues.map((venue) => (
                <UnbudgetedVenueRow key={venue.locationId} projectId={projectId} currency={data.currency} venue={venue} onSaved={store} />
              ))}
            </ul>
          </div>
        )}

        {data.items.length === 0 ? (
          <EmptyState icon={Wallet}>No lines yet. Add the venues’ fees, permits, deposits and the rest as they come in.</EmptyState>
        ) : (
          <ul aria-label="Budget lines" className="divide-y divide-line rounded-lg border border-line bg-white">
            {data.items.map((item) => (
              <LineRow key={item.id} projectId={projectId} item={item} currency={data.currency} onSaved={store} />
            ))}
          </ul>
        )}

        {data.byCategory.length > 1 && (
          <dl aria-label="By category" className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {data.byCategory.map((row) => (
              <div key={row.category} className="rounded-md bg-ground px-3 py-2">
                <dt className="font-script text-[11px] font-bold tracking-[0.1em] text-muted uppercase">{categoryLabels[row.category]}</dt>
                <dd className="font-semibold">{money(row.amount)}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </Section>
  )
}

function Summary({ projectId, budget, onSaved }: { projectId: string; budget: Budget; onSaved: (budget: Budget) => void }) {
  const { api } = useSession()
  const canEdit = useCanEdit()
  const [editing, setEditing] = useState(false)
  const [total, setTotal] = useState(budget.total?.toString() ?? '')
  const [currency, setCurrency] = useState(budget.currency)
  const save = useMutation({
    mutationFn: () => api.budget.settings(projectId, { total: total.trim() === '' ? null : Number(total), currency: currency.trim().toUpperCase() }),
    onSuccess: (updated) => {
      onSaved(updated)
      setEditing(false)
    },
  })
  const errors = fieldErrors(save.error)
  const money = (amount: number) => formatMoney(amount, budget.currency)
  const { planned, committed, paid, remaining } = budget.totals
  const share = budget.total ? Math.min(100, (planned / budget.total) * 100) : null
  const over = remaining != null && remaining < 0

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  return (
    <div className="space-y-4 rounded-lg border border-line bg-white p-5">
      {editing ? (
        <form onSubmit={submit} className="grid gap-3 sm:grid-cols-[1fr_8rem_auto] sm:items-end" noValidate>
          <TextField label="Total budget" inputMode="decimal" value={total} onChange={(e) => setTotal(e.target.value)} error={errors.total} autoFocus />
          <TextField label="Currency" maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value)} error={errors.currency} />
          <div className="flex gap-2">
            <Button variant="ghost" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button type="submit" busy={save.isPending}>
              Save
            </Button>
          </div>
          <div className="sm:col-span-3">
            <ErrorAlert error={save.error} />
          </div>
        </form>
      ) : (
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="font-script text-xs font-bold tracking-[0.1em] text-muted uppercase">Total budget</p>
            <p className="font-display text-4xl leading-none">{budget.total != null ? money(budget.total) : 'Not set'}</p>
          </div>
          {canEdit && (
            <Button variant="ghost" onClick={() => setEditing(true)}>
              {budget.total != null ? 'Change the total' : 'Set a total'}
            </Button>
          )}
        </div>
      )}
      {share != null && (
        <div
          role="meter"
          aria-label="Planned against the total"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(share)}
          className="h-3 overflow-hidden rounded-full bg-ground"
        >
          <div className={`h-full ${over ? 'bg-stop' : 'bg-go-mid'}`} style={{ width: `${share}%` }} />
        </div>
      )}
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Figure label="Planned" value={money(planned)} />
        <Figure label="Committed" value={money(committed)} />
        <Figure label="Paid" value={money(paid)} />
        <Figure label={over ? 'Over budget' : 'Left'} value={remaining == null ? '—' : money(Math.abs(remaining))} tone={over ? 'stop' : undefined} />
      </dl>
    </div>
  )
}

function Figure({ label, value, tone }: { label: string; value: string; tone?: 'stop' }) {
  return (
    <div>
      <dt className={`font-script text-[11px] font-bold tracking-[0.1em] uppercase ${tone ? 'text-stop-ink' : 'text-muted'}`}>{label}</dt>
      <dd className={`text-lg font-semibold ${tone ? 'text-stop-ink' : ''}`}>{value}</dd>
    </div>
  )
}

function UnbudgetedVenueRow({
  projectId,
  currency,
  venue,
  onSaved,
}: {
  projectId: string
  currency: string
  venue: Budget['unbudgetedVenues'][number]
  onSaved: (budget: Budget) => void
}) {
  const { api } = useSession()
  const amount = quoteAmount(venue.quote, venue.shootDays)
  const days = venue.shootDays && venue.shootDays > 1 ? `, ${venue.shootDays} days` : ''
  const add = useMutation({
    mutationFn: () =>
      api.budget.add(projectId, {
        category: 'VENUE',
        label: `${venue.name}${days}`,
        amount: amount ?? 0,
        status: 'COMMITTED',
        note: venue.quote ? `Quote: ${venue.quote}` : null,
        locationId: venue.locationId,
      }),
    onSuccess: onSaved,
  })
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 py-2">
      <div>
        <Link to={`/locations/${venue.locationId}`} className="font-semibold text-ink underline-offset-2 hover:underline">
          {venue.name}
        </Link>
        <p className="text-sm text-muted">
          {venue.sceneTitle}
          {venue.quote ? ` · quoted ${venue.quote}` : ' · no quote yet'}
        </p>
      </div>
      <div className="space-y-1 text-right">
        <Button variant="secondary" busy={add.isPending} onClick={() => add.mutate()} aria-label={`Add ${venue.name} to the budget`}>
          {amount != null ? `Add ${formatMoney(amount, currency)}` : 'Add'}
        </Button>
        <ErrorAlert error={add.error} />
      </div>
    </li>
  )
}

function LineRow({ projectId, item, currency, onSaved }: { projectId: string; item: BudgetItem; currency: string; onSaved: (budget: Budget) => void }) {
  const { api } = useSession()
  const canEdit = useCanEdit()
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const remove = useMutation({ mutationFn: () => api.budget.remove(item.id), onSuccess: onSaved })
  const setStatus = useMutation({
    mutationFn: (status: BudgetStatus) => api.budget.update(item.id, { ...requestOf(item), status }),
    onSuccess: onSaved,
  })

  if (editing) {
    return (
      <li className="p-4">
        <LineForm
          projectId={projectId}
          currency={currency}
          item={item}
          submitLabel="Save the line"
          onCancel={() => setEditing(false)}
          onSaved={(updated) => {
            onSaved(updated)
            setEditing(false)
          }}
        />
      </li>
    )
  }
  return (
    <li className="space-y-2 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold">{item.label}</p>
          <p className="text-sm text-muted">
            {categoryLabels[item.category]}
            {item.venueName && item.locationId && (
              <>
                {' · '}
                <Link to={`/locations/${item.locationId}`} className="underline-offset-2 hover:underline">
                  {item.venueName}
                </Link>
              </>
            )}
            {item.note && ` · ${item.note}`}
          </p>
        </div>
        <div className="flex items-center gap-3">
          {canEdit ? (
            <select
              aria-label={`Stage of ${item.label}`}
              value={item.status}
              disabled={setStatus.isPending}
              onChange={(e) => setStatus.mutate(e.target.value as BudgetStatus)}
              className="rounded-md border border-line bg-white px-2 py-1 text-sm font-semibold"
            >
              {budgetStatuses.map((status) => (
                <option key={status} value={status}>
                  {budgetStatusLabels[status]}
                </option>
              ))}
            </select>
          ) : (
            <span className="text-sm font-semibold text-muted">{budgetStatusLabels[item.status]}</span>
          )}
          <span className="font-display text-xl">{formatMoney(item.amount, currency)}</span>
        </div>
      </div>
      <ErrorAlert error={setStatus.error ?? remove.error} />
      {canEdit &&
        (confirming ? (
          <ConfirmDelete
            title={`Remove “${item.label}”?`}
            confirmLabel="Remove"
            busy={remove.isPending}
            error={remove.error}
            onConfirm={() => remove.mutate()}
            onCancel={() => setConfirming(false)}
          >
            The line goes from the budget; nothing else changes.
          </ConfirmDelete>
        ) : (
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setConfirming(true)} aria-label={`Remove ${item.label}`}>
              Remove
            </Button>
            <Button variant="ghost" onClick={() => setEditing(true)} aria-label={`Edit ${item.label}`}>
              Edit
            </Button>
          </div>
        ))}
    </li>
  )
}

function requestOf(item: BudgetItem): BudgetItemRequest {
  return { category: item.category, label: item.label, amount: item.amount, status: item.status, note: item.note, locationId: item.locationId }
}

function LineForm({
  projectId,
  currency,
  item,
  submitLabel,
  onCancel,
  onSaved,
}: {
  projectId: string
  currency: string
  item?: BudgetItem
  submitLabel: string
  onCancel: () => void
  onSaved: (budget: Budget) => void
}) {
  const { api } = useSession()
  const [category, setCategory] = useState<BudgetCategory>(item?.category ?? 'OTHER')
  const [label, setLabel] = useState(item?.label ?? '')
  const [amount, setAmount] = useState(item?.amount.toString() ?? '')
  const [status, setStatus] = useState<BudgetStatus>(item?.status ?? 'ESTIMATE')
  const [note, setNote] = useState(item?.note ?? '')
  const amountNumber = Number(amount)
  const amountProblem = amount.trim() !== '' && (Number.isNaN(amountNumber) || amountNumber < 0) ? 'An amount of 0 or more.' : undefined
  const save = useMutation({
    mutationFn: () => {
      const body: BudgetItemRequest = { category, label: label.trim(), amount: amountNumber, status, note: note.trim() || null, locationId: item?.locationId ?? null }
      return item ? api.budget.update(item.id, body) : api.budget.add(projectId, body)
    },
    onSuccess: onSaved,
  })
  const errors = fieldErrors(save.error)

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-lg border border-line bg-white p-4" noValidate>
      <ErrorAlert error={save.error} />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="What for" required maxLength={200} value={label} onChange={(e) => setLabel(e.target.value)} error={errors.label} autoFocus />
        <TextField
          label={`Amount (${currency})`}
          required
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          error={amountProblem ?? errors.amount}
        />
        <SelectField label="Category" value={category} onChange={(e) => setCategory(e.target.value as BudgetCategory)}>
          {budgetCategories.map((value) => (
            <option key={value} value={value}>
              {categoryLabels[value]}
            </option>
          ))}
        </SelectField>
        <SelectField label="Stage" value={status} onChange={(e) => setStatus(e.target.value as BudgetStatus)}>
          {budgetStatuses.map((value) => (
            <option key={value} value={value}>
              {budgetStatusLabels[value]}
            </option>
          ))}
        </SelectField>
      </div>
      <TextField label="Note" maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} error={errors.note} />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onCancel} disabled={save.isPending}>
          Cancel
        </Button>
        <Button type="submit" busy={save.isPending} disabled={!label.trim() || amount.trim() === '' || !!amountProblem}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
