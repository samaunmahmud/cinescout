import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { AcousticSensitivity, Scene, SceneRequirements } from '../api/types'
import { useSession } from '../auth/context'
import { Building2, Clock, Lightbulb, Palette, Sparkles, Users, Volume2, type LucideIcon } from 'lucide-react'
import { Eyebrow, Fact } from '../components/surfaces'
import { Button, ErrorAlert, Spinner } from '../components/ui'

const sensitivity: Record<AcousticSensitivity, string> = {
  LOW: 'Low: background noise is fine',
  MEDIUM: 'Medium: some dialogue to record',
  HIGH: 'High: needs a quiet location',
}

/** The requirements the AI extracted from the script, and the button that (re-)extracts them. */
export function RequirementsPanel({ scene }: { scene: Scene }) {
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
    <section aria-labelledby="requirements-heading" className="relative space-y-5 overflow-hidden rounded-xl border border-amber-400/15 bg-gradient-to-br from-amber-500/[0.07] via-frame/90 to-reel p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <Eyebrow icon={Sparkles}>Read by the AI</Eyebrow>
          <h2 id="requirements-heading" className="font-display text-3xl leading-none">
            Location requirements
          </h2>
        </div>
        {action}
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
          <p className="text-sm text-stone-400">
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
    ['Setting', Building2, r.settingType],
    ['Time of day', Clock, r.timeOfDay],
    ['Visual mood', Palette, r.visualMood],
    ['Lighting', Lightbulb, r.lightingNeeds],
    ['Sound', Volume2, r.acousticSensitivity && sensitivity[r.acousticSensitivity]],
    ['Cast and crew', Users, r.estimatedCastAndCrewSize != null && `About ${r.estimatedCastAndCrewSize} people`],
  ]
  return (
    <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      {rows.map(([label, icon, value]) => (
        <Fact key={label} icon={icon} label={label}>
          {value || <span className="text-stone-500">Not specified</span>}
        </Fact>
      ))}
    </dl>
  )
}
