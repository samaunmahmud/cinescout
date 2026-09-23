import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { AcousticSensitivity, Scene, SceneRequirements } from '../api/types'
import { useSession } from '../auth/context'
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
    <section aria-labelledby="requirements-heading" className="space-y-4 rounded-lg border border-stone-800 bg-stone-900/60 p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 id="requirements-heading" className="text-lg font-semibold">
          Location requirements
        </h2>
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
  const rows: [string, ReactNode][] = [
    ['Setting', r.settingType],
    ['Time of day', r.timeOfDay],
    ['Visual mood', r.visualMood],
    ['Lighting', r.lightingNeeds],
    ['Sound', r.acousticSensitivity && sensitivity[r.acousticSensitivity]],
    ['Cast and crew', r.estimatedCastAndCrewSize != null && `About ${r.estimatedCastAndCrewSize} people`],
  ]
  return (
    <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt className="text-xs font-medium tracking-wide text-stone-500 uppercase">{label}</dt>
          <dd className="mt-1 text-stone-100">{value || <span className="text-stone-500">Not specified</span>}</dd>
        </div>
      ))}
    </dl>
  )
}
