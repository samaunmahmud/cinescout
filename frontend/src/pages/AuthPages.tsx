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
    <div className="grid min-h-dvh lg:grid-cols-[1.15fr_1fr]">
      {/* Premiere night: searchlights over the theatre, the marquee lit, velvet in the wings. */}
      <aside className="relative hidden overflow-hidden border-r border-amber-300/15 bg-black lg:block">
        <div aria-hidden className="absolute inset-0 bg-[radial-gradient(ellipse_at_50%_115%,rgb(223_184_73/0.30),transparent_60%),radial-gradient(ellipse_at_50%_-20%,rgb(23_37_84/0.55),transparent_60%)]" />
        <div aria-hidden className="searchlight left-[8%] animate-sweep" />
        <div aria-hidden className="searchlight right-[6%] animate-sweep-slow" />
        <div aria-hidden className="searchlight left-[38%] animate-sweep-slow opacity-60" />
        <div aria-hidden className="velvet absolute inset-y-0 left-0 w-10 opacity-90 shadow-[8px_0_24px_rgb(0_0_0/0.7)]" />
        <div aria-hidden className="velvet absolute inset-y-0 right-0 w-10 opacity-90 shadow-[-8px_0_24px_rgb(0_0_0/0.7)]" />

        <div className="relative flex h-full flex-col items-center justify-between px-20 py-12 text-center">
          <Logo size="lg" />

          {/* The marquee. */}
          <div className="w-full max-w-lg">
            <div className="rounded-lg bg-gradient-to-b from-amber-200/90 via-amber-400 to-amber-600 p-[3px] shadow-[0_0_70px_-8px_rgb(223_184_73/0.65)]">
              <div className="rounded-[5px] bg-gradient-to-b from-stone-950 to-black">
                <div aria-hidden className="bulbs mx-3 mt-2" />
                <div className="space-y-3 px-8 py-7">
                  <p className="text-[11px] font-semibold tracking-[0.45em] text-amber-200/80 uppercase">Now scouting</p>
                  <p className="gold-leaf animate-flicker font-marquee text-5xl leading-[1.05] xl:text-6xl">
                    The place your scene was written for
                  </p>
                  <div aria-hidden className="deco-rule text-xs">◆</div>
                  <p className="font-serif text-lg text-stone-300 italic">From the page to the perfect location.</p>
                </div>
                <div aria-hidden className="bulbs mx-3 mb-2" />
              </div>
            </div>
          </div>

          <div className="w-full max-w-lg space-y-6">
            <ul className="grid grid-cols-2 gap-x-6 gap-y-4 text-left">
              {features.map(({ icon: Icon, text }) => (
                <li key={text} className="flex items-start gap-3 text-sm text-stone-300">
                  <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full bg-amber-300/10 text-amber-300 ring-1 ring-amber-300/30">
                    <Icon aria-hidden className="size-4" />
                  </span>
                  {text}
                </li>
              ))}
            </ul>
            <p className="billing text-[10px] text-stone-500">AI location scouting for film and television</p>
          </div>
        </div>
      </aside>

      <div className="relative flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm animate-fade-in space-y-6">
          <div className="space-y-4 text-center">
            <span className="lg:hidden">
              <Logo size="lg" />
            </span>
            <p className="text-[11px] font-semibold tracking-[0.4em] text-amber-300/80 uppercase">Admit one</p>
            <h1 className="gold-leaf font-display text-6xl leading-none">{title}</h1>
            <div aria-hidden className="deco-rule text-xs">◆</div>
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
