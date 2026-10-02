import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Columns3 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Location, NoiseLevel, Page } from '../api/types'
import { useSession } from '../auth/context'
import { useUpdateLocation } from '../components/locationHooks'
import { FitScore, StatusSelect } from '../components/locationParts'
import { EmptyState, Eyebrow } from '../components/surfaces'
import { Badge, ErrorAlert, Spinner } from '../components/ui'
import { MAX_COMPARED, venuesToCompare } from '../lib/compare'
import { frictionLabel } from '../lib/fit'
import { formatDate, sceneLabel } from '../lib/format'
import { localTime, temperatureRange } from '../lib/logisticsFormat'
import { NotFoundPage } from './NotFoundPage'
import { usePageTitle } from '../lib/usePageTitle'

const noiseTones: Record<NoiseLevel, 'green' | 'cue' | 'red'> = { LOW: 'green', MEDIUM: 'cue', HIGH: 'red' }
const noiseLabels: Record<NoiseLevel, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' }

/**
 * A scene's leading venues side by side, one column each and one row per question a producer asks: does it
 * fit, who says yes, what could go wrong, what is the day like there. The shortlist when there is one,
 * otherwise the best fits.
 */
export function ComparePage() {
  const { sceneId = '' } = useParams()
  const { api } = useSession()
  const scene = useQuery({ queryKey: queryKeys.scene(sceneId), queryFn: () => api.scenes.get(sceneId) })
  usePageTitle(scene.data && `Compare venues: ${scene.data.title}`)
  // Refetched on every visit: statuses and notes change on the scene and venue pages.
  const locations = useQuery({
    queryKey: queryKeys.locationTop(sceneId),
    queryFn: () => api.locations.listTop(sceneId),
    enabled: scene.isSuccess,
    refetchOnMount: 'always',
  })

  if (scene.isPending) return <Spinner label="Loading scene" />
  if (scene.isError) {
    if (isNotFound(scene.error)) return <NotFoundPage />
    return <ErrorAlert error={scene.error} onRetry={() => scene.refetch()} />
  }

  const compared = locations.data ? venuesToCompare(locations.data.items) : null
  return (
    <div className="space-y-8">
      <Link to={`/scenes/${sceneId}`} className="inline-flex items-center gap-1 font-script text-sm font-bold tracking-[0.06em] text-muted uppercase hover:text-ink">
        <ChevronLeft aria-hidden className="size-4" />
        {sceneLabel(scene.data)}
      </Link>
      <header className="space-y-2">
        <Eyebrow icon={Columns3}>Side by side</Eyebrow>
        <h1 className="font-extrabold font-display text-6xl leading-none">Compare venues</h1>
        {compared && compared.venues.length > 1 && (
          <p className="max-w-prose text-muted">
            {compared.shortlist
              ? 'The venues you have shortlisted, contacted or confirmed for this scene.'
              : 'The best-fitting venues for this scene. Shortlist two or more and this page compares those instead.'}{' '}
            {`At most ${MAX_COMPARED} fit side by side.`}
          </p>
        )}
      </header>

      {locations.isPending ? (
        <Spinner label="Loading locations" />
      ) : locations.isError ? (
        <ErrorAlert error={locations.error} onRetry={() => locations.refetch()} />
      ) : compared!.venues.length < 2 ? (
        <EmptyState icon={Columns3}>There is nothing to compare yet: this scene needs at least two venues that have not been rejected.</EmptyState>
      ) : (
        <ComparisonTable venues={compared!.venues} />
      )}
    </div>
  )
}

