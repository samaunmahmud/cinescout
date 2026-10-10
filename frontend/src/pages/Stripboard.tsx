import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useId, useState, type DragEvent, type ReactNode } from 'react'
import { CalendarOff, CalendarPlus, GripVertical, MapPin, Users } from 'lucide-react'
import { Link } from 'react-router'
import { queryKeys } from '../api/queryKeys'
import type { Schedule, ScheduledScene } from '../api/types'
import { useSession } from '../auth/context'
import { useCanEdit } from '../components/projectRole'
import { ErrorAlert } from '../components/ui'
import { formatDay, shortDay } from '../lib/format'
import { movedTo, nextDay, pagesLabel, pagesText, stripColour, stripKind, stripLabel } from '../lib/stripboard'

const SCENE_TYPE = 'application/x-cinescout-scene'
const OFF_BOARD = 'off'

/**
 * The schedule as a production stripboard: a coloured strip per scene (white interior day, yellow exterior day, blue
 * interior night, green exterior night), the shoot days as day breaks. Strips are dragged to another day, a new day at
 * the end, or off the board; the "Move to" select on each strip does the same from the keyboard or on a phone.
 */
export function Stripboard({ projectId, schedule }: { projectId: string; schedule: Schedule }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const [over, setOver] = useState<string | null>(null)
  const scenes = new Map([...schedule.days.flatMap((day) => day.scenes), ...schedule.unscheduled].map((scene) => [scene.id, scene]))
  const newDay = schedule.days.length > 0 ? nextDay(schedule.days[schedule.days.length - 1].date) : null
  const targets = [
    ...schedule.days.map((day, i) => ({ value: day.date, label: `Day ${i + 1} · ${shortDay(day.date)}` })),
    ...(newDay ? [{ value: newDay, label: `New day · ${shortDay(newDay)}` }] : []),
    { value: OFF_BOARD, label: 'Not scheduled' },
  ]

  const move = useMutation({
    mutationFn: ({ scene, day }: { scene: ScheduledScene; day: string | null }) => api.scenes.reschedule(scene.id, movedTo(scene, day)),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.scene(updated.id), updated)
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(projectId) })
      return queryClient.invalidateQueries({ queryKey: queryKeys.projectSchedule(projectId) })
    },
  })

  function moveTo(sceneId: string, target: string) {
    const scene = scenes.get(sceneId)
    if (!scene) return
    const day = target === OFF_BOARD ? null : target
    if (day === scene.shootDateStart) return
    move.mutate({ scene, day })
  }

  // A drop zone for each day, the new day and the bin; only strips of this board are taken.
  function zone(target: string) {
    if (!canEdit) return {}
    return {
      onDragOver: (event: DragEvent) => {
        if (!event.dataTransfer.types.includes(SCENE_TYPE)) return
        event.preventDefault()
        event.dataTransfer.dropEffect = 'move'
        setOver(target)
      },
      onDragLeave: (event: DragEvent) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setOver((current) => (current === target ? null : current))
      },
      onDrop: (event: DragEvent) => {
        event.preventDefault()
        setOver(null)
        const id = event.dataTransfer.getData(SCENE_TYPE)
        if (id) moveTo(id, target)
      },
    }
  }

  const strip = (scene: ScheduledScene, at: string) => (
    <Strip
      key={scene.id}
      scene={scene}
      at={at}
      targets={targets}
      canEdit={canEdit}
      moving={move.isPending && move.variables?.scene.id === scene.id}
      onMove={(target) => moveTo(scene.id, target)}
    />
  )

  return (
    <div className="space-y-3">
      <Legend />
      {canEdit && (
        <p className="text-sm text-muted">Drag a strip to another day, or use its Move to list. A scene over several days keeps its length.</p>
      )}
      <ErrorAlert error={move.error} />
      {move.isPending && (
        <p role="status" className="sr-only">
          Moving {move.variables?.scene.title}
        </p>
      )}
      <div className="overflow-hidden rounded-lg border border-line bg-paper">
        {schedule.days.map((day, i) => (
          <DayBlock key={day.date} id={`strip-day-${day.date}`} highlighted={over === day.date} zone={zone(day.date)}
            title={`Day ${i + 1}`} detail={`${formatDay(day.date)} · ${count(day.scenes)}`}>
            {day.scenes.map((scene) => strip(scene, day.date))}
          </DayBlock>
        ))}
        {canEdit && newDay && (
          <div
            {...zone(newDay)}
            className={`flex items-center gap-2 border-b border-dashed border-line px-4 py-3 text-sm text-muted transition-colors ${over === newDay ? 'bg-cue-wash text-cue-ink' : ''}`}
          >
            <CalendarPlus aria-hidden className="size-4" />
            Drop here for a new day, {formatDay(newDay)}
          </div>
        )}
        <DayBlock id="strip-day-unscheduled" highlighted={over === OFF_BOARD} zone={zone(OFF_BOARD)} title="Not scheduled"
          detail={schedule.unscheduled.length > 0 ? count(schedule.unscheduled) : 'Drop a strip here to take its dates off'} muted>
          {schedule.unscheduled.map((scene) => strip(scene, OFF_BOARD))}
        </DayBlock>
      </div>
    </div>
  )
}

/** "2 scenes · 1 3/8 pages": what a day of the board holds. */
function count(scenes: ScheduledScene[]) {
  const eighths = scenes.reduce((sum, scene) => sum + scene.pageEighths, 0)
  return `${scenes.length === 1 ? '1 scene' : `${scenes.length} scenes`} · ${pagesLabel(eighths)}`
}

