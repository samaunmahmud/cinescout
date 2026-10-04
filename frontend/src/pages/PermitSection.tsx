import { useQuery } from '@tanstack/react-query'
import { ExternalLink, Landmark } from 'lucide-react'
import { queryKeys } from '../api/queryKeys'
import type { Location, PermitGuidance } from '../api/types'
import { useSession } from '../auth/context'
import { Card, Section } from '../components/surfaces'
import { ErrorAlert, Spinner } from '../components/ui'
import { formatDate } from '../lib/format'

/**
 * For a venue marked as a public space: the filming office of its area, how many working days ahead to apply, and
 * what to have ready, from the repository's filming offices file. The area is looked up from the venue's pin.
 */
export function PermitSection({ location }: { location: Location }) {
  const { api } = useSession()
  const guide = useQuery({
    queryKey: queryKeys.permit(location.id, location.latitude, location.longitude),
    queryFn: () => api.locations.permit(location.id),
    staleTime: 5 * 60_000,
  })
  return (
    <Section titleId="permit-heading" title="Filming permit" eyebrow="A public space" icon={Landmark}>
      {guide.isPending ? (
        <Spinner label="Finding the filming office for this area" />
      ) : guide.isError ? (
        <ErrorAlert error={guide.error} onRetry={() => guide.refetch()} />
      ) : (
        <Guide guide={guide.data} />
      )}
    </Section>
  )
}

function Guide({ guide }: { guide: PermitGuidance }) {
  const office = guide.office
  return (
    <div className="space-y-3">
      {guide.status === 'NEEDS_POSITION' && (
        <p className="text-[15px] text-graphite">Put the venue on the map and CineScout finds the filming office for its area.</p>
      )}
      {guide.status === 'OUTSIDE_COVERAGE' && (
        <p className="text-[15px] text-graphite">
          The permit guide covers the UK so far{guide.areaName ? `, and this venue is in ${guide.areaName}` : ''}. Ask the local authority’s
          filming or events office.
        </p>
      )}
      {guide.status === 'LOOKUP_FAILED' && (
        <p className="text-[15px] text-graphite">The venue’s area could not be looked up just now. Until it can, ask the local council.</p>
      )}
      {office && (
        <Card className="space-y-3 p-5">
          <div className="space-y-1">
            <p className="font-script text-xs font-bold tracking-[0.12em] text-cue-ink uppercase">{office.area}</p>
            <h3 className="text-lg font-semibold text-ink">{office.name}</h3>
            {guide.areaName && !office.listed && <p className="text-sm text-muted">The venue is in {guide.areaName}.</p>}
          </div>
          {office.leadTimeWorkingDays != null ? (
            <p className="text-[15px] text-ink">
              Apply at least <strong>{office.leadTimeWorkingDays === 1 ? '1 working day' : `${office.leadTimeWorkingDays} working days`}</strong> ahead
              for this scene’s crew. The schedule reminds you when.
            </p>
          ) : (
            office.listed && <p className="text-[15px] text-ink">Ask how far ahead to apply.</p>
          )}
          {office.leadTimeText && <p className="text-sm text-muted">{office.leadTimeText}.</p>}
          {office.note && <p className="text-[15px] text-graphite">{office.note}</p>}
          <a
            href={office.contactUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-sm font-semibold text-cue-deep underline-offset-2 hover:underline"
          >
            Contact {office.listed ? office.name : 'the council'}
            <ExternalLink aria-hidden className="size-3.5" />
            <span className="sr-only"> (opens in a new tab)</span>
          </a>
          <div>
            <h4 className="text-sm font-semibold text-graphite">Have ready</h4>
            <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-graphite">
              {office.checklist.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </Card>
      )}
      <p className="text-xs text-muted">
        Guide last reviewed {formatDate(guide.lastReviewed)}
        {guide.sources.length > 0 && (
          <>
            {' from '}
            {guide.sources.map((source, i) => (
              <span key={source.url}>
                {i > 0 && ' and '}
                <a href={source.url} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2 hover:text-ink">
                  {source.name}
                </a>
              </span>
            ))}
          </>
        )}
        . Rules change: check with the office.
        {guide.editUrl && (
          <>
            {' '}
            <a href={guide.editUrl} target="_blank" rel="noopener noreferrer" className="font-semibold underline underline-offset-2 hover:text-ink">
              Correct this guide
            </a>
          </>
        )}
      </p>
    </div>
  )
}