function ComparisonTable({ venues }: { venues: Location[] }) {
  const rows: { label: string; cell: (venue: Location) => ReactNode }[] = [
    { label: 'Fit', cell: (venue) => (venue.fitScore != null ? <FitScore score={venue.fitScore} /> : <Muted>Not assessed</Muted>) },
    { label: 'Why', cell: (venue) => venue.fitReason ?? <Muted>—</Muted> },
    {
      label: 'Booking',
      cell: (venue) =>
        venue.bookingFriction ? (
          <div className="space-y-1">
            <p className="font-semibold text-ink">{frictionLabel(venue.bookingFriction)}</p>
            {venue.frictionNote && <p>{venue.frictionNote}</p>}
          </div>
        ) : (
          <Muted>Unknown</Muted>
        ),
    },
    {
      label: 'Warnings',
      cell: (venue) =>
        venue.footprintWarnings.length > 0 ? (
          <ul className="list-disc space-y-1 pl-4 text-cue-deep">
            {venue.footprintWarnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        ) : (
          <Muted>None found</Muted>
        ),
    },
    { label: 'Quote', cell: (venue) => venue.quote ?? <Muted>None yet</Muted> },
    { label: 'Address', cell: (venue) => venue.address ?? <Muted>Not known</Muted> },
    { label: 'Noise risk', cell: (venue) => <Noise venue={venue} /> },
    { label: 'First shoot day', cell: (venue) => <FirstDay venue={venue} /> },
    { label: 'Your notes', cell: (venue) => (venue.notes ? <span className="whitespace-pre-line italic">{venue.notes}</span> : <Muted>—</Muted>) },
    { label: 'Status', cell: (venue) => <Status venue={venue} /> },
  ]
  return (
    <>
    <p className="text-sm text-subtle sm:hidden">Swipe sideways to see every venue.</p>
    <div className="overflow-x-auto board-card rounded-lg bg-white">
      <table className="w-full min-w-[44rem] table-fixed border-collapse text-left text-sm text-graphite">
        <caption className="sr-only">Venues compared</caption>
        <thead>
          <tr className="border-b border-line">
            <td className="sticky left-0 z-10 w-28 bg-white p-4 sm:w-32" />
            {venues.map((venue) => (
              <th key={venue.id} scope="col" className="p-4 align-top">
                <Link to={`/locations/${venue.id}`} className="text-lg font-semibold text-ink hover:text-cue-deep">
                  {venue.name}
                </Link>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line-soft">
          {rows.map((row) => (
            <tr key={row.label}>
              <th scope="row" className="sticky left-0 z-10 bg-white p-4 align-top text-[11px] font-semibold tracking-wider text-subtle uppercase">
                {row.label}
              </th>
              {venues.map((venue) => (
                <td key={venue.id} className="p-4 align-top">
                  {row.cell(venue)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
    </>
  )
}

function Muted({ children }: { children: ReactNode }) {
  return <span className="text-subtle">{children}</span>
}

const needsLogistics = <Muted>Work out the venue’s logistics to see this</Muted>

function Noise({ venue }: { venue: Location }) {
  const environment = venue.logistics?.environment
  if (!environment) return needsLogistics
  if (!environment.noiseRisk) return <Muted>Not available</Muted>
  const loudest = environment.noiseSources[0]
  return (
    <div className="space-y-1">
      <Badge tone={noiseTones[environment.noiseRisk]}>{noiseLabels[environment.noiseRisk]}</Badge>
      {loudest && <p>{loudest.name ?? loudest.advice}</p>}
    </div>
  )
}

/** The light and the weather of the first shoot day, on the venue's clock. */
function FirstDay({ venue }: { venue: Location }) {
  const report = venue.logistics
  if (!report) return needsLogistics
  const sun = report.solar.days[0]
  const weather = report.weather.days[0]
  const temperature = weather && temperatureRange(weather.temperatureMinC, weather.temperatureMaxC)
  return (
    <div className="space-y-1">
      {sun && (
        <p>
          <span className="text-ink">{formatDate(sun.date)}</span>
          {sun.sunrise && sun.sunset && `: sun ${localTime(sun.sunrise, sun.date)}–${localTime(sun.sunset, sun.date)}`}
        </p>
      )}
      {weather ? (
        <p>
          {[weather.summary, temperature].filter(Boolean).join(', ')}
          {weather.basis === 'PAST_YEAR' && ' (an earlier year)'}
        </p>
      ) : (
        <Muted>No weather</Muted>
      )}
      {weather?.warnings.map((warning) => (
        <p key={warning} className="text-cue-deep">
          {warning}
        </p>
      ))}
    </div>
  )
}

function Status({ venue }: { venue: Location }) {
  const queryClient = useQueryClient()
  const update = useUpdateLocation(venue, (updated) =>
    queryClient.setQueryData<Page<Location>>(queryKeys.locationTop(venue.sceneId), (cached) =>
      cached && { ...cached, items: cached.items.map((l) => (l.id === updated.id ? updated : l)) },
    ),
  )
  return (
    <div className="space-y-2">
      <StatusSelect location={venue} update={update} />
      <ErrorAlert error={update.error} />
    </div>
  )
}
