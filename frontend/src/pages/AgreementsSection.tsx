import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, FileText } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import type { Agreement, Location } from '../api/types'
import { useSession } from '../auth/context'
import { useCanEdit } from '../components/projectRole'
import { EmptyState, Section } from '../components/surfaces'
import { Button, ErrorAlert, Spinner, TextField } from '../components/ui'
import { saveFile } from '../lib/saveFile'
import { blankToNull } from '../lib/text'

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

/**
 * The venue's location release: a PDF template filled in from its contact and quote and the scene's dates, times and
 * crew, kept as numbered versions. A template, not legal advice, as the page and every page of the PDF say.
 */
export function AgreementsSection({ location }: { location: Location }) {
  const canEdit = useCanEdit()
  const { api } = useSession()
  const queryClient = useQueryClient()
  const key = queryKeys.agreements(location.id)
  const agreements = useQuery({ queryKey: key, queryFn: () => api.agreements.list(location.id) })
  const [company, setCompany] = useState('')
  const generate = useMutation({
    mutationFn: () => api.agreements.generate(location.id, blankToNull(company)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  })
  const remove = useMutation({ mutationFn: (id: string) => api.agreements.remove(id), onSuccess: () => queryClient.invalidateQueries({ queryKey: key }) })
  const fetchFile = useMutation({
    mutationFn: (agreement: Agreement) => api.agreements.download(agreement.id),
    onSuccess: (file, agreement) => saveFile(file.blob, file.filename ?? `location-release-v${agreement.version}.pdf`),
  })
  const server = fieldErrors(generate.error)
  const latest = agreements.data?.items[0]

  function submit(e: FormEvent) {
    e.preventDefault()
    generate.mutate()
  }

  return (
    <Section
      titleId="agreements-heading"
      title="Location release"
      eyebrow="The paperwork"
      icon={FileText}
      description="A template filled in from the venue’s contact and quote and the scene’s dates, times and crew, with blanks for the rest. It is not legal advice: have it reviewed before anyone signs."
    >
      {canEdit && (
        <form onSubmit={submit} aria-label="Make a location release" className="flex flex-wrap items-end gap-3 board-card rounded-lg bg-white p-4" noValidate>
          <TextField
            label="Production company"
            className="min-w-56 flex-1"
            maxLength={200}
            hint="Optional. Left blank on the form if empty."
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            error={server.productionCompany}
          />
          <Button type="submit" busy={generate.isPending}>
            {!generate.isPending && <FileText aria-hidden className="size-4" />}
            {latest ? 'Make a new version' : 'Make the release'}
          </Button>
        </form>
      )}
      <ErrorAlert error={generate.error ?? remove.error ?? fetchFile.error} />
      {agreements.isPending ? (
        <Spinner label="Loading the releases" />
      ) : agreements.isError ? (
        <ErrorAlert error={agreements.error} onRetry={() => agreements.refetch()} />
      ) : agreements.data.items.length === 0 ? (
        <EmptyState icon={FileText}>No release yet. Add the venue’s contact and quote first, and set the scene’s dates and times, so it comes out filled in.</EmptyState>
      ) : (
        <ul aria-label="Versions of the release" className="divide-y divide-line-soft board-card rounded-lg bg-white">
          {agreements.data.items.map((agreement) => (
            <li key={agreement.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
              <p className="min-w-0 text-sm text-muted">
                <span className="font-semibold text-ink">Version {agreement.version}</span>
                {' · made '}
                {dateFormat.format(new Date(agreement.createdAt))}
                {agreement.createdByName && ` by ${agreement.createdByName}`}
                {` · ${Math.max(1, Math.round(agreement.sizeBytes / 1024))} KB`}
              </p>
              <div className="flex gap-2">
                {canEdit && (
                  <Button variant="ghost" aria-label={`Delete version ${agreement.version}`} busy={remove.isPending && remove.variables === agreement.id} onClick={() => remove.mutate(agreement.id)}>
                    Delete
                  </Button>
                )}
                <Button
                  variant="secondary"
                  aria-label={`Download version ${agreement.version}`}
                  busy={fetchFile.isPending && fetchFile.variables?.id === agreement.id}
                  onClick={() => fetchFile.mutate(agreement)}
                >
                  <Download aria-hidden className="size-4" />
                  PDF
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}
