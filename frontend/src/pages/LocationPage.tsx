import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { fieldErrors, isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Location } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { useStoreLocation, useUpdateLocation } from '../components/locationHooks'
import {
  Banknote,
  ChevronLeft,
  Clapperboard,
  Contact as Contact2,
  Crosshair,
  ExternalLink,
  Gauge,
  KeyRound,
  LayoutGrid,
  Mail,
  MapPin as PinIcon,
  MapPinned,
  NotebookPen,
  Phone,
  Quote,
  Sparkles,
  Sun,
  TriangleAlert,
  UserRound,
} from 'lucide-react'
import { FitScore, LocationBadges, StatusSelect } from '../components/locationParts'
import { frictionLabel } from '../lib/fit'
import { Eyebrow, Fact, Section, Tabs, type TabItem } from '../components/surfaces'
import { locationPin } from '../components/map/locationPin'
import type { MapPin } from '../components/map/types'
import { VenueMap } from '../components/map/VenueMap'
import { Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { sceneLabel } from '../lib/format'
import { looksLikeEmail } from '../lib/email'
import { formatCoordinates, osmLink, parseCoordinates, roundCoordinates } from '../lib/geo'
import { blankToNull } from '../lib/text'
import { displayHost, safeHttpUrl } from '../lib/url'
import { LogisticsSection } from './LogisticsSection'
import { NotFoundPage } from './NotFoundPage'
import { OutreachSection } from './OutreachSection'
import { VideosSection } from './VideosSection'
import { usePageTitle } from '../lib/usePageTitle'

export function LocationPage() {
  const { locationId = '' } = useParams()
  const { api } = useSession()
  const location = useQuery({ queryKey: queryKeys.location(locationId), queryFn: () => api.locations.get(locationId) })
  usePageTitle(location.data?.name)

  if (location.isPending) return <Spinner label="Loading location" />
  if (location.isError) {
    if (isNotFound(location.error)) return <NotFoundPage />
    return <ErrorAlert error={location.error} onRetry={() => location.refetch()} />
  }
  return <LocationDetails location={location.data} />
}

function LocationDetails({ location }: { location: Location }) {
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [params, setParams] = useSearchParams()
  const update = useUpdateLocation(location)
  // Only for the breadcrumb; the page works without it.
  const scene = useQuery({ queryKey: queryKeys.scene(location.sceneId), queryFn: () => api.scenes.get(location.sceneId) })
  const scenePath = `/scenes/${location.sceneId}`
  const sourceUrl = safeHttpUrl(location.sourceUrl)

  const remove = useMutation({
    mutationFn: () => api.locations.remove(location.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.location(location.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.locationList(location.sceneId) })
      navigate(scenePath, { replace: true })
    },
  })

  const tab = tabOf(params.get('tab'))
  const pin = location.latitude != null && location.longitude != null
  return (
    <div className="space-y-8">
      <Link to={scenePath} className="inline-flex items-center gap-1 text-sm text-stone-400 hover:text-stone-200">
        <ChevronLeft aria-hidden className="size-4" />
        {scene.data ? sceneLabel(scene.data) : 'Scene'}
      </Link>

      <header className="space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div className="flex min-w-0 items-start gap-5">
            {location.fitScore != null && <FitScore score={location.fitScore} size="lg" />}
            <div className="min-w-0 space-y-2">
              <Eyebrow icon={MapPinned}>Location</Eyebrow>
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="gold-leaf font-display text-5xl leading-none sm:text-6xl">{location.name}</h1>
                <LocationBadges location={location} />
              </div>
              {location.address && (
                <p className="flex items-center gap-1.5 text-stone-300">
                  <PinIcon aria-hidden className="size-4 shrink-0 text-amber-400" />
                  {location.address}
                </p>
              )}
              {sourceUrl && (
                <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-sm text-amber-300 underline hover:text-amber-200">
                  {displayHost(sourceUrl)}
                  <ExternalLink aria-hidden className="size-3.5" />
                  <span className="sr-only"> (opens in a new tab)</span>
                </a>
              )}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <StatusSelect location={location} update={update} />
            <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
              Remove
            </Button>
          </div>
        </div>

        <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Fact icon={Gauge} label="Fit">
            {location.fitScore != null ? `${location.fitScore} / 100` : 'Not assessed'}
          </Fact>
          <Fact icon={KeyRound} label="Booking">
            {location.bookingFriction ? frictionLabel(location.bookingFriction) : 'Unknown'}
          </Fact>
          <Fact icon={Crosshair} label="Position">
            {pin ? 'On the map' : 'Not set yet'}
          </Fact>
          <Fact icon={Sun} label="Logistics">
            {location.logistics ? 'Ready' : 'To do'}
          </Fact>
        </dl>
      </header>

      {confirmingDelete && (
        <ConfirmDelete
          title={`Remove “${location.name}”?`}
          confirmLabel="Remove location"
          busy={remove.isPending}
          error={remove.error}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirmingDelete(false)}
        >
          This also deletes its outreach drafts. Scouting again may find it again as a new suggestion.
        </ConfirmDelete>
      )}

      <Tabs
        label="About this location"
        items={tabs}
        selected={tab}
        onSelect={(key) => setParams(key === 'overview' ? {} : { tab: key }, { replace: true })}
      >
        {tab === 'overview' && (
          <div className="grid items-start gap-8 lg:grid-cols-2">
            <div className="space-y-8">
              <Assessment location={location} />
              <Contact location={location} />
              <Notes location={location} update={update} />
            </div>
            <Position location={location} />
          </div>
        )}
        {tab === 'videos' && <VideosSection location={location} />}
        {tab === 'logistics' && <LogisticsSection location={location} />}
        {tab === 'outreach' && <OutreachSection location={location} />}
      </Tabs>
    </div>
  )
}

