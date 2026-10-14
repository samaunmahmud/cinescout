import { useMutation } from '@tanstack/react-query'
import { Download, MailOpen } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../api/errors'
import type { Location } from '../api/types'
import { useSession } from '../auth/context'
import { useCanEdit } from '../components/projectRole'
import { Section } from '../components/surfaces'
import { Button, ErrorAlert, TextArea, TextField } from '../components/ui'
import { saveFile } from '../lib/saveFile'
import { blankToNull } from '../lib/text'

/**
 * The letter dropped through the doors round a venue before a shoot: who is filming, the scene's dates and hours, how
 * many people, parking, a night-work note when the scene runs late, and who to call. Made as a PDF on request, never
 * stored, and never carrying the venue owner's details or quote. Editors only, like the release.
 */
export function ResidentsLetterSection({ location }: { location: Location }) {
  const canEdit = useCanEdit()
  const { api, user } = useSession()
  const [company, setCompany] = useState('')
  const [name, setName] = useState(user.displayName)
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [parking, setParking] = useState('')
  const [note, setNote] = useState('')
  const letter = useMutation({
    mutationFn: () =>
      api.agreements.residentsLetter(location.id, {
        productionCompany: blankToNull(company),
        contactName: blankToNull(name),
        contactPhone: blankToNull(phone),
        contactEmail: blankToNull(email),
        parking: blankToNull(parking),
        note: blankToNull(note),
      }),
    onSuccess: (file) => saveFile(file.blob, file.filename ?? 'letter-to-neighbours.pdf'),
  })
  const server = fieldErrors(letter.error)

  if (!canEdit) return null

  function submit(e: FormEvent) {
    e.preventDefault()
    letter.mutate()
  }

  return (
    <Section
      titleId="residents-letter-heading"
      title="Letter to neighbours"
      eyebrow="Before the shoot"
      icon={MailOpen}
      description="A one-page letter for the doors round the venue, written from the scene’s dates, hours and crew size. It warns of night work when the scene runs late. The venue owner’s details and fee are never on it."
    >
      <form onSubmit={submit} aria-label="Write the letter to neighbours" className="grid gap-4 board-card rounded-lg bg-paper p-4 sm:grid-cols-2" noValidate>
        <TextField
          label="Production company"
          maxLength={200}
          hint="Optional. “Our crew” when empty."
          value={company}
          onChange={(e) => setCompany(e.target.value)}
          error={server.productionCompany}
        />
        <TextField label="Who to call" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} error={server.contactName} />
        <TextField label="Phone" type="tel" maxLength={40} value={phone} onChange={(e) => setPhone(e.target.value)} error={server.contactPhone} />
        <TextField
          label="Email"
          type="email"
          maxLength={254}
          hint="Optional. It goes through strangers’ doors."
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={server.contactEmail}
        />
        <div className="space-y-4 sm:col-span-2">
          <TextArea
            label="Parking"
            rows={3}
            maxLength={600}
            hint="Optional. Without it the letter promises to park legally and keep pavements and driveways clear."
            value={parking}
            onChange={(e) => setParking(e.target.value)}
            error={server.parking}
          />
          <TextArea
            label="Anything else neighbours should know"
            rows={3}
            maxLength={1500}
            hint="Road closures, a rain machine, a drone, special effects."
            value={note}
            onChange={(e) => setNote(e.target.value)}
            error={server.note}
          />
        </div>
        <div className="flex items-end sm:col-span-2">
          <Button type="submit" busy={letter.isPending}>
            {!letter.isPending && <Download aria-hidden className="size-4" />}
            Download the letter (PDF)
          </Button>
        </div>
      </form>
      <ErrorAlert error={letter.error} />
    </Section>
  )
}
