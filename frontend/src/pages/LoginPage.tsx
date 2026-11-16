import { ArrowRight, Lock, Mail } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/http'
import { Alert } from '../components/ui/Alert'
import { FormField, PasswordField } from '../components/ui/FormField'
import { Spinner } from '../components/ui/Spinner'
import { AuthShell } from '../features/auth/AuthShell'
import { useAuth } from '../features/auth/useAuth'

export function LoginPage() {
  const { login, status } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated' && !submitting) return <Navigate to={from} replace />

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const local: Record<string, string> = {}
    if (!email.trim()) local.email = 'Enter your email'
    if (!password) local.password = 'Enter your password'
    setFieldErrors(local)
    if (Object.keys(local).length) return

    setSubmitting(true)
    try {
      await login({ email: email.trim(), password })
      navigate(from, { replace: true })
    } catch (err) {
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setError(errorMessage(err, 'Login failed'))
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Log in to manage your tickets and events."
      footer={
        <>
          New to EventHub?{' '}
          <Link to="/register" state={{ from }}>
            Create an account
          </Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        {error && <Alert>{error}</Alert>}
        <FormField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          icon={<Mail size={16} />}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          autoFocus
        />
        <PasswordField
          label="Password"
          name="password"
          autoComplete="current-password"
          placeholder="Your password"
          icon={<Lock size={16} />}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={submitting}>
          {submitting ? (
            <>
              <Spinner small /> Logging in…
            </>
          ) : (
            <>
              Log in <ArrowRight size={18} />
            </>
          )}
        </button>
      </form>
    </AuthShell>
  )
}
