import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { Logo } from '../components/Layout'
import { fieldErrors } from '../api/errors'
import { Button, ErrorAlert, TextField } from '../components/ui'

function AuthCard({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm space-y-6">
        <div className="space-y-2 text-center">
          <Logo />
          <h1 className="text-2xl font-semibold">{title}</h1>
        </div>
        <div className="rounded-lg border border-stone-800 bg-stone-900/60 p-6">{children}</div>
        <p className="text-center text-sm text-stone-400">{footer}</p>
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
  const { session, logIn } = useAuth()
  const navigate = useNavigate()
  const returnTo = useReturnTo()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

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
  const { session, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ displayName: '', email: '', password: '' })
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

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
