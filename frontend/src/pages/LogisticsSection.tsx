import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { queryKeys } from '../api/queryKeys'
import type {
  Location,
  LogisticsReport,
  NoiseLevel,
  PlaceKind,
  SceneLight,
  SectionStatus,
  SolarDay,
  WeatherBasis,
  WeatherDay,
} from '../api/types'
import { useSession } from '../auth/context'
import { CloudSun, Sun, Sunset, Trees, type LucideIcon } from 'lucide-react'
import { EmptyState, Section } from '../components/surfaces'
import { Badge, Button, ErrorAlert, Spinner } from '../components/ui'
import { formatDate, formatShootWindow } from '../lib/format'
import { formatDaylight, formatDistance, localTime, temperatureRange, timeWindow } from '../lib/logisticsFormat'
import { useCanEdit } from '../components/projectRole'

/** The report shape this page understands (LogisticsReport.VERSION on the server). */
const SUPPORTED_VERSION = 1

const sceneLightLabels: Record<SceneLight, string> = {
  DAWN: 'Dawn',
  DUSK: 'Dusk',
  GOLDEN_HOUR: 'Golden hour',
  BLUE_HOUR: 'Blue hour',
  NIGHT: 'Night',
  DAY: 'Daylight',
}

const placeLabels: Record<PlaceKind, string> = {
  HOSPITAL: 'Hospital',
  PHARMACY: 'Pharmacy',
  PARKING: 'Parking',
  FOOD: 'Food',
  TOILETS: 'Toilets',
  FUEL: 'Fuel',
  LODGING: 'Lodging',
  HARDWARE: 'Hardware store',
  GROCERY: 'Grocery',
  AIRPORT: 'Airport',
  HELIPORT: 'Heliport',
  STADIUM: 'Stadium',
  RAILWAY: 'Railway',
  EMERGENCY_STATION: 'Emergency station',
  CONSTRUCTION: 'Construction',
  MAJOR_ROAD: 'Major road',
  SCHOOL: 'School',
  NIGHTLIFE: 'Nightlife',
  PLACE_OF_WORSHIP: 'Place of worship',
}

const noiseTones: Record<NoiseLevel, 'green' | 'cue' | 'red'> = { LOW: 'green', MEDIUM: 'cue', HIGH: 'red' }
const noiseLabels: Record<NoiseLevel, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' }

const generatedFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** A location's shoot logistics: the light, the weather and the surroundings on the scene's shoot days. */
export function LogisticsSection({ location }: { location: Location }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()

  const refresh = useMutation({
    mutationFn: () => api.locations.refreshLogistics(location.id),
    onSuccess: (report) => {
      // Show the report at once; the refetch picks up the coordinates the server may have looked up and kept.
      queryClient.setQueryData<Location>(queryKeys.location(location.id), (current) => current && { ...current, logistics: report })
      queryClient.invalidateQueries({ queryKey: queryKeys.location(location.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.locationList(location.sceneId) })
    },
  })

  const report = location.logistics
  return (
    <Section
      titleId="logistics-heading"
      title="Logistics"
      eyebrow="The day of the shoot"
      icon={Sun}
      description={report && `Worked out ${generatedFormat.format(new Date(report.generatedAt))}`}
      actions={
        canEdit && <Button variant={report ? 'secondary' : 'primary'} busy={refresh.isPending} onClick={() => refresh.mutate()}>
          {report ? 'Refresh' : 'Work out logistics'}
        </Button>
      }
    >

      {refresh.isPending ? (
        <Spinner label="Checking the light, weather and surroundings. This can take up to half a minute." />
      ) : (
        <ErrorAlert error={refresh.error} />
      )}

      {!report ? (
        !refresh.isPending && (
          <EmptyState icon={Sunset}>
            Not worked out yet. CineScout checks golden and blue hours, the weather and nearby noise and services for the scene's shoot
            days.
          </EmptyState>
        )
      ) : report.version !== SUPPORTED_VERSION ? (
        <p className="text-sm text-muted">This report was saved in a format this page does not know. Refresh it to see it.</p>
      ) : (
        <Report report={report} />
      )}
    </Section>
  )
}

function Report({ report }: { report: LogisticsReport }) {
  const { shootWindow } = report
  return (
    <div className="space-y-6">
      <p className="text-sm text-graphite">
        {formatShootWindow(shootWindow.start, shootWindow.end)} · times are local to the venue ({report.timeZone})
      </p>
      {(report.notes.length > 0 || shootWindow.assumed || shootWindow.truncated) && (
        <ul aria-label="About this report" className="space-y-1 rounded-md border border-cue bg-cue-wash px-4 py-3 text-sm text-cue-ink">
          {shootWindow.assumed && <li>The scene has no shoot dates yet, so this covers the coming days.</li>}
          {shootWindow.truncated && <li>The shoot window is longer than one report covers; this shows its start.</li>}
          {report.notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      )}
      <Light solar={report.solar} />
      <Weather weather={report.weather} />
      <Surroundings environment={report.environment} />
      {report.attribution.length > 0 && (
        <footer className="text-xs text-subtle">
          {report.attribution.map((credit) => (
            <p key={credit}>{credit}</p>
          ))}
        </footer>
      )}
    </div>
  )
}

const panelIcons: Record<string, LucideIcon> = { Light: Sunset, Weather: CloudSun, Surroundings: Trees }

function Panel({ title, children }: { title: string; children: ReactNode }) {
  const Icon = panelIcons[title]
  return (
    <section aria-label={title} className="space-y-4 board-card rounded-lg bg-white p-5">
      <h3 className="flex items-center gap-2 font-display text-2xl leading-none text-ink">
        {Icon && (
          <span className="flex size-8 items-center justify-center rounded-full bg-cue-wash text-cue-ink ring-1 ring-cue">
            <Icon aria-hidden className="size-4" />
          </span>
        )}
        {title}
      </h3>
      {children}
    </section>
  )
}

/** Why a section has no (or not all) data, as the server put it. */
function SectionMessage({ status, message }: { status: SectionStatus; message: string | null }) {
  if (status === 'OK') return null
  const fallback = status === 'PARTIAL' ? 'Some days could not be looked up.' : 'Not available right now; refresh later.'
  return <p className="text-sm text-muted">{message ?? fallback}</p>
}

const windows = (list: SolarDay['goldenHours'], day: string) => (list.length === 0 ? '—' : list.map((w) => timeWindow(w, day)).join(', '))

function Light({ solar }: { solar: LogisticsReport['solar'] }) {
  const sceneLight = solar.sceneLight && sceneLightLabels[solar.sceneLight]
  return (
    <Panel title="Light">
      {solar.timeOfDay && (
        <p className="text-sm text-muted">
          The scene is set at “{solar.timeOfDay}”
          {sceneLight ? `, read as ${sceneLight.toLowerCase()}.` : ', which names no natural light.'}
        </p>
      )}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs tracking-wide text-subtle uppercase">
            <tr>
              <th scope="col" className="py-2 pr-4 font-medium">Day</th>
              <th scope="col" className="py-2 pr-4 font-medium">Sunrise</th>
              <th scope="col" className="py-2 pr-4 font-medium">Sunset</th>
              <th scope="col" className="py-2 pr-4 font-medium">Daylight</th>
              <th scope="col" className="py-2 pr-4 font-medium">Golden hour</th>
              <th scope="col" className="py-2 pr-4 font-medium">Blue hour</th>
              {sceneLight && <th scope="col" className="py-2 font-medium text-cue-ink">{sceneLight} (scene)</th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-line-soft">
            {solar.days.map((day) => (
              <tr key={day.date}>
                <th scope="row" className="py-2 pr-4 font-medium whitespace-nowrap">{formatDate(day.date)}</th>
                <td className="py-2 pr-4">{day.sunrise ? localTime(day.sunrise, day.date) : 'None'}</td>
                <td className="py-2 pr-4">{day.sunset ? localTime(day.sunset, day.date) : 'None'}</td>
                <td className="py-2 pr-4 whitespace-nowrap">{formatDaylight(day.daylightMinutes)}</td>
                <td className="py-2 pr-4">{windows(day.goldenHours, day.date)}</td>
                <td className="py-2 pr-4">{windows(day.blueHours, day.date)}</td>
                {sceneLight && <td className="py-2 text-cue-ink">{day.sceneWindows.length === 0 ? 'Does not occur' : windows(day.sceneWindows, day.date)}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  )
}

function basisLabel(day: WeatherDay): string {
  const labels: Record<WeatherBasis, string> = {
    FORECAST: 'Forecast',
    RECORDED: 'Recorded',
    PAST_YEAR: `Too far ahead to forecast: as recorded on ${formatDate(day.referenceDate)}`,
  }
  return labels[day.basis]
}

function Weather({ weather }: { weather: LogisticsReport['weather'] }) {
  return (
    <Panel title="Weather">
      <SectionMessage status={weather.status} message={weather.message} />
      {weather.days.length > 0 && (
        <ul className="divide-y divide-line-soft">
          {weather.days.map((day) => {
            const facts = [
              temperatureRange(day.temperatureMinC, day.temperatureMaxC),
              day.precipitationMm != null &&
                `${day.precipitationMm} mm rain${day.precipitationProbabilityPercent != null ? ` (${day.precipitationProbabilityPercent}% chance)` : ''}`,
              day.windSpeedMaxKmh != null &&
                `Wind ${Math.round(day.windSpeedMaxKmh)} km/h${day.windGustsMaxKmh != null ? `, gusts ${Math.round(day.windGustsMaxKmh)} km/h` : ''}`,
              day.cloudCoverPercent != null && `${day.cloudCoverPercent}% cloud`,
            ].filter(Boolean)
            return (
              <li key={day.date} aria-label={formatDate(day.date)} className="space-y-1 py-3 first:pt-0 last:pb-0">
                <div className="flex flex-wrap items-baseline gap-x-3">
                  <span className="font-medium">{formatDate(day.date)}</span>
                  {day.summary && <span className="text-ink">{day.summary}</span>}
                  <span className="text-xs text-subtle">{basisLabel(day)}</span>
                </div>
                {facts.length > 0 && <p className="text-sm text-muted">{facts.join(' · ')}</p>}
                {day.warnings.length > 0 && (
                  <ul className="list-inside list-disc text-sm text-cue-ink">
                    {day.warnings.map((warning) => (
                      <li key={warning}>{warning}</li>
                    ))}
                  </ul>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </Panel>
  )
}

const subheading = 'pt-2 text-sm font-semibold text-graphite'

function Surroundings({ environment }: { environment: LogisticsReport['environment'] }) {
  // Grouped by kind, in the server's order (nearest first within a kind).
  const services = new Map<PlaceKind, LogisticsReport['environment']['nearbyServices']>()
  for (const service of environment.nearbyServices) services.set(service.kind, [...(services.get(service.kind) ?? []), service])

  return (
    <Panel title="Surroundings">
      <SectionMessage status={environment.status} message={environment.message} />
      {environment.noiseRisk && (
        <p className="flex flex-wrap items-center gap-2 text-sm">
          Noise risk <Badge tone={noiseTones[environment.noiseRisk]}>{noiseLabels[environment.noiseRisk]}</Badge>
          {environment.acousticSensitivity && (
            <span className="text-muted">for a scene whose sound sensitivity is {environment.acousticSensitivity.toLowerCase()}</span>
          )}
        </p>
      )}
      {environment.noiseSources.length > 0 && <h4 className={subheading}>Noise sources</h4>}
      {environment.noiseSources.length > 0 && (
        <ul aria-label="Noise sources" className="space-y-2 text-sm">
          {environment.noiseSources.map((source, i) => (
            <li key={`${source.kind}-${source.name}-${i}`}>
              <span className="font-medium">
                {placeLabels[source.kind]}
                {source.name && `: ${source.name}`}
              </span>{' '}
              <span className="text-muted">· {formatDistance(source.distanceMeters)}</span>{' '}
              <Badge tone={noiseTones[source.level]}>{noiseLabels[source.level]}</Badge>
              <p className="text-muted">{source.advice}</p>
            </li>
          ))}
        </ul>
      )}
      {environment.status !== 'UNAVAILABLE' && environment.noiseSources.length === 0 && (
        <p className="text-sm text-muted">No known noise sources nearby.</p>
      )}
      {services.size > 0 && <h4 className={subheading}>Nearby services</h4>}
      {services.size > 0 && (
        <dl aria-label="Nearby services" className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          {[...services].map(([kind, places]) => (
            <div key={kind}>
              <dt className="text-xs font-medium tracking-wide text-subtle uppercase">{placeLabels[kind]}</dt>
              {places.map((place, i) => (
                <dd key={i} className="text-ink">
                  {place.name ?? 'Unnamed'} <span className="text-muted">· {formatDistance(place.distanceMeters)}</span>
                </dd>
              ))}
            </div>
          ))}
        </dl>
      )}
    </Panel>
  )
}