type TabKey = 'overview' | 'videos' | 'logistics' | 'outreach'

const tabs: TabItem<TabKey>[] = [
  { key: 'overview', label: 'Overview', icon: LayoutGrid },
  { key: 'videos', label: 'Videos', icon: Clapperboard },
  { key: 'logistics', label: 'Logistics', icon: Sun },
  { key: 'outreach', label: 'Outreach', icon: Mail },
]

function tabOf(value: string | null): TabKey {
  return tabs.some((t) => t.key === value) ? (value as TabKey) : 'overview'
}

/** What the AI made of the venue, in the order a producer asks: does it fit, who says yes, what could go wrong. */
function Assessment({ location }: { location: Location }) {
  if (location.fitScore == null) return null
  return (
    <section aria-labelledby="assessment-heading" className="space-y-5 rounded-xl border border-amber-400/15 bg-gradient-to-br from-amber-500/[0.07] via-frame/90 to-reel p-6">
      <div className="space-y-1">
        <Eyebrow icon={Sparkles}>The AI’s read</Eyebrow>
        <h2 id="assessment-heading" className="gold-leaf font-display text-3xl leading-none">
          Assessment
        </h2>
      </div>
      {location.fitReason && (
        <div className="space-y-1">
          <h3 className="text-xs font-semibold tracking-wider text-stone-500 uppercase">Why it fits</h3>
          <p className="text-stone-100">{location.fitReason}</p>
        </div>
      )}
      {location.frictionNote && (
        <div className="space-y-1">
          <h3 className="flex items-center gap-1.5 text-xs font-semibold tracking-wider text-stone-500 uppercase">
            <KeyRound aria-hidden className="size-3.5" />
            Booking
          </h3>
          <p className="text-sm text-stone-300">{location.frictionNote}</p>
        </div>
      )}
      {location.footprintWarnings.length > 0 && (
        <div className="space-y-2">
          <h3 className="text-xs font-semibold tracking-wider text-stone-500 uppercase">Watch out for</h3>
          <ul aria-label="Warnings" className="space-y-1.5">
            {location.footprintWarnings.map((warning) => (
              <li key={warning} className="flex items-start gap-2 rounded-lg bg-amber-500/[0.06] px-3 py-2 text-sm text-amber-100 ring-1 ring-amber-400/15 ring-inset">
                <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-amber-400" />
                {warning}
              </li>
            ))}
          </ul>
        </div>
      )}
      {location.sourceExcerpt && (
        <figure className="space-y-1">
          <figcaption className="text-xs font-semibold tracking-wider text-stone-500 uppercase">From the source</figcaption>
          <blockquote className="flex gap-2 border-l-2 border-amber-400/40 pl-3 text-sm text-stone-300 italic">
            <Quote aria-hidden className="size-4 shrink-0 text-amber-400/50" />
            {location.sourceExcerpt}
          </blockquote>
        </figure>
      )}
    </section>
  )
}

