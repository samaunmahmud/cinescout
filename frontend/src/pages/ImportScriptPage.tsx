import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, FileText, ScissorsLineDashed } from 'lucide-react'
import { useId, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { fieldErrors, isNotFound } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { ScriptImport } from '../api/types'
import { useSession } from '../auth/context'
import { EmptyState, Slate } from '../components/surfaces'
import { Button, ErrorAlert, Spinner, TextArea } from '../components/ui'
import { NotFoundPage } from './NotFoundPage'
import { usePageTitle } from '../lib/usePageTitle'

/** As the server limits it; a feature-length screenplay is well under. */
const MAX_SCRIPT_LENGTH = 500_000

/**
 * Adds a whole screenplay to a project: paste it (or pick a text file), check the scenes it is cut into, then
 * import them. The cut is previewed by the server, so what is shown is exactly what an import adds.
 */
export function ImportScriptPage() {
  const { projectId = '' } = useParams()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const fileId = useId()
  const [script, setScript] = useState('')
  const [fileError, setFileError] = useState<string | null>(null)
  const project = useQuery({ queryKey: queryKeys.project(projectId), queryFn: () => api.projects.get(projectId) })
  usePageTitle('Import script')

  const preview = useMutation({ mutationFn: (text: string) => api.scenes.previewImport(projectId, text) })
  const importScript = useMutation({
    mutationFn: (text: string) => api.scenes.importScript(projectId, text),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.sceneList(projectId) })
      navigate(`/projects/${projectId}`, { replace: true })
    },
  })

  if (project.isPending) return <Spinner label="Loading project" />
  if (project.isError) {
    if (isNotFound(project.error)) return <NotFoundPage />
    return <ErrorAlert error={project.error} onRetry={() => project.refetch()} />
  }

  // A preview describes the text it was made from: once the text changes it no longer holds.
  function change(text: string) {
    setScript(text)
    preview.reset()
    importScript.reset()
  }

  async function chooseFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setFileError(null)
    try {
      change(await file.text())
    } catch {
      setFileError('That file could not be read. Paste the script instead.')
    }
  }

  const tooLong = script.length > MAX_SCRIPT_LENGTH
  const error = preview.error ?? importScript.error
  const found = preview.data
  const canSubmit = script.trim() !== '' && !tooLong

  function submit(e: FormEvent) {
    e.preventDefault()
    if (canSubmit) preview.mutate(script)
  }

  return (
    <div className="space-y-6">
      <Link to={`/projects/${projectId}`} className="inline-flex items-center gap-1 font-script text-sm font-bold tracking-[0.06em] text-muted uppercase hover:text-ink">
        <ChevronLeft aria-hidden className="size-4" />
        {project.data.title}
      </Link>
      <section aria-labelledby="import-script" className="mx-auto max-w-3xl space-y-6 board-card rounded-lg bg-white p-6 sm:p-8">
        <div className="space-y-2">
          <h1 id="import-script" className="font-bold font-display text-5xl leading-none">
            Import script
          </h1>
          <p className="max-w-prose text-sm text-muted">
            Paste a screenplay and it is cut into scenes at its scene headings, the lines that start with INT. or EXT. Each scene is added to
            the project, ready to be analysed and scouted.
          </p>
        </div>

        <form onSubmit={submit} className="space-y-4" noValidate>
          <ErrorAlert error={error} />
          <TextArea
            label="Script"
            required
            rows={14}
            className="font-mono"
            value={script}
            onChange={(e) => change(e.target.value)}
            error={tooLong ? `That is ${script.length.toLocaleString()} characters; a script can have ${MAX_SCRIPT_LENGTH.toLocaleString()} at most.` : fieldErrors(error).script}
            placeholder={'INT. DINER - NIGHT\n\nRain on the windows. Two detectives talk in low voices.\n\nEXT. ROOFTOP - DAWN\n\nThe city wakes up below.'}
          />
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <label htmlFor={fileId} className="inline-flex cursor-pointer items-center gap-2 text-sm font-semibold text-cue-ink underline hover:text-cue-deep focus-within:outline-2 focus-within:outline-ink">
                <FileText aria-hidden className="size-4" />
                Or choose a text file
                <input id={fileId} type="file" accept=".txt,.fountain,.spmd,text/plain" onChange={chooseFile} className="sr-only" />
              </label>
              {fileError && (
                <p role="alert" className="mt-1 text-sm text-stop-ink">
                  {fileError}
                </p>
              )}
            </div>
            <div className="flex gap-2">
              <Button variant="ghost" onClick={() => navigate(`/projects/${projectId}`)} disabled={importScript.isPending}>
                Cancel
              </Button>
              <Button type="submit" variant={found ? 'secondary' : 'primary'} busy={preview.isPending} disabled={!canSubmit || importScript.isPending}>
                {!preview.isPending && <ScissorsLineDashed aria-hidden className="size-4" />}
                Find scenes
              </Button>
            </div>
          </div>
        </form>

        {found && (
          <FoundScenes found={found} busy={importScript.isPending} onImport={() => importScript.mutate(script)} />
        )}
      </section>
    </div>
  )
}

function FoundScenes({ found, busy, onImport }: { found: ScriptImport; busy: boolean; onImport: () => void }) {
  const count = found.scenes.length
  if (count === 0) {
    return (
      <EmptyState icon={ScissorsLineDashed}>
        No scene headings found. A scene starts at a line such as “INT. DINER - NIGHT”. A script without headings can be added as a
        single scene instead.
      </EmptyState>
    )
  }
  const cut = found.scenes.filter((scene) => scene.truncated).length
  return (
    <section aria-labelledby="found-scenes" className="space-y-4 border-t border-line pt-6">
      <div className="space-y-1">
        <h2 id="found-scenes" className="font-bold font-display text-3xl leading-none">
          {count === 1 ? '1 scene found' : `${count} scenes found`}
        </h2>
        <p className="text-sm text-muted">
          {found.scriptNumbersKept
            ? 'The scenes keep the numbers the script gives them.'
            : 'The scenes are numbered in script order, on from the project’s last scene.'}
          {cut > 0 && ` ${cut === 1 ? '1 scene is' : `${cut} scenes are`} too long to keep whole: the end will be cut off.`}
        </p>
      </div>
      <ol aria-label="Scenes found" className="max-h-96 space-y-2 overflow-y-auto pr-1">
        {found.scenes.map((scene, index) => (
          <li key={index} className="flex items-center gap-3 rounded-lg border border-line bg-white p-2 pr-4">
            <Slate number={scene.sceneNumber} className="scale-90" />
            <span className="sr-only">Scene {scene.sceneNumber}:</span>
            <span className="min-w-0 flex-1 truncate font-semibold text-ink">{scene.title}</span>
            <span className="shrink-0 text-xs text-subtle">
              {scene.characters.toLocaleString()} characters{scene.truncated && ', cut short'}
            </span>
          </li>
        ))}
      </ol>
      <div className="flex justify-end">
        <Button busy={busy} onClick={onImport}>
          {count === 1 ? 'Import 1 scene' : `Import ${count} scenes`}
        </Button>
      </div>
    </section>
  )
}
