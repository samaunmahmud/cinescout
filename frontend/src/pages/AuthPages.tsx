import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { SessionCheck } from '../auth/SessionCheck'
import { Logo } from '../components/Layout'
import { AdmitOne, QuietOnSet, TapeLabel } from '../components/stickers'
import { fieldErrors } from '../api/errors'
import { usePageTitle } from '../lib/usePageTitle'
import { Button, ErrorAlert, TextField } from '../components/ui'

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
      {/* The location department's door: the pitch on a chalk slate, with stickers on it. */}
      <aside className="relative hidden flex-col justify-between gap-10 overflow-hidden bg-ink px-14 py-11 text-white lg:flex">
        <div className="flex items-center justify-between">
          <Logo size="lg" />
          <span className="font-script text-[13px] tracking-[0.1em] text-ink-muted">LOCATION DEPT.</span>
        </div>

        <div className="max-w-xl space-y-5">
          <p className="font-display text-6xl leading-[0.98] font-extrabold xl:text-[4rem]">
            Find the place your scene was <span className="text-cue">written for.</span>
          </p>
          <p className="max-w-lg text-lg leading-relaxed text-fog">
            Paste a scene. CineScout reads what it needs, finds real venues nearby, scores how well each one fits and
            drafts the email to the owner.
          </p>
        </div>

        <div aria-hidden className="max-w-xl -rotate-[1.5deg]">
          <div className="clapper h-11 rounded-t-lg border-[3px] border-white" />
          <div className="grid grid-cols-3 rounded-b-lg border-[3px] border-t-0 border-white bg-[#1b222b] font-script">
            <div className="col-span-3 flex items-baseline gap-4 border-b-2 border-ink-line px-5 py-3">
              <span className="text-xs tracking-[0.1em] text-ink-muted">PROD.</span>
              <span className="font-marker text-2xl text-paper">The Night Ferry</span>
            </div>
            {exampleSlate.map(({ label, value, cue }) => (
              <div key={label} className="flex flex-col border-r-2 border-ink-line px-5 py-2.5 last:border-r-0">
                <span className="text-xs tracking-[0.1em] text-ink-muted">{label}</span>
                <span className={`font-marker text-3xl ${cue ? 'text-cue' : 'text-paper'}`}>{value}</span>
              </div>
            ))}
            <div className="col-span-3 border-t-2 border-ink-line px-5 py-2.5 text-[15px] text-fog">INT. ALL-NIGHT DINER - NIGHT</div>
          </div>
        </div>

        <QuietOnSet className="absolute top-36 right-12" />
        <AdmitOne className="absolute right-16 bottom-14" />
      </aside>

      <div className="relative flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm animate-fade-in space-y-6">
          <span className="flex justify-center lg:hidden">
            <Logo size="lg" onDark={false} />
          </span>
          <div className="board-card relative rounded-lg bg-white px-6 pt-10 pb-6">
            <TapeLabel tilt={-2} className="absolute -top-4 left-1/2 -translate-x-1/2">
              {tape}
            </TapeLabel>
            <h1 className="mb-5 font-display text-4xl leading-none font-extrabold">{title}</h1>
            {children}
          </div>
          <p className="text-center text-[15px] text-muted">{footer}</p>
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
      tape="Crew sign-in"
      footer={
        <>
          New to CineScout?{' '}
          <Link to="/register" className="font-bold text-cue-ink underline decoration-2 underline-offset-2 hover:text-cue-deep">
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
      tape="New crew"
      footer={
        <>
          Already have one?{' '}
          <Link to="/login" className="font-bold text-cue-ink underline decoration-2 underline-offset-2 hover:text-cue-deep">
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