function Notes({ location, update }: { location: Location; update: ReturnType<typeof useUpdateLocation> }) {
  const [notes, setNotes] = useState(location.notes ?? '')
  const changed = (blankToNull(notes) ?? null) !== (location.notes ?? null)

  function save(e: FormEvent) {
    e.preventDefault()
    if (changed) update.mutate({ status: location.status, notes: blankToNull(notes) })
  }

  return (
    <Section titleId="notes-heading" title="Notes" eyebrow="Just for you" icon={NotebookPen}>
      <form onSubmit={save} className="space-y-3" noValidate>
        <ErrorAlert error={update.error} />
        <TextArea
          label="Your notes on this venue"
          maxLength={4000}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          error={fieldErrors(update.error).notes}
          hint="Private to you. Never shared with the venue."
        />
        <div className="flex justify-end">
          <Button type="submit" variant="secondary" busy={update.isPending} disabled={!changed}>
            Save notes
          </Button>
        </div>
      </form>
    </Section>
  )
}

/** Who to talk to at the venue. Shown as links to write or call; edited as a whole. */
function Contact({ location }: { location: Location }) {
  const { api } = useSession()
  const store = useStoreLocation()
  const known = location.contactName != null || location.contactEmail != null || location.contactPhone != null || location.quote != null
  const [editing, setEditing] = useState(false)
  const [values, setValues] = useState({ name: '', email: '', phone: '', quote: '' })
  const save = useMutation({
    mutationFn: () => api.locations.updateContact(location.id, {
        name: blankToNull(values.name),
        email: blankToNull(values.email),
        phone: blankToNull(values.phone),
        quote: blankToNull(values.quote),
      }),
    onSuccess: (updated) => {
      store(updated)
      setEditing(false)
    },
  })
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })
  const email = values.email.trim()
  const emailValid = email === '' || looksLikeEmail(email)
  const phoneValid = /^[0-9+()./ xX-]*$/.test(values.phone)
  const errors = fieldErrors(save.error)

  function edit() {
    setValues({ name: location.contactName ?? '', email: location.contactEmail ?? '', phone: location.contactPhone ?? '', quote: location.quote ?? '' })
    save.reset()
    setEditing(true)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (emailValid && phoneValid) save.mutate()
  }

  return (
    <Section
      titleId="contact-heading"
      title="Contact"
      eyebrow="Who to talk to, and what they ask"
      icon={Contact2}
      actions={
        !editing && (
          <Button variant="secondary" onClick={edit}>
            {known ? 'Edit contact' : 'Add a contact'}
          </Button>
        )
      }
    >
      {editing ? (
        <form onSubmit={submit} className="space-y-3" noValidate>
          <ErrorAlert error={save.error} />
          <TextField label="Contact name" maxLength={200} autoComplete="off" value={values.name} onChange={set('name')} error={errors.name} />
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label="Contact email"
              type="email"
              maxLength={254}
              autoComplete="off"
              value={values.email}
              onChange={set('email')}
              error={emailValid ? errors.email : 'Enter an email address like owner@example.com.'}
            />
            <TextField
              label="Contact phone"
              type="tel"
              maxLength={40}
              autoComplete="off"
              value={values.phone}
              onChange={set('phone')}
              error={phoneValid ? errors.phone : 'Digits, spaces and + ( ) - . only.'}
            />
          </div>
          <TextField
            label="Their quote"
            maxLength={300}
            autoComplete="off"
            value={values.quote}
            onChange={set('quote')}
            error={errors.quote}
            hint="What they ask for the shoot, as they put it: “$425 an hour, four hour minimum”."
          />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" disabled={save.isPending} onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button type="submit" busy={save.isPending} disabled={!emailValid || !phoneValid}>
              Save contact
            </Button>
          </div>
        </form>
      ) : known ? (
        <dl className="grid gap-3 sm:grid-cols-3 lg:grid-cols-1 xl:grid-cols-[1fr_1.4fr_1fr]">
          <Fact icon={UserRound} label="Name">
            {location.contactName ?? '—'}
          </Fact>
          <Fact icon={Mail} label="Email">
            {location.contactEmail ? (
              <a href={`mailto:${location.contactEmail}`} className="break-words text-amber-300 underline hover:text-amber-200">
                {location.contactEmail}
              </a>
            ) : (
              '—'
            )}
          </Fact>
          <Fact icon={Phone} label="Phone">
            {location.contactPhone ? (
              <a href={`tel:${location.contactPhone.replace(/[^0-9+]/g, '')}`} className="text-amber-300 underline hover:text-amber-200">
                {location.contactPhone}
              </a>
            ) : (
              '—'
            )}
          </Fact>
          {location.quote && (
            <div className="sm:col-span-3 lg:col-span-1 xl:col-span-3">
              <Fact icon={Banknote} label="Their quote">
                {location.quote}
              </Fact>
            </div>
          )}
        </dl>
      ) : (
        <p className="text-sm text-stone-400">Nobody yet. Add who to talk to here, and emails to this venue start out addressed to them.</p>
      )}
    </Section>
  )
}

