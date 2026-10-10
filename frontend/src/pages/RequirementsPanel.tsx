import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { AcousticSensitivity, Scene, SceneRequirements } from '../api/types'
import { useSession } from '../auth/context'
import { Lightbulb, MapPin, Mic, Palette, Sparkles, SunMedium, Users, type LucideIcon } from 'lucide-react'
import { Eyebrow } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'
import { useCanEdit } from '../components/projectRole'

const sensitivity: Record<AcousticSensitivity, string> = {
  LOW: 'Low: background noise is fine',
  MEDIUM: 'Medium: some dialogue to record',
  HIGH: 'High: needs a quiet location',
}

/** The requirements the AI extracted from the script, and the button that (re-)extracts them. */
export function RequirementsPanel({ scene }: { scene: Scene }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()

  const parse = useMutation({
    mutationFn: () => api.scenes.parse(scene.id),
    onSuccess: (parsed) => {
      queryClient.setQueryData(queryKeys.scene(scene.id), parsed)
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(scene.projectId) })
    },
    // An unusable answer marks the scene FAILED on the server, so fetch it again to show that.
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.scene(scene.id) }),
  })

  const { requirements, parseStatus } = scene
  const action = (
    <Button variant={requirements ? 'secondary' : 'primary'} busy={parse.isPending} onClick={() => parse.mutate()}>
      {requirements ? 'Analyse again' : parseStatus === 'FAILED' ? 'Try again' : 'Analyse script'}
    </Button>
  )

  return (
    <section aria-labelledby="requirements-heading" className="board-card relative space-y-5 rounded-lg bg-paper p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <Eyebrow icon={Sparkles}>Read by the AI</Eyebrow>
          <h2 id="requirements-heading" className="font-display text-3xl leading-none font-bold">
            Location requirements
          </h2>
        </div>
        {canEdit && action}
      </div>

      {parse.isPending ? (
        <Spinner label="Reading the script. This can take up to a minute." />
      ) : (
        <ErrorAlert error={parse.error} />
      )}

      {requirements ? (
        <RequirementsList requirements={requirements} />
      ) : (
        !parse.isPending && (
          <p className="text-sm text-muted">
            {parseStatus === 'FAILED'
              ? 'The last analysis did not produce usable requirements. Try again, or make the script clearer about where the scene takes place.'
              : 'Not analysed yet. The AI reads the script and works out the setting, mood, lighting, time of day and how quiet the location must be.'}
          </p>
        )
      )}
    </section>
  )
}

function RequirementsList({ requirements: r }: { requirements: SceneRequirements }) {
  const rows: [string, LucideIcon, ReactNode][] = [
    ['Setting', MapPin, r.settingType],
    ['Time of day', SunMedium, r.timeOfDay],
    ['Visual mood', Palette, r.visualMood],
    ['Lighting', Lightbulb, r.lightingNeeds],
    ['Sound', Mic, r.acousticSensitivity && sensitivity[r.acousticSensitivity]],
    ['Cast and crew', Users, r.estimatedCastAndCrewSize != null && `About ${r.estimatedCastAndCrewSize} people`],
  ]
  // A spec sheet: what the venue hunt turns on, one tile each; what the script does not say stays quiet.
  return (
    <dl className="grid gap-3 pt-1 sm:grid-cols-2 xl:grid-cols-3">
      {rows.map(([label, Icon, value]) => (
        <div
          key={label}
          className={`flex items-start gap-3 rounded-lg px-3.5 py-3 ${value ? 'bg-ground ring-1 ring-line' : 'border border-dashed border-line'}`}
        >
          <span
            aria-hidden
            className={`grid size-8 shrink-0 place-items-center rounded-md ${value ? 'bg-paper text-cue-ink ring-1 ring-line' : 'text-subtle'}`}
          >
            <Icon className="size-4" />
          </span>
          <div className="min-w-0">
            <dt className="text-[11px] font-semibold tracking-wider text-muted uppercase">{label}</dt>
            <dd className={`text-sm leading-snug break-words ${value ? 'font-semibold text-ink first-letter:uppercase' : 'text-subtle'}`}>
              {value || 'Not in the script'}
            </dd>
          </div>
        </div>
      ))}
    </dl>
  )
}
