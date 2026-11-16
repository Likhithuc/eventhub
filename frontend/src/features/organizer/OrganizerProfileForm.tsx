import { Building2, Mail } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiError, errorMessage } from '../../api/http'
import type { OrganizerProfile, OrganizerProfileInput } from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { FormField } from '../../components/ui/FormField'
import { Spinner } from '../../components/ui/Spinner'

interface Props {
  initial?: OrganizerProfile | null
  submitLabel: ReactNode
  onSubmit: (input: OrganizerProfileInput) => Promise<void>
}

export function OrganizerProfileForm({ initial, submitLabel, onSubmit }: Props) {
  const [displayName, setDisplayName] = useState(initial?.displayName ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [contactEmail, setContactEmail] = useState(initial?.contactEmail ?? '')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (displayName.trim().length < 2) {
      setFieldErrors({ displayName: 'Use at least 2 characters' })
      return
    }
    setFieldErrors({})
    setBusy(true)
    try {
      await onSubmit({
        displayName: displayName.trim(),
        description: description.trim() || undefined,
        contactEmail: contactEmail.trim() || undefined,
      })
    } catch (err) {
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setError(errorMessage(err, 'Could not save your profile'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={submit} noValidate>
      {error && <Alert>{error}</Alert>}
      <FormField
        label="Organizer name"
        name="displayName"
        placeholder="e.g. Nightfall Collective"
        icon={<Building2 size={16} />}
        maxLength={120}
        value={displayName}
        onChange={(e) => setDisplayName(e.target.value)}
        error={fieldErrors.displayName}
        hint="Shown to attendees on every event you publish."
      />
      <div className="field">
        <label className="field-label" htmlFor="field-description">
          About you <span className="muted">(optional)</span>
        </label>
        <textarea
          id="field-description"
          className="input textarea"
          rows={4}
          maxLength={2000}
          placeholder="What kind of events do you run?"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </div>
      <FormField
        label="Contact email (optional)"
        name="contactEmail"
        type="email"
        placeholder="Defaults to your account email"
        icon={<Mail size={16} />}
        value={contactEmail}
        onChange={(e) => setContactEmail(e.target.value)}
        error={fieldErrors.contactEmail}
      />
      <div>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy && <Spinner small />} {submitLabel}
        </button>
      </div>
    </form>
  )
}