/** Where the venue is: logistics are worked out for this spot. */
function Position({ location }: { location: Location }) {
  const { api } = useSession()
  const store = useStoreLocation()
  const current = location.latitude != null && location.longitude != null ? { latitude: location.latitude, longitude: location.longitude } : null
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState('')
  const parsed = parseCoordinates(text)
  const pin = locationPin(location, { withLink: false })
  // While editing, the pin follows what has been typed or picked, as long as it reads as coordinates.
  const placed = parsed.ok && parsed.value ? parsed.value : current
  const editedPin: MapPin | null = placed && { ...(pin ?? { id: location.id, label: location.name, tone: 'neutral' }), position: placed }

  const relocate = useMutation({
    mutationFn: (body: { latitude: number; longitude: number }) => api.locations.updateCoordinates(location.id, body),
    onSuccess: (updated) => {
      store(updated)
      setEditing(false)
    },
  })

  function save(e: FormEvent) {
    e.preventDefault()
    if (parsed.ok && parsed.value) relocate.mutate(parsed.value)
  }

  const server = fieldErrors(relocate.error)
  return (
    <Section
      titleId="position-heading"
      title="Position"
      eyebrow="On the map"
      icon={Crosshair}
      actions={
        !editing && (
          <Button
            variant="secondary"
            onClick={() => {
              setText(current ? formatCoordinates(current) : '')
              relocate.reset()
              setEditing(true)
            }}
          >
            {current ? 'Move pin' : 'Set coordinates'}
          </Button>
        )
      }
    >

      {editing ? (
        <form onSubmit={save} className="space-y-3 rounded-xl border border-white/[0.07] bg-frame/80 p-5" noValidate>
          <ErrorAlert error={relocate.error} />
          <TextField
            label="Coordinates"
            placeholder="40.6745, -73.9633"
            hint="Latitude, longitude in decimal degrees, as copied from a map. Or click the spot on the map below."
            value={text}
            onChange={(e) => setText(e.target.value)}
            error={(!parsed.ok ? parsed.error : (server.latitude ?? server.longitude)) || undefined}
            autoFocus
          />
          <VenueMap
            pins={editedPin ? [editedPin] : []}
            label="Map: click to place the pin"
            onPick={(spot) => setText(formatCoordinates(roundCoordinates(spot)))}
            className="h-72"
          />
          {location.logistics && (
            <p role="note" className="rounded-md border border-amber-900 bg-amber-950/40 px-4 py-3 text-sm text-amber-200">
              Moving the pin discards the logistics report, which was worked out for the old spot.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setEditing(false)} disabled={relocate.isPending}>
              Cancel
            </Button>
            <Button type="submit" busy={relocate.isPending} disabled={!parsed.ok || !parsed.value}>
              Save position
            </Button>
          </div>
        </form>
      ) : current && pin ? (
        <div className="space-y-2">
          <VenueMap pins={[pin]} label={`Map of ${location.name}`} className="h-80 shadow-xl shadow-black/40" />
          <p className="text-sm text-stone-300">
            {formatCoordinates(current)} ·{' '}
            <a href={osmLink(current)} target="_blank" rel="noopener noreferrer" className="text-amber-300 underline hover:text-amber-200">
              View on OpenStreetMap
              <span className="sr-only"> (opens in a new tab)</span>
            </a>
          </p>
        </div>
      ) : (
        <p className="text-sm text-stone-400">
          Not set. Logistics look the venue up from its {location.address ? 'address' : 'name'}; set the coordinates if that finds the wrong place.
        </p>
      )}
    </Section>
  )
}
