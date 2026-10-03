import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { fieldErrors, isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Location } from '../api/types'
import { useSession } from '../auth/context'
import { ConfirmDelete } from '../components/ConfirmDelete'
import { DirectorsCall } from '../components/DirectorsCall'
import { SaveToLibrary } from '../components/SaveToLibrary'
import { useStoreLocation, useUpdateLocation } from '../components/locationHooks'
import {
  Banknote,
  ChevronLeft,
  Clapperboard,
  ClipboardCheck,
  Contact as Contact2,
  Crosshair,
  ExternalLink,
  Gauge,
  Gavel,
  KeyRound,
  LayoutGrid,
  Mail,
  MapPin as PinIcon,
  MapPinned,
  MessagesSquare,
  NotebookPen,
  Phone,
  Quote,
  Sparkles,
  Sun,
  TriangleAlert,
  UserRound,
} from 'lucide-react'
import { FitLabel, FitScore, LocationBadges, StatusSelect } from '../components/locationParts'
import { Stamp } from '../components/stickers'
import { frictionLabel } from '../lib/fit'
import { Eyebrow, Fact, Section, Tabs, type TabItem } from '../components/surfaces'
import { locationPin } from '../components/map/locationPin'
import type { MapPin } from '../components/map/types'
import { VenueMap } from '../components/map/VenueMap'
import { VenuePicture } from '../components/VenuePicture'
import { Button, ErrorAlert, Spinner, TextArea, TextField } from '../components/ui'
import { sceneLabel } from '../lib/format'
import { looksLikeEmail } from '../lib/email'
import { formatCoordinates, osmLink, parseCoordinates, roundCoordinates } from '../lib/geo'
import { blankToNull } from '../lib/text'
import { displayHost, safeHttpUrl } from '../lib/url'
import { CommentsSection } from './CommentsSection'
import { LogisticsSection } from './LogisticsSection'
import { NotFoundPage } from './NotFoundPage'
import { OutreachSection } from './OutreachSection'
import { PhotosSection } from './PhotosSection'
import { RecceSection } from './RecceSection'
import { VideosSection } from './VideosSection'
import { usePageTitle } from '../lib/usePageTitle'
import { ProjectRoleProvider } from '../components/ProjectRoleProvider'
import { useCanEdit, useProjectRole } from '../components/projectRole'

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
  const store = useStoreLocation()
  // The venue's picture is looked up the first time its page is opened, once, in the background.
  const lookUp = location.imageCheckedAt === null && location.sourceUrl !== null
  const image = useMutation({ mutationFn: () => api.locations.lookUpImage(location.id), onSuccess: store })
  const { mutate: lookUpImage } = image
  useEffect(() => {
    if (lookUp) lookUpImage()
  }, [lookUp, lookUpImage])
  const navigate = useNavigate()
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [params, setParams] = useSearchParams()
  const update = useUpdateLocation(location)
  // Only for the breadcrumb; the page works without it.
  const scene = useQuery({ queryKey: queryKeys.scene(location.sceneId), queryFn: () => api.scenes.get(location.sceneId) })
  const scenePath = `/scenes/${location.sceneId}`
  const sourceUrl = safeHttpUrl(location.sourceUrl)
  const role = useProjectRole(scene.data?.projectId)
  const canEdit = role !== 'VIEWER'

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
    <ProjectRoleProvider role={role}>
    <div className="space-y-8">
      <Link to={scenePath} className="inline-flex items-center gap-1 font-script text-sm font-bold tracking-[0.06em] text-muted uppercase hover:text-ink">
        <ChevronLeft aria-hidden className="size-4" />
        {scene.data ? sceneLabel(scene.data) : 'Scene'}
      </Link>

      <header className="space-y-6">
        {/* The venue through the camera: its picture in the frame lines, its name over the foot of the shot. */}
        <div className="relative min-h-72 overflow-hidden rounded-lg border-2 border-ink bg-[#1b222b] text-white sm:min-h-80">
          {location.imageUrl && (
            <div aria-hidden className="absolute inset-0">
              <VenuePicture src={location.imageUrl} className="h-full w-full opacity-60" />
            </div>
          )}
          <span aria-hidden className="vf-corner top-4 left-4 border-t-[3px] border-l-[3px]" />
          <span aria-hidden className="vf-corner top-4 right-4 border-t-[3px] border-r-[3px]" />
          <span aria-hidden className="vf-corner bottom-4 left-4 border-b-[3px] border-l-[3px]" />
          <span aria-hidden className="vf-corner right-4 bottom-4 border-r-[3px] border-b-[3px]" />
          <div aria-hidden className="absolute top-6 right-16 left-16 flex justify-between font-script text-xs font-bold tracking-[0.08em] [text-shadow:0_1px_2px_rgb(0_0_0/0.6)]">
            <span className="flex items-center gap-2">
              <span className="size-2.5 rounded-full bg-stop" />
              REC
            </span>
            <span className="hidden sm:inline">24 FPS · 2.39:1</span>
          </div>
          <div className="relative flex min-h-72 flex-col justify-end gap-2 bg-gradient-to-t from-ink/85 to-transparent p-8 sm:min-h-80">
            <Eyebrow onDark icon={MapPinned}>Location</Eyebrow>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="font-display text-4xl leading-none font-extrabold sm:text-6xl">{location.name}</h1>
              <LocationBadges location={location} />
            </div>
            {location.address && (
              <p className="flex items-center gap-1.5 font-script text-fog">
                <PinIcon aria-hidden className="size-4 shrink-0 text-cue" />
                {location.address}
              </p>
            )}
            {sourceUrl && (
              <a href={sourceUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 self-start text-sm font-semibold text-cue underline hover:text-white">
                {displayHost(sourceUrl)}
                <ExternalLink aria-hidden className="size-3.5" />
                <span className="sr-only"> (opens in a new tab)</span>
              </a>
            )}
          </div>
          {location.status === 'SHORTLISTED' && <Stamp className="absolute top-12 right-6 text-sm !text-cue !bg-transparent sm:top-16 sm:right-8 sm:text-lg">Shortlisted</Stamp>}
          {location.status === 'CONFIRMED' && <Stamp className="absolute top-12 right-6 text-sm !text-go !bg-transparent sm:top-16 sm:right-8 sm:text-lg">Locked</Stamp>}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-4">
            {location.fitScore != null && (
              <>
                <FitScore score={location.fitScore} size="lg" />
                <FitLabel score={location.fitScore} className="text-sm" />
              </>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <StatusSelect location={location} update={update} />
            {location.status === 'REJECTED' && location.rejectionReason && (
              <span className="font-marker text-[15px] text-stop-ink">Passed: {location.rejectionReason}</span>
            )}
            <SaveToLibrary locationId={location.id} />
            {canEdit && (
              <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>
                Remove
              </Button>
            )}
          </div>
        </div>

        <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
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
              <DirectorsCallSection location={location} />
              <Contact location={location} />
              <Notes location={location} update={update} />
            </div>
            <Position location={location} />
            <div className="lg:col-span-2">
              <PhotosSection location={location} />
            </div>
          </div>
        )}
        {tab === 'recce' && <RecceSection location={location} />}
        {tab === 'comments' && <CommentsSection location={location} projectId={scene.data?.projectId} />}
        {tab === 'videos' && <VideosSection location={location} />}
        {tab === 'logistics' && <LogisticsSection location={location} />}
        {tab === 'outreach' && <OutreachSection location={location} />}
      </Tabs>
    </div>
    </ProjectRoleProvider>
  )
}