function DayBlock({
  id,
  title,
  detail,
  highlighted,
  zone,
  muted = false,
  children,
}: {
  id: string
  title: string
  detail: string
  highlighted: boolean
  zone: object
  muted?: boolean
  children: ReactNode
}) {
  const Icon = muted ? CalendarOff : null
  return (
    <section aria-labelledby={id} {...zone} className={`transition-colors ${highlighted ? 'bg-cue-wash ring-2 ring-cue ring-inset' : ''}`}>
      <h3 id={id} className={`flex items-baseline gap-3 px-4 py-2 text-sm ${muted ? 'border-y border-line bg-tape text-muted' : 'bg-night text-white'}`}>
        {Icon && <Icon aria-hidden className="size-4 self-center" />}
        <span className="font-mono font-semibold tracking-wider uppercase">{title}</span>
        <span className={`text-xs ${muted ? '' : 'text-fog'}`}>{detail}</span>
      </h3>
      {/* An empty day still needs room to drop onto. */}
      <ul className="min-h-3 divide-y divide-line">{children}</ul>
    </section>
  )
}

function Strip({
  scene,
  at,
  targets,
  canEdit,
  moving,
  onMove,
}: {
  scene: ScheduledScene
  at: string
  targets: { value: string; label: string }[]
  canEdit: boolean
  moving: boolean
  onMove: (target: string) => void
}) {
  const selectId = useId()
  const [dragging, setDragging] = useState(false)
  const kind = stripKind(scene)
  const label = stripLabel(kind)
  const venue = scene.venues[0]
  const days = spanDays(scene)
  return (
    <li
      draggable={canEdit && !moving}
      onDragStart={(event) => {
        event.dataTransfer.setData(SCENE_TYPE, scene.id)
        event.dataTransfer.setData('text/plain', scene.title)
        event.dataTransfer.effectAllowed = 'move'
        setDragging(true)
      }}
      onDragEnd={() => setDragging(false)}
      aria-busy={moving || undefined}
      className={`flex flex-wrap items-center gap-x-4 gap-y-1.5 px-3 py-2 text-ink sm:flex-nowrap ${stripColour(kind)} ${dragging || moving ? 'opacity-50' : ''} ${canEdit ? 'cursor-grab active:cursor-grabbing' : ''}`}
    >
      {canEdit && <GripVertical aria-hidden className="hidden size-4 shrink-0 text-subtle sm:block" />}
      <span className="w-10 shrink-0 font-mono text-sm font-bold">{scene.sceneNumber ?? '–'}</span>
      <span className="w-12 shrink-0 font-mono text-xs text-graphite" title={pagesLabel(scene.pageEighths)}>
        {pagesText(scene.pageEighths)}
        <span className="sr-only"> {scene.pageEighths > 8 ? 'pages' : 'page'}</span>
      </span>
      <span className="w-24 shrink-0 font-mono text-xs font-semibold tracking-wide text-graphite uppercase">{label ?? 'Not read yet'}</span>
      <span className="min-w-0 flex-1 basis-48">
        <Link to={`/scenes/${scene.id}`} className="block truncate font-semibold hover:underline" draggable={false}>
          {scene.title}
        </Link>
        <span className="flex flex-wrap gap-x-3 text-xs text-muted">
          <span className={`inline-flex items-center gap-1 ${venue ? 'text-go-ink' : ''}`}>
            <MapPin aria-hidden className="size-3" />
            {venue ? venue.name : 'No confirmed location'}
          </span>
          {scene.characters.length > 0 && (
            <span className="inline-flex items-center gap-1" title={scene.characters.join(', ')}>
              <Users aria-hidden className="size-3" />
              {scene.characters.length === 1 ? scene.characters[0] : `${scene.characters.length} cast`}
            </span>
          )}
          {days > 1 && <span className="font-semibold text-graphite">{days} days</span>}
        </span>
      </span>
      {canEdit && (
        <span className="flex shrink-0 items-center gap-2">
          <label htmlFor={selectId} className="text-xs text-muted">
            Move to<span className="sr-only"> for {scene.title}</span>
          </label>
          <select
            id={selectId}
            value={at}
            disabled={moving}
            onChange={(e) => onMove(e.target.value)}
            className="rounded-md border border-line bg-paper px-2 py-1 text-xs font-semibold text-ink focus:ring-4 focus:ring-cue/25 focus:outline-none"
          >
            {targets.map((target) => (
              <option key={target.value} value={target.value}>
                {target.label}
              </option>
            ))}
          </select>
        </span>
      )}
    </li>
  )
}

function spanDays(scene: ScheduledScene): number {
  if (!scene.shootDateStart || !scene.shootDateEnd) return 1
  return Math.round((Date.parse(scene.shootDateEnd) - Date.parse(scene.shootDateStart)) / 86_400_000) + 1
}

/** What the strip colours mean. */
function Legend() {
  const items: [string, string][] = [
    ['bg-strip-int-day', 'Interior day'],
    ['bg-strip-ext-day', 'Exterior day'],
    ['bg-strip-int-night', 'Interior night'],
    ['bg-strip-ext-night', 'Exterior night'],
  ]
  return (
    <ul aria-label="Strip colours" className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
      {items.map(([colour, text]) => (
        <li key={text} className="inline-flex items-center gap-1.5">
          <span aria-hidden className={`size-3 rounded-sm ring-1 ring-line ${colour}`} />
          {text}
        </li>
      ))}
    </ul>
  )
}
