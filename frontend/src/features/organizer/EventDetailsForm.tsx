import { Save } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import type { Category } from '../../api/eventApi'
import { ApiError, errorMessage } from '../../api/http'
import type { EventInput, OrganizerEvent } from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { FormField } from '../../components/ui/FormField'
import { Spinner } from '../../components/ui/Spinner'
import { fromLocalInput, toLocalInput } from '../../lib/format'

interface Props {
  event?: OrganizerEvent | null
  categories: Category[]
  disabled?: boolean
  submitLabel: string
  onSave: (input: EventInput) => Promise<void>
}

type Form = {
  title: string
  description: string
  category: string
  venueName: string
  address: string
  city: string
  startsAt: string
  endsAt: string
  cancellationCutoffHours: string
}

function initialForm(e?: OrganizerEvent | null): Form {
  return {
    title: e?.title ?? '',
    description: e?.description ?? '',
    category: e?.category.slug ?? '',
    venueName: e?.venueName ?? '',
    address: e?.address ?? '',
    city: e?.city ?? '',
    startsAt: toLocalInput(e?.startsAt),
    endsAt: toLocalInput(e?.endsAt),
    cancellationCutoffHours: String(e?.cancellationCutoffHours ?? 24),
  }
}

function validate(f: Form): Record<string, string> {
  const errs: Record<string, string> = {}
  if (f.title.trim().length < 3) errs.title = 'Use at least 3 characters'
  if (f.description.trim().length < 10) errs.description = 'Tell attendees a bit more (10+ characters)'
  if (!f.category) errs.category = 'Choose a category'
  if (!f.venueName.trim()) errs.venueName = 'Enter the venue name'
  if (!f.address.trim()) errs.address = 'Enter the address'
  if (f.city.trim().length < 2) errs.city = 'Enter the city'
  const start = fromLocalInput(f.startsAt)
  const end = fromLocalInput(f.endsAt)
  if (!start) errs.startsAt = 'Choose a start date and time'
  else if (new Date(start) <= new Date()) errs.startsAt = 'The event must start in the future'
  if (!end) errs.endsAt = 'Choose an end date and time'
  else if (start && new Date(end) <= new Date(start)) errs.endsAt = 'Must be after the start'
  return errs
}

export function EventDetailsForm({ event, categories, disabled, submitLabel, onSave }: Props) {
  const [form, setForm] = useState<Form>(() => initialForm(event))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const set = (key: keyof Form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const errs = validate(form)
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      await onSave({
        title: form.title.trim(),
        description: form.description.trim(),
        category: form.category,
        venueName: form.venueName.trim(),
        address: form.address.trim(),
        city: form.city.trim(),
        startsAt: fromLocalInput(form.startsAt)!,
        endsAt: fromLocalInput(form.endsAt)!,
        cancellationCutoffHours: Number(form.cancellationCutoffHours),
      })
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors)
      setError(errorMessage(err, 'Could not save the event'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form-grid" onSubmit={submit} noValidate>
      {error && (
        <div className="span-2">
          <Alert>{error}</Alert>
        </div>
      )}
      <fieldset className="span-2 form-grid" disabled={disabled || busy} style={{ border: 0, padding: 0, margin: 0 }}>
        <div className="span-2">
          <FormField label="Event title" name="title" maxLength={150} placeholder="e.g. Indie Nights Live"
            value={form.title} onChange={set('title')} error={errors.title} />
        </div>
        <div className="field">
          <label className="field-label" htmlFor="field-category">Category</label>
          <select id="field-category" className="input" value={form.category} onChange={set('category')}
            aria-invalid={!!errors.category}>
            <option value="">Choose a category</option>
            {categories.map((c) => (
              <option key={c.slug} value={c.slug}>{c.name}</option>
            ))}
          </select>
          {errors.category && <span className="field-error">{errors.category}</span>}
        </div>
        <FormField label="City" name="city" maxLength={80} placeholder="e.g. Bengaluru"
          value={form.city} onChange={set('city')} error={errors.city} />
        <div className="field span-2">
          <label className="field-label" htmlFor="field-description">Description</label>
          <textarea id="field-description" className="input textarea" rows={6} maxLength={5000}
            placeholder="What should attendees expect? Line-up, schedule, age limits, what to bring…"
            value={form.description} onChange={set('description')} aria-invalid={!!errors.description} />
          {errors.description ? (
            <span className="field-error">{errors.description}</span>
          ) : (
            <span className="field-hint">{form.description.length}/5000</span>
          )}
        </div>
        <FormField label="Venue" name="venueName" maxLength={150} placeholder="e.g. The Courtyard"
          value={form.venueName} onChange={set('venueName')} error={errors.venueName} />
        <FormField label="Address" name="address" maxLength={300} placeholder="Street and area"
          value={form.address} onChange={set('address')} error={errors.address} />
        <FormField label="Starts" name="startsAt" type="datetime-local"
          value={form.startsAt} onChange={set('startsAt')} error={errors.startsAt} />
        <FormField label="Ends" name="endsAt" type="datetime-local" min={form.startsAt || undefined}
          value={form.endsAt} onChange={set('endsAt')} error={errors.endsAt} />
        <div className="field span-2">
          <label className="field-label" htmlFor="field-cutoff">Attendee cancellations</label>
          <select id="field-cutoff" className="input" value={form.cancellationCutoffHours} onChange={set('cancellationCutoffHours')}>
            <option value="0">Allowed until the event starts</option>
            <option value="2">Until 2 hours before</option>
            <option value="24">Until 24 hours before</option>
            <option value="48">Until 2 days before</option>
            <option value="168">Until 7 days before</option>
          </select>
          <span className="field-hint">Cancelled tickets go back on sale automatically.</span>
        </div>
      </fieldset>
      {!disabled && (
        <div className="span-2 row">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? <Spinner small /> : <Save size={16} />} {submitLabel}
          </button>
        </div>
      )}
    </form>
  )
}
