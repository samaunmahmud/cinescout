import { useMutation } from '@tanstack/react-query'
import { KeyRound, Trash2, UserRound } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { fieldErrors } from '../api/errors'
import { useAuth, useSession } from '../auth/context'
import { Card, Eyebrow } from '../components/surfaces'
import { Button, ErrorAlert, TextField } from '../components/ui'
import { usePageTitle } from '../lib/usePageTitle'

const MIN_PASSWORD_LENGTH = 8

/** The user's own account: the name emails are signed with, the password, and the way out. */
export function AccountPage() {
  const { user } = useSession()
  usePageTitle('Account')
  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <header className="space-y-2">
        <Eyebrow icon={UserRound}>Account</Eyebrow>
        <h1 className="gold-leaf font-display text-6xl leading-none">{user.displayName}</h1>
        <p className="text-stone-400">{user.email}</p>
      </header>
      <ProfileForm />
      <PasswordForm />
      <DeleteAccount />
    </div>
  )
}

function Panel({ titleId, title, icon, description, children }: { titleId: string; title: string; icon: typeof UserRound; description: string; children: ReactNode }) {
  const Icon = icon
  return (
    <Card className="p-6">
      <section aria-labelledby={titleId} className="space-y-4">
        <div className="space-y-1">
          <h2 id={titleId} className="flex items-center gap-2 gold-leaf font-display text-3xl leading-none">
            <Icon aria-hidden className="size-5 text-amber-400" />
            {title}
          </h2>
          <p className="text-sm text-stone-400">{description}</p>
        </div>
        {children}
      </section>
    </Card>
  )
}

function Saved({ children }: { children: ReactNode }) {
  return (
    <p role="status" className="rounded-lg border border-emerald-900/70 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-200">
      {children}
    </p>
  )
}

function ProfileForm() {
  const { user, api } = useSession()
  const { updateUser } = useAuth()
  const [displayName, setDisplayName] = useState(user.displayName)
  const save = useMutation({ mutationFn: (name: string) => api.account.update(name), onSuccess: updateUser })
  const name = displayName.trim()

  function submit(e: FormEvent) {
    e.preventDefault()
    if (name !== '') save.mutate(name)
  }

  return (
    <Panel titleId="profile-heading" title="Profile" icon={UserRound} description="Outreach emails are signed with this name. The email address is your login and stays as it is.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert error={save.error} />
        {save.isSuccess && <Saved>Name saved.</Saved>}
        <TextField
          label="Display name"
          required
          maxLength={100}
          autoComplete="name"
          value={displayName}
          onChange={(e) => {
            setDisplayName(e.target.value)
            save.reset()
          }}
          error={fieldErrors(save.error).displayName}
        />
        <div className="flex justify-end">
          <Button type="submit" busy={save.isPending} disabled={name === '' || name === user.displayName}>
            Save name
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function PasswordForm() {
  const { api } = useSession()
  const [values, setValues] = useState({ currentPassword: '', newPassword: '', repeat: '' })
  const change = useMutation({
    mutationFn: () => api.account.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }),
    onSuccess: () => setValues({ currentPassword: '', newPassword: '', repeat: '' }),
  })
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => {
    setValues({ ...values, [field]: e.target.value })
    change.reset()
  }

  const tooShort = values.newPassword !== '' && values.newPassword.length < MIN_PASSWORD_LENGTH
  const mismatch = values.repeat !== '' && values.repeat !== values.newPassword
  const canSubmit = values.currentPassword !== '' && values.newPassword.length >= MIN_PASSWORD_LENGTH && values.repeat === values.newPassword
  const errors = fieldErrors(change.error)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (canSubmit) change.mutate()
  }

  return (
    <Panel titleId="password-heading" title="Password" icon={KeyRound} description="Changing it logs you out everywhere else; you stay logged in here.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert error={change.error} />
        {change.isSuccess && <Saved>Password changed. Your other devices were logged out.</Saved>}
        <TextField label="Current password" type="password" required autoComplete="current-password" maxLength={72} value={values.currentPassword} onChange={set('currentPassword')} error={errors.currentPassword} />
        <TextField
          label="New password"
          type="password"
          required
          autoComplete="new-password"
          maxLength={72}
          value={values.newPassword}
          onChange={set('newPassword')}
          error={tooShort ? `At least ${MIN_PASSWORD_LENGTH} characters.` : errors.newPassword}
          hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
        />
        <TextField label="New password again" type="password" required autoComplete="new-password" maxLength={72} value={values.repeat} onChange={set('repeat')} error={mismatch ? 'The two passwords are not the same.' : undefined} />
        <div className="flex justify-end">
          <Button type="submit" busy={change.isPending} disabled={!canSubmit}>
            Change password
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function DeleteAccount() {
  const { api } = useSession()
  const { forget } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const [password, setPassword] = useState('')
  // The account is gone, and the session with it: back to the login page without asking the server again.
  const remove = useMutation({ mutationFn: () => api.account.remove(password), onSuccess: forget })

  function submit(e: FormEvent) {
    e.preventDefault()
    if (password !== '') remove.mutate()
  }

  return (
    <Panel titleId="delete-account-heading" title="Delete account" icon={Trash2} description="Deletes your account with all its projects, scenes, locations and outreach drafts. It cannot be undone.">
      {confirming ? (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <ErrorAlert error={remove.error} />
          <TextField
            label="Your password, to confirm"
            type="password"
            required
            autoComplete="current-password"
            maxLength={72}
            value={password}
            onChange={(e) => {
              setPassword(e.target.value)
              remove.reset()
            }}
            error={fieldErrors(remove.error).password}
          />
          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              disabled={remove.isPending}
              onClick={() => {
                setConfirming(false)
                setPassword('')
                remove.reset()
              }}
            >
              Cancel
            </Button>
            <Button type="submit" variant="danger" busy={remove.isPending} disabled={password === ''}>
              Delete my account for good
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex justify-end">
          <Button variant="secondary" onClick={() => setConfirming(true)}>
            Delete account…
          </Button>
        </div>
      )}
    </Panel>
  )
}