type TabKey = 'overview' | 'comments' | 'recce' | 'videos' | 'logistics' | 'outreach'

const tabs: TabItem<TabKey>[] = [
  { key: 'overview', label: 'Overview', icon: LayoutGrid },
  { key: 'comments', label: 'Comments', icon: MessagesSquare },
  { key: 'recce', label: 'Recce', icon: ClipboardCheck },
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
    // A scout's report on ruled paper, the score circled in marker.
    <section aria-labelledby="assessment-heading" className="lined-paper relative space-y-5 rounded-md border-2 border-ink py-6 pr-6 pl-14">
      <span aria-hidden className="absolute inset-y-0 left-10 w-0.5 bg-[#f4b4b4]" />
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <Eyebrow icon={Sparkles}>The AI’s read</Eyebrow>
          <h2 id="assessment-heading" className="font-display text-3xl leading-none font-extrabold">
            Scout’s report
          </h2>
        </div>
        <span aria-hidden className="relative flex h-16 w-20 shrink-0 items-center justify-center">
          <span className="absolute inset-0 -rotate-6 rounded-[50%] border-[3px] border-go-mid" />
          <span className="font-marker text-4xl text-go-ink">{location.fitScore}</span>
        </span>
      </div>
      {location.fitReason && (
        <div className="space-y-1">
          <h3 className="font-script text-xs font-bold tracking-[0.1em] text-muted uppercase">Why it fits</h3>
          <p className="text-[15px] leading-relaxed text-ink">{location.fitReason}</p>
        </div>
      )}
      {location.frictionNote && (
        <div className="space-y-1">
          <h3 className="flex items-center gap-1.5 font-script text-xs font-bold tracking-[0.1em] text-muted uppercase">
            <KeyRound aria-hidden className="size-3.5" />
            Booking
          </h3>
          <p className="text-sm text-graphite">{location.frictionNote}</p>
        </div>
      )}
      {location.footprintWarnings.length > 0 && (
        <div className="space-y-2">
          <h3 className="font-script text-xs font-bold tracking-[0.1em] text-muted uppercase">Watch out for</h3>
          <ul aria-label="Warnings" className="flex flex-wrap gap-2">
            {location.footprintWarnings.map((warning, i) => (
              <li key={warning} style={{ transform: `rotate(${i % 2 ? 0.8 : -0.8}deg)` }} className="tape flex items-center gap-1.5 !bg-highlight px-3 py-1 text-sm font-semibold">
                <TriangleAlert aria-hidden className="size-4 shrink-0" />
                {warning}
              </li>
            ))}
          </ul>
        </div>
      )}
      {location.sourceExcerpt && (
        <figure className="space-y-1">
          <figcaption className="font-script text-xs font-bold tracking-[0.1em] text-muted uppercase">From the source</figcaption>
          <blockquote className="flex gap-2 border-l-2 border-cue pl-3 text-sm text-graphite italic">
            <Quote aria-hidden className="size-4 shrink-0 text-cue-ink" />
            {location.sourceExcerpt}
          </blockquote>
        </figure>
      )}
    </section>
  )
}

