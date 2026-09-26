import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { SessionCheck } from '../auth/SessionCheck'
import { Mail, MapPinned, ScrollText, Sunset } from 'lucide-react'
import { Logo } from '../components/Layout'
import { Card } from '../components/surfaces'
import { fieldErrors } from '../api/errors'
import { Button, ErrorAlert, TextField } from '../components/ui'

const features = [
  { icon: ScrollText, text: 'Reads your scene and works out the location it needs' },
  { icon: MapPinned, text: 'Finds real venues nearby and rates how well each one fits' },
  { icon: Sunset, text: 'Golden hour, weather and noise for every shoot day' },
  { icon: Mail, text: 'Drafts the email to the venue’s owner for you to send' },
]

function AuthCard({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      {/* The poster side: decoration and a word on what CineScout does. */}
      <aside className="relative hidden overflow-hidden border-r border-white/[0.06] lg:block">
        <div aria-hidden className="absolute inset-0 bg-[radial-gradient(ellipse_at_20%_20%,rgb(245_158_11/0.28),transparent_55%),radial-gradient(ellipse_at_80%_90%,rgb(180_83_9/0.22),transparent_50%)]" />
        <div aria-hidden className="film-strip absolute inset-x-0 top-0" />
        <div aria-hidden className="film-strip absolute inset-x-0 bottom-0" />
        <div className="relative flex h-full flex-col justify-between p-12">
          <Logo size="lg" />
          <div className="space-y-8">
            <p className="font-display text-7xl leading-[0.9] text-stone-50">
              Find the place
              <br />
              your scene was
              <br />
              <span className="text-amber-400">written for.</span>
            </p>
            <ul className="space-y-3">
              {features.map(({ icon: Icon, text }) => (
                <li key={text} className="flex items-center gap-3 text-stone-300">
                  <span className="flex size-8 items-center justify-center rounded-full bg-amber-500/10 text-amber-400 ring-1 ring-amber-400/25">
                    <Icon aria-hidden className="size-4" />
                  </span>
                  {text}
                </li>
              ))}
            </ul>
          </div>
          <p className="text-xs tracking-[0.3em] text-stone-500 uppercase">AI location scouting for film and TV</p>
        </div>
      </aside>

      <div className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm space-y-6">
          <div className="space-y-3 text-center">
            <span className="lg:hidden">
              <Logo size="lg" />
            </span>
            <h1 className="font-display text-5xl leading-none text-stone-50">{title}</h1>
          </div>
          <Card className="p-6">{children}</Card>
          <p className="text-center text-sm text-stone-400">{footer}</p>
        </div>
      </div>
    </div>
  )
}

/** Where to go after logging in: back to the page that sent the user here, or the project list. */
function useReturnTo(): string {
  const state = useLocation().state as { from?: string } | null
  return state?.from?.startsWith('/') ? state.from : '/projects'
}

export function LoginPage() {
  const { session, checking, logIn } = useAuth()
  const navigate = useNavigate()
  const returnTo = useReturnTo()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  if (checking) return <SessionCheck />
  if (session) return <Navigate to={returnTo} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await logIn(email, password)
      navigate(returnTo, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? new ApiError(401, 'Unauthorized', 'Wrong email or password.') : err)
      setBusy(false)
    }
  }

  return (
    <AuthCard
      title="Log in"
      footer={
        <>
          New to CineScout?{' '}
          <Link to="/register" className="font-semibold text-amber-400 hover:text-amber-300">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert error={error} />
        <TextField label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button type="submit" busy={busy} className="w-full" disabled={!email || !password}>
          Log in
        </Button>
      </form>
    </AuthCard>
  )
}

export function RegisterPage() {
  const { session, checking, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ displayName: '', email: '', password: '' })
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  if (checking) return <SessionCheck />
  if (session) return <Navigate to="/projects" replace />

  const errors = fieldErrors(error)
  const set = (field: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [field]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register(form)
      navigate('/projects', { replace: true })
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  return (
    <AuthCard
      title="Create an account"
      footer={
        <>
          Already have one?{' '}
          <Link to="/login" className="font-semibold text-amber-400 hover:text-amber-300">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorAlert error={error} />
        <TextField label="Name" autoComplete="name" required maxLength={100} value={form.displayName} onChange={set('displayName')} error={errors.displayName} />
        <TextField label="Email" type="email" autoComplete="email" required maxLength={254} value={form.email} onChange={set('email')} error={errors.email} />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
          hint="At least 8 characters."
          value={form.password}
          onChange={set('password')}
          error={errors.password}
        />
        <Button type="submit" busy={busy} className="w-full" disabled={!form.displayName || !form.email || !form.password}>
          Create account
        </Button>
      </form>
    </AuthCard>
  )
}
