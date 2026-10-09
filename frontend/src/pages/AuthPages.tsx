import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { SessionCheck } from '../auth/SessionCheck'
import { Logo } from '../components/Layout'
import { QuietOnSet, TapeLabel } from '../components/stickers'
import { fieldErrors } from '../api/errors'
import { usePageTitle } from '../lib/usePageTitle'
import { Button, ErrorAlert, TextField } from '../components/ui'
import { AvatarStack } from '../components/Avatar'
import { posterBackdrop } from '../lib/poster'
import { Mail, MapPinned, ScrollText } from 'lucide-react'
import { StudioLight } from '../components/StudioLight'

/** The slate on the login page: one scene, scouted, as an example of what the app does. */
const exampleSlate = [
  { label: 'SCENE', value: '2' },
  { label: 'VENUES', value: '8' },
  { label: 'BEST FIT', value: '88', cue: true },
]

function AuthCard({ title, tape, children, footer }: { title: string; tape: string; children: ReactNode; footer: ReactNode }) {
  usePageTitle(title)
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.15fr_1fr]">
      {/* The way in: the pitch over a lit set, with the slate of a scene CineScout has just scouted. */}
      <aside className="hero hidden flex-col justify-between gap-10 px-14 py-11 lg:flex" style={{ background: posterBackdrop('the edit') }}>
        <div className="flex items-center justify-between">
          <Logo size="lg" onDark />
          <span className="text-xs font-semibold tracking-[0.14em] text-fog">LOCATION SCOUTING</span>
        </div>

        <div className="max-w-xl space-y-5">
          <p className="font-display text-6xl leading-[0.98] font-bold xl:text-[4rem]">
            Find the place your scene was <span className="text-cue">written for.</span>
          </p>
          <p className="max-w-lg text-lg leading-relaxed text-fog">
            Paste a scene. CineScout reads what it needs, finds real venues nearby, scores how well each one fits and
            drafts the email to the owner.
          </p>
        </div>

        <div aria-hidden className="relative max-w-xl">
          <QuietOnSet className="absolute -top-14 -right-4 z-10 !size-24" />
          <div className="overflow-hidden rounded-2xl bg-white/10 ring-1 ring-white/15 backdrop-blur-md">
            <div className="flex items-baseline gap-4 border-b border-white/10 px-6 py-4">
              <span className="text-xs font-semibold tracking-[0.14em] text-fog">PROD.</span>
              <span className="text-xl font-semibold text-white">The Night Ferry</span>
            </div>
            <div className="grid grid-cols-3">
              {exampleSlate.map(({ label, value, cue }) => (
                <div key={label} className="flex flex-col border-r border-white/10 px-6 py-3 last:border-r-0">
                  <span className="text-xs font-semibold tracking-[0.14em] text-fog">{label}</span>
                  <span className={`font-mono text-3xl font-medium ${cue ? 'text-cue' : 'text-white'}`}>{value}</span>
                </div>
              ))}
            </div>
            <div className="flex items-center justify-between gap-4 border-t border-white/10 px-6 py-3 text-sm text-fog">
              <span>INT. ALL-NIGHT DINER - NIGHT</span>
              <AvatarStack names={['Maya Chen', 'Leo Okafor', 'Ruth Lane']} max={3} />
            </div>
          </div>
        </div>

      </aside>

      {/* The other side of the set: night, the red light from the far corner, the form on frosted glass. */}
      <div
        className="theme-dark hero flex items-center justify-center px-4 py-12 text-ink"
        style={{ background: 'radial-gradient(90% 70% at 100% 100%, hsl(8 82% 52% / 0.7), transparent 65%), radial-gradient(60% 50% at 0% 0%, hsl(36 90% 50% / 0.18), transparent 60%), #08090c' }}
      >
        <StudioLight />
        <div className="w-full max-w-sm animate-fade-in space-y-6">
          <span className="flex justify-center lg:hidden">
            <Logo size="lg" onDark />
          </span>
          <div className="relative rounded-2xl bg-white/[0.06] px-6 pt-10 pb-6 shadow-[0_30px_60px_-30px_rgb(0_0_0/0.8)] ring-1 ring-white/12 backdrop-blur-xl">
            <TapeLabel className="absolute -top-3.5 left-1/2 -translate-x-1/2 !bg-brand !text-white">
              {tape}
            </TapeLabel>
            <h1 className="mb-5 font-display text-4xl leading-none font-bold text-white">{title}</h1>
            {children}
          </div>
          <p className="text-center text-[15px] text-fog">{footer}</p>
          <ul aria-label="What CineScout does" className="grid grid-cols-3 gap-2 pt-2 text-center text-xs text-fog">
            <li className="flex flex-col items-center gap-2 rounded-xl bg-white/[0.04] px-2 py-3 ring-1 ring-white/10">
              <ScrollText aria-hidden className="size-4 text-cue" />
              Reads the scene
            </li>
            <li className="flex flex-col items-center gap-2 rounded-xl bg-white/[0.04] px-2 py-3 ring-1 ring-white/10">
              <MapPinned aria-hidden className="size-4 text-cue" />
              Finds real venues
            </li>
            <li className="flex flex-col items-center gap-2 rounded-xl bg-white/[0.04] px-2 py-3 ring-1 ring-white/10">
              <Mail aria-hidden className="size-4 text-cue" />
              Writes to owners
            </li>
          </ul>
        </div>
      </div>
    </div>
  )
}

/** Where to go after logging in: back to the page that sent the user here, or the project list. */
function useReturnTo(): string {
  const state = useLocation().state as { from?: string } | null
  return state?.from?.startsWith('/') && !state.from.startsWith('//') ? state.from : '/projects'
}

export function LoginPage() {
  const { session, checking, logIn } = useAuth()
  const navigate = useNavigate()
  const entry = useLocation().state as unknown
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
      tape="Crew sign-in"
      footer={
        <>
          New to CineScout?{' '}
          <Link to="/register" state={entry} className="font-bold text-cue-ink underline decoration-2 underline-offset-2 hover:text-cue-deep">
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
  const entry = useLocation().state as unknown
  const returnTo = useReturnTo()
  const [form, setForm] = useState({ displayName: '', email: '', password: '' })
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  if (checking) return <SessionCheck />
  if (session) return <Navigate to={returnTo} replace />

  const errors = fieldErrors(error)
  const set = (field: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [field]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register(form)
      navigate(returnTo, { replace: true })
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  return (
    <AuthCard
      title="Create an account"
      tape="New crew"
      footer={
        <>
          Already have one?{' '}
          <Link to="/login" state={entry} className="font-bold text-cue-ink underline decoration-2 underline-offset-2 hover:text-cue-deep">
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