/** What the director said through a director link; nothing until someone has answered. */
function DirectorsCallSection({ location }: { location: Location }) {
  const { api } = useSession()
  const calls = useQuery({
    queryKey: queryKeys.directorCalls('location', location.id),
    queryFn: () => api.director.forLocation(location.id),
  })
  if (!calls.data || calls.data.items.length === 0) return null
  return (
    <Section titleId="directors-call-heading" title="Director’s call" eyebrow="From the director link" icon={Gavel}>
      <DirectorsCall calls={calls.data.items} />
    </Section>
  )
}

function Notes({ location, update }: { location: Location; update: ReturnType<typeof useUpdateLocation> }) {
  const canEdit = useCanEdit()
  const [notes, setNotes] = useState(location.notes ?? '')
  const changed = (blankToNull(notes) ?? null) !== (location.notes ?? null)

  function save(e: FormEvent) {
    e.preventDefault()
    if (changed) update.mutate({ status: location.status, notes: blankToNull(notes) })
  }

  return (
    <Section titleId="notes-heading" title="Notes" eyebrow="For the crew" icon={NotebookPen}>
      {!canEdit ? (
        <p className="text-[15px] whitespace-pre-line text-graphite">{location.notes ?? 'No notes yet.'}</p>
      ) : (
      <form onSubmit={save} className="space-y-3" noValidate>
        <ErrorAlert error={update.error} />
        <TextArea
          label="Your notes on this venue"
          maxLength={4000}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          error={fieldErrors(update.error).notes}
          hint="Shared with the project's crew. Never with the venue."
        />
        <div className="flex justify-end">
          <Button type="submit" variant="secondary" busy={update.isPending} disabled={!changed}>
            Save notes
          </Button>
        </div>
      </form>
      )}
    </Section>
  )
}

/** Who to talk to at the venue. Shown as links to write or call; edited as a whole. */
function Contact({ location }: { location: Location }) {
  const canEdit = useCanEdit()
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
        canEdit &&
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
              <a href={`mailto:${location.contactEmail}`} className="break-words text-cue-ink underline hover:text-cue-deep">
                {location.contactEmail}
              </a>
            ) : (
              '—'
            )}
          </Fact>
          <Fact icon={Phone} label="Phone">
            {location.contactPhone ? (
              <a href={`tel:${location.contactPhone.replace(/[^0-9+]/g, '')}`} className="text-cue-ink underline hover:text-cue-deep">
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
        <p className="text-sm text-muted">Nobody yet. Add who to talk to here, and emails to this venue start out addressed to them.</p>
      )}
    </Section>
  )
}

/** Where the venue is: logistics are worked out for this spot. */
function Position({ location }: { location: Location }) {
  const canEdit = useCanEdit()
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
        canEdit &&
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
        <form onSubmit={save} className="space-y-3 board-card rounded-lg bg-white p-5" noValidate>
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
            <p role="note" className="rounded-md border border-cue bg-cue-wash px-4 py-3 text-sm text-cue-ink">
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
          <VenueMap pins={[pin]} label={`Map of ${location.name}`} className="h-80" />
          <p className="text-sm text-graphite">
            {formatCoordinates(current)} ·{' '}
            <a href={osmLink(current)} target="_blank" rel="noopener noreferrer" className="text-cue-ink underline hover:text-cue-deep">
              View on OpenStreetMap
              <span className="sr-only"> (opens in a new tab)</span>
            </a>
          </p>
        </div>
      ) : (
        <p className="text-sm text-muted">
          Not set. Logistics look the venue up from its {location.address ? 'address' : 'name'}; set the coordinates if that finds the wrong place.
        </p>
      )}
    </Section>
  )
}
