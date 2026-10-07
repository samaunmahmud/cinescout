import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CloudRain, Mail } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Alert } from '../api/types'
import { useSession } from '../auth/context'
import { alertWords } from '../lib/alerts'
import { ErrorAlert, Spinner } from './ui'

/** How often the unread count is asked for while a page stays open. */
const POLL_MS = 5 * 60 * 1000

const whenFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

/**
 * The bell in the header: how many alerts are unread, and the latest ten in a panel that opens below it. Opening an
 * alert marks it read. Escape or a click elsewhere closes the panel.
 */
export function AlertBell() {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const wrapper = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  const count = useQuery({ queryKey: queryKeys.alertCount, queryFn: () => api.alerts.unreadCount(), refetchInterval: POLL_MS })
  const list = useQuery({ queryKey: queryKeys.alertList, queryFn: () => api.alerts.list(), enabled: open })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['alerts'] })
  const read = useMutation({ mutationFn: (id: string) => api.alerts.markRead(id), onSettled: refresh })
  const readAll = useMutation({ mutationFn: () => api.alerts.markAllRead(), onSettled: refresh })

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (wrapper.current && !wrapper.current.contains(e.target as Node)) setOpen(false)
    }
    const escape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false)
        button.current?.focus()
      }
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  const unread = count.data?.unread ?? 0
  return (
    <div ref={wrapper} className="relative">
      <button
        ref={button}
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={unread > 0 ? `Alerts, ${unread} unread` : 'Alerts'}
        onClick={() => setOpen(!open)}
        className="relative flex size-10 items-center justify-center rounded-lg text-fog transition hover:bg-ink-soft hover:text-white focus-visible:outline-2 focus-visible:outline-cue"
      >
        <Bell aria-hidden className="size-5" />
        {unread > 0 && (
          <span aria-hidden className="absolute -top-0.5 -right-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-cue px-1 text-xs font-bold text-ink">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <section
          id={panelId}
          aria-labelledby={`${panelId}-heading`}
          className="absolute right-0 z-40 mt-2 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-lg border-2 border-ink bg-white text-ink shadow-[0_5px_0_var(--color-cue)]"
        >
          <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-3">
            <h2 id={`${panelId}-heading`} className="font-display text-xl leading-none">
              Alerts
            </h2>
            {unread > 0 && (
              <button
                type="button"
                onClick={() => readAll.mutate()}
                disabled={readAll.isPending}
                className="text-sm font-semibold text-cue-ink underline-offset-2 hover:underline disabled:opacity-50"
              >
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-[70vh] overflow-y-auto">
            {list.isPending ? (
              <div className="p-4">
                <Spinner label="Loading alerts" />
              </div>
            ) : list.isError ? (
              <div className="p-4">
                <ErrorAlert error={list.error} onRetry={() => list.refetch()} />
              </div>
            ) : list.data.items.length === 0 ? (
              <p className="px-4 py-6 text-sm text-muted">
                No alerts. CineScout tells you here when rain or wind threatens a shoot day, and when an email waits on a follow-up.
              </p>
            ) : (
              <ul aria-label="Latest alerts" className="divide-y divide-line-soft">
                {list.data.items.map((alert) => (
                  <li key={alert.id}>
                    <AlertItem
                      alert={alert}
                      onOpen={() => {
                        if (!alert.read) read.mutate(alert.id)
                        setOpen(false)
                      }}
                    />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      )}
    </div>
  )
}

function AlertItem({ alert, onOpen }: { alert: Alert; onOpen: () => void }) {
  const words = alertWords(alert)
  const Icon = alert.kind === 'WEATHER' ? CloudRain : Mail
  return (
    <Link
      to={words.to}
      onClick={onOpen}
      className={`flex gap-3 px-4 py-3 text-sm transition hover:bg-ground focus-visible:bg-ground focus-visible:outline-none ${alert.read ? '' : 'bg-cue-wash/60'}`}
    >
      <Icon aria-hidden className={`mt-0.5 size-4 shrink-0 ${alert.read ? 'text-subtle' : 'text-cue-ink'}`} />
      <span className="min-w-0 space-y-0.5">
        <span className={`block ${alert.read ? 'text-graphite' : 'font-semibold text-ink'}`}>
          {!alert.read && <span className="sr-only">Unread: </span>}
          {words.title}
        </span>
        {words.detail.map((line) => (
          <span key={line} className="block text-muted">
            {line}
          </span>
        ))}
        <span className="block text-xs text-subtle">
          {alert.projectTitle} · {whenFormat.format(new Date(alert.createdAt))}
        </span>
      </span>
    </Link>
  )
}
