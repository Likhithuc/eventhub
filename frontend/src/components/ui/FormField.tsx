import { AlertCircle, Eye, EyeOff } from 'lucide-react'
import { useState, type InputHTMLAttributes, type ReactNode } from 'react'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  name: string
  icon?: ReactNode
  hint?: ReactNode
  error?: string
  /** Rendered inside the input on the right (e.g. a show-password toggle). */
  action?: ReactNode
}

export function FormField({ label, name, icon, hint, error, action, className, ...input }: FieldProps) {
  const id = input.id ?? `field-${name}`
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  const classes = ['input', icon && 'has-icon', action && 'has-action', className].filter(Boolean).join(' ')

  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      <div className="input-wrap">
        {icon && <span className="input-icon">{icon}</span>}
        <input id={id} name={name} className={classes} aria-invalid={!!error} aria-describedby={describedBy} {...input} />
        {action}
      </div>
      {error ? (
        <span id={`${id}-error`} className="field-error">
          <AlertCircle size={13} /> {error}
        </span>
      ) : hint ? (
        <span id={`${id}-hint`} className="field-hint">
          {hint}
        </span>
      ) : null}
    </div>
  )
}

type PasswordProps = Omit<FieldProps, 'type' | 'action'> & { showStrength?: boolean }

export function PasswordField({ showStrength, ...props }: PasswordProps) {
  const [visible, setVisible] = useState(false)
  const value = typeof props.value === 'string' ? props.value : ''

  return (
    <div className="stack" style={{ gap: 8 }}>
      <FormField
        {...props}
        type={visible ? 'text' : 'password'}
        action={
          <button
            type="button"
            className="input-action"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? 'Hide password' : 'Show password'}
            aria-pressed={visible}
          >
            {visible ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        }
      />
      {showStrength && value.length > 0 && <PasswordStrength password={value} />}
    </div>
  )
}

const LABELS = ['Too short', 'Weak', 'Fair', 'Good', 'Strong']

function score(pw: string): number {
  if (pw.length < 8) return 1
  let s = 1
  if (pw.length >= 12) s++
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) s++
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) s++
  return Math.min(s, 4)
}

function PasswordStrength({ password }: { password: string }) {
  const s = score(password)
  const label = password.length < 8 ? LABELS[0] : LABELS[s]
  return (
    <div className="strength" data-score={s} aria-live="polite">
      <div className="strength-bars" aria-hidden="true">
        <span />
        <span />
        <span />
        <span />
      </div>
      <span className="strength-label">{label}</span>
    </div>
  )
}
