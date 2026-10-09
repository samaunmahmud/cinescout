import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { AcousticSensitivity, Scene, SceneRequirements } from '../api/types'
import { useSession } from '../auth/context'
import { Sparkles } from 'lucide-react'
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
  const rows: [string, ReactNode][] = [
    ['Setting', r.settingType],
    ['Time of day', r.timeOfDay],
    ['Visual mood', r.visualMood],
    ['Lighting', r.lightingNeeds],
    ['Sound', r.acousticSensitivity && sensitivity[r.acousticSensitivity]],
    ['Cast and crew', r.estimatedCastAndCrewSize != null && `About ${r.estimatedCastAndCrewSize} people`],
  ]
  // Each requirement on its own strip of tape, as a scout would label the board; the ones the venue hunt turns on
  // (the light, the sound) in the board's colours.
  const tapes: Record<string, string> = { Lighting: 'bg-cue', Sound: 'bg-go' }
  return (
    <dl className="flex flex-wrap gap-x-3 gap-y-4 pt-1">
      {rows.map(([label, value], i) => (
        <div
          key={label}
          style={{ transform: `rotate(${[-1.5, 1.2, -0.8, 1.5, -1.2, 0.8][i]}deg)` }}
          className={`tape flex flex-col px-4 pt-1.5 pb-1 ${tapes[label] ?? ''}`}
        >
          <dt className="font-script text-[11px] font-bold tracking-[0.1em] uppercase">{label}</dt>
          <dd className="font-marker text-[17px] leading-snug">{value || <span className="opacity-60">Not specified</span>}</dd>
        </div>
      ))}
    </dl>
  )
}
