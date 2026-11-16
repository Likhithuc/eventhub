import { ArrowRight, Lock, Mail, UserRound } from 'lucide-react'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/http'
import { Alert } from '../components/ui/Alert'
import { FormField, PasswordField } from '../components/ui/FormField'
import { Spinner } from '../components/ui/Spinner'
import { AuthShell } from '../features/auth/AuthShell'
import { useAuth } from '../features/auth/useAuth'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function RegisterPage() {
  const { register, status } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated' && !submitting) return <Navigate to={from} replace />

  const update = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const validate = (): Record<string, string> => {
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = 'Enter your name'
    if (!EMAIL_RE.test(form.email.trim())) errs.email = 'Enter a valid email address'
    if (form.password.length < 8) errs.password = 'Use at least 8 characters'
    else if (form.password.length > 72) errs.password = 'Use at most 72 characters'
    if (form.confirm !== form.password) errs.confirm = 'Passwords do not match'
    return errs
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const errs = validate()
    setFieldErrors(errs)
    if (Object.keys(errs).length) return

    setSubmitting(true)
    try {
      await register({ name: form.name.trim(), email: form.email.trim(), password: form.password })
      navigate(from, { replace: true })
    } catch (err) {
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setError(errorMessage(err, 'Registration failed'))
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="Create your account"
      subtitle="Join EventHub to discover and book events."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" state={{ from }}>
            Log in
          </Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        {error && <Alert>{error}</Alert>}
        <FormField
          label="Full name"
          name="name"
          autoComplete="name"
          placeholder="Asha Rao"
          maxLength={100}
          icon={<UserRound size={16} />}
          value={form.name}
          onChange={update('name')}
          error={fieldErrors.name}
          autoFocus
        />
        <FormField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          icon={<Mail size={16} />}
          value={form.email}
          onChange={update('email')}
          error={fieldErrors.email}
        />
        <PasswordField
          label="Password"
          name="password"
          autoComplete="new-password"
          placeholder="At least 8 characters"
          maxLength={72}
          icon={<Lock size={16} />}
          value={form.password}
          onChange={update('password')}
          error={fieldErrors.password}
          showStrength
        />
        <PasswordField
          label="Confirm password"
          name="confirm"
          autoComplete="new-password"
          placeholder="Repeat your password"
          icon={<Lock size={16} />}
          value={form.confirm}
          onChange={update('confirm')}
          error={fieldErrors.confirm}
        />
        <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={submitting}>
          {submitting ? (
            <>
              <Spinner small /> Creating account…
            </>
          ) : (
            <>
              Create account <ArrowRight size={18} />
            </>
          )}
        </button>
        <p className="small muted" style={{ textAlign: 'center' }}>
          By signing up you agree to our Terms and Privacy Policy.
        </p>
      </form>
    </AuthShell>
  )
}
