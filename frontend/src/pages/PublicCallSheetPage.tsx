import { useQuery } from '@tanstack/react-query'
import { Printer } from 'lucide-react'
import { useParams } from 'react-router'
import { publicApi } from '../api/endpoints'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../auth/context'
import { CallSheet } from '../components/CallSheet'
import { Logo } from '../components/Layout'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { usePageTitle } from '../lib/usePageTitle'

/** A call sheet shared by link: for the crew, no account needed, nothing but the sheet. */
export function PublicCallSheetPage() {
  const { token = '' } = useParams()
  // Not before the app knows whether someone is logged in: finding a session clears every cached query, and would
  // take this one with it half-way.
  const { checking } = useAuth()
  const sheet = useQuery({ queryKey: queryKeys.publicCallSheet(token), queryFn: () => publicApi.callSheet(token), retry: false, enabled: !checking })
  usePageTitle(sheet.data ? `Call sheet: ${sheet.data.projectTitle}` : 'Call sheet')

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
      <div className="no-print flex flex-wrap items-center justify-between gap-4">
        <Logo onDark={false} />
        {sheet.data && (
          <Button onClick={() => window.print()}>
            <Printer aria-hidden className="size-4" />
            Print
          </Button>
        )}
      </div>
      {sheet.isPending ? (
        <Spinner label="Loading the call sheet" />
      ) : sheet.isError ? (
        isNotFound(sheet.error) ? (
          <div role="alert" className="space-y-3 py-16 text-center">
            <h1 className="font-extrabold font-display text-5xl leading-none">Not shared</h1>
            <p className="text-lg text-muted">This call sheet is not shared, or no longer is. Ask the production for a new link.</p>
          </div>
        ) : (
          <ErrorAlert error={sheet.error} onRetry={() => sheet.refetch()} />
        )
      ) : (
        <CallSheet title={sheet.data.projectTitle} locationArea={sheet.data.locationArea} preparedBy={sheet.data.preparedBy} schedule={sheet.data.schedule} />
      )}
    </div>
  )
}
