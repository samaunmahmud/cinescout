import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, CircleHelp, MapPin as PinIcon, TriangleAlert, X } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { useParams } from 'react-router'
import { publicApi } from '../api/endpoints'
import { fieldErrors, isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { DirectorVerdict, ShortlistVenue } from '../api/types'
import { useAuth } from '../auth/context'
import { DirectorsCall } from '../components/DirectorsCall'
import { Logo } from '../components/Layout'
import { FitLabel, FitScore, LocationBadges } from '../components/locationParts'
import { locationPin } from '../components/map/locationPin'
import type { MapPin } from '../components/map/types'
import { VenueMap } from '../components/map/VenueMap'
import { MapSnapshot } from '../components/MapSnapshot'
import { Pager } from '../components/Pager'
import { TapeLabel } from '../components/stickers'
import { Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { VenuePicture } from '../components/VenuePicture'
import { usePageTitle } from '../lib/usePageTitle'

const NAME_KEY = 'cinescout.guestName'

function rememberedName(): string {
  try {
    return localStorage.getItem(NAME_KEY) ?? ''
  } catch {
    return ''
  }
}

function rememberName(name: string) {
  try {
    localStorage.setItem(NAME_KEY, name)
  } catch {
    // A private window: the name is simply asked again next time.
  }
}

const choices: { verdict: DirectorVerdict; label: string; icon: typeof Check; picked: string }[] = [
  { verdict: 'APPROVE', label: 'Approve', icon: Check, picked: 'border-go-mid bg-go-wash text-go-ink' },
  { verdict: 'MAYBE', label: 'Maybe', icon: CircleHelp, picked: 'border-cue bg-cue-wash text-cue-deep' },
  { verdict: 'NO', label: 'No', icon: X, picked: 'border-stop bg-stop-wash text-stop-ink' },
]

/**
 * A shortlist sent to the director by link: no account, nothing but the venues and a call on each. The name the
 * guest types is remembered in this browser, so each venue's form starts from their earlier call.
 */
export function PublicShortlistPage() {
  const { token = '' } = useParams()
  // As on the shared call sheet: wait until the app knows whether someone is logged in, as finding a session clears
  // every cached query.
  const { checking } = useAuth()
  const [page, setPage] = useState(0)
  const [name, setName] = useState(rememberedName)
  const shortlist = useQuery({
    queryKey: queryKeys.publicShortlist(token, page),
    queryFn: () => publicApi.shortlist(token, page),
    retry: false,
    enabled: !checking,
  })
  usePageTitle(shortlist.data ? `Shortlist: ${shortlist.data.projectTitle}` : 'Shortlist')

  return (
    <div className="mx-auto max-w-5xl space-y-8 px-4 py-8">
      <Logo onDark={false} />
      {shortlist.isPending ? (
        <Spinner label="Loading the shortlist" />
      ) : shortlist.isError ? (
        isNotFound(shortlist.error) ? (
          <div role="alert" className="space-y-3 py-16 text-center">
            <h1 className="font-display text-5xl leading-none font-bold">Not shared</h1>
            <p className="text-lg text-muted">This shortlist is not shared, or no longer is. Ask the production for a new link.</p>
          </div>
        ) : (
          <ErrorAlert error={shortlist.error} onRetry={() => shortlist.refetch()} />
        )
      ) : (
        <>
          <header className="relative space-y-3 rounded-lg border border-line bg-night px-6 pt-10 pb-7 text-white shadow-[var(--shadow-card)]">
            <TapeLabel tilt={-3} className="absolute -top-4 left-6">
              Director’s cut
            </TapeLabel>
            <h1 className="font-display text-4xl leading-[0.95] font-bold sm:text-6xl">{shortlist.data.projectTitle}</h1>
            {shortlist.data.sceneTitle && <p className="font-script text-lg text-fog">{shortlist.data.sceneTitle}</p>}
            <p className="max-w-prose text-fog">
              {shortlist.data.sharedBy ? `${shortlist.data.sharedBy} sent you` : 'You have been sent'} the venues on the shortlist. Approve, say
              maybe or pass on each one, and add a note if you like. You can change your call at any time.
            </p>
          </header>

          <div className="max-w-sm">
            <TextField
              label="Your name"
              autoComplete="name"
              maxLength={60}
              value={name}
              onChange={(e) => setName(e.target.value)}
              onBlur={() => rememberName(name.trim())}
              hint="Shown to the production with each call you make."
            />
          </div>

          {shortlist.data.venues.items.length === 0 ? (
            <p className="rounded-lg border-2 border-dashed border-line bg-paper px-6 py-10 text-center text-muted">
              Nothing is on the shortlist yet. Come back to this link once the production has picked some venues.
            </p>
          ) : (
            <>
              <ShortlistMap venues={shortlist.data.venues.items} />
              <ul aria-label="Shortlisted venues" className="space-y-6">
                {shortlist.data.venues.items.map((venue) => (
                  <li key={venue.id}>
                    <VenueCard token={token} venue={venue} guestName={name.trim()} onAnswered={() => rememberName(name.trim())} />
                  </li>
                ))}
              </ul>
              <Pager data={shortlist.data.venues} onChange={setPage} label="Shortlist pages" />
            </>
          )}
        </>
      )}
    </div>
  )
}

function ShortlistMap({ venues }: { venues: ShortlistVenue[] }) {
  const pins = venues.map((venue) => locationPin(venue, { withLink: false })).filter((pin): pin is MapPin => pin !== null)
  if (pins.length === 0) return null
  return <VenueMap pins={pins} label="Map of the shortlisted venues" className="h-72" />
}

function VenueCard({ token, venue, guestName, onAnswered }: { token: string; venue: ShortlistVenue; guestName: string; onAnswered: () => void }) {
  const placed = venue.latitude != null && venue.longitude != null
  return (
    <article aria-labelledby={`venue-${venue.id}`} className="board-card space-y-5 rounded-lg bg-paper p-5">
      <div className="flex flex-col gap-5 sm:flex-row">
        <div aria-hidden className="polaroid relative w-full shrink-0 sm:w-56">
          <span className="tape-piece -top-2 left-1/2 z-10 w-14 -translate-x-1/2 -rotate-3" />
          <div className="relative h-40 bg-ground">
            {placed ? (
              <MapSnapshot latitude={venue.latitude!} longitude={venue.longitude!} />
            ) : (
              <div className="flex h-full items-center justify-center">
                <PinIcon className="size-6 text-line" />
              </div>
            )}
            {venue.imageUrl && <VenuePicture src={venue.imageUrl} className="absolute inset-0 h-full w-full" />}
          </div>
          <span className="absolute right-2 bottom-1 left-2 truncate font-marker text-[13px] text-graphite">{venue.name}</span>
        </div>
        <div className="min-w-0 flex-1 space-y-3">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0 space-y-1">
              <p className="font-script text-xs font-bold tracking-[0.08em] text-muted uppercase">{venue.sceneTitle}</p>
              <h2 id={`venue-${venue.id}`} className="font-display text-3xl leading-none">
                {venue.name}
              </h2>
              {venue.address && <p className="text-sm text-muted">{venue.address}</p>}
            </div>
            {venue.fitScore != null && (
              <div className="flex flex-col items-center gap-1">
                <FitScore score={venue.fitScore} />
                <FitLabel score={venue.fitScore} />
              </div>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <LocationBadges location={venue} />
          </div>
          {venue.frictionNote && <p className="text-[15px] text-graphite">{venue.frictionNote}</p>}
          {venue.warnings.length > 0 && (
            <ul aria-label="Warnings" className="space-y-1 text-sm text-cue-deep">
              {venue.warnings.map((warning) => (
                <li key={warning} className="flex items-start gap-1.5">
                  <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
                  {warning}
                </li>
              ))}
            </ul>
          )}
          {(venue.fitReason || venue.notes || venue.quote) && (
            <dl className="space-y-2 rounded-lg bg-ground p-3 text-sm">
              {venue.fitReason && <Detail term="Why it fits">{venue.fitReason}</Detail>}
              {venue.quote && <Detail term="Quote">{venue.quote}</Detail>}
              {venue.notes && <Detail term="Production notes">{venue.notes}</Detail>}
            </dl>
          )}
        </div>
      </div>
      <CallForm token={token} venue={venue} guestName={guestName} onAnswered={onAnswered} />
      {venue.responses.length > 0 && (
        <div className="space-y-2 border-t border-line-soft pt-4">
          <h3 className="font-script text-xs font-bold tracking-[0.08em] text-muted uppercase">Calls so far</h3>
          <DirectorsCall calls={venue.responses} />
        </div>
      )}
    </article>
  )
}

function Detail({ term, children }: { term: string; children: string }) {
  return (
    <div>
      <dt className="font-script text-xs font-bold tracking-[0.08em] text-muted uppercase">{term}</dt>
      <dd className="whitespace-pre-line text-graphite">{children}</dd>
    </div>
  )
}

function CallForm({ token, venue, guestName, onAnswered }: { token: string; venue: ShortlistVenue; guestName: string; onAnswered: () => void }) {
  const queryClient = useQueryClient()
  const id = useId()
  const mine = venue.responses.find((call) => call.guestName.toLowerCase() === guestName.toLowerCase())
  const [verdict, setVerdict] = useState<DirectorVerdict | null>(mine?.verdict ?? null)
  const [comment, setComment] = useState(mine?.comment ?? '')
  const [sent, setSent] = useState(false)
  const answer = useMutation({
    mutationFn: () => publicApi.answer(token, venue.id, { guestName, verdict: verdict!, comment: comment.trim() || null }),
    onSuccess: () => {
      setSent(true)
      onAnswered()
      queryClient.invalidateQueries({ queryKey: ['public', 'shortlist', token] })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setSent(false)
    answer.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-3 border-t border-line-soft pt-4" noValidate>
      <fieldset className="space-y-2">
        <legend className="font-script text-[13px] font-bold tracking-[0.08em] text-ink uppercase">Your call on {venue.name}</legend>
        <div className="flex flex-wrap gap-2">
          {choices.map((choice) => {
            const picked = verdict === choice.verdict
            const Icon = choice.icon
            return (
              <label
                key={choice.verdict}
                className={`flex cursor-pointer items-center gap-1.5 rounded-lg border-2 px-4 py-2 text-sm font-bold has-[:focus-visible]:ring-4 has-[:focus-visible]:ring-cue/40 ${
                  picked ? choice.picked : 'border-line bg-paper text-graphite hover:border-ink'
                }`}
              >
                <input
                  type="radio"
                  name={`${id}-verdict`}
                  value={choice.verdict}
                  checked={picked}
                  onChange={() => setVerdict(choice.verdict)}
                  className="sr-only"
                />
                <Icon aria-hidden className="size-4" />
                {choice.label}
              </label>
            )
          })}
        </div>
      </fieldset>
      <TextArea
        label="A note for the production (optional)"
        rows={2}
        maxLength={2000}
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        error={fieldErrors(answer.error).comment}
      />
      <ErrorAlert error={fieldErrors(answer.error).comment ? null : answer.error} />
      <div className="flex flex-wrap items-center justify-end gap-3">
        {!guestName && <span className="text-sm text-muted">Type your name above first.</span>}
        {sent && (
          <span role="status" className="text-sm font-semibold text-go-ink">
            Sent. You can change it any time.
          </span>
        )}
        <Button type="submit" variant="secondary" busy={answer.isPending} disabled={!guestName || !verdict}>
          {mine ? 'Update my call' : 'Send my call'}
        </Button>
      </div>
    </form>
  )
}
