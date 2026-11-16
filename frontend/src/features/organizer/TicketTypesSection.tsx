import { Pencil, Plus, Ticket, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/http'
import {
  addTicketType,
  deleteTicketType,
  updateTicketType,
  type OrganizerEvent,
  type OrganizerTicketType,
  type TicketTypeInput,
} from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { FormField } from '../../components/ui/FormField'
import { Spinner } from '../../components/ui/Spinner'
import { formatMoney, fromLocalInput, toLocalInput } from '../../lib/format'

interface Props {
  event: OrganizerEvent
  onChange: (event: OrganizerEvent) => void
}

export function TicketTypesSection({ event, onChange }: Props) {
  // null = closed, 'new' = adding, otherwise the id being edited
  const [editing, setEditing] = useState<string | null>(null)
  const [toDelete, setToDelete] = useState<OrganizerTicketType | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const capacity = event.ticketTypes.reduce((sum, t) => sum + t.totalQuantity, 0)

  const remove = async () => {
    if (!toDelete) return
    setDeleting(true)
    setError(null)
    try {
      onChange(await deleteTicketType(event.id, toDelete.id))
      setToDelete(null)
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the ticket type'))
      setToDelete(null)
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2>Ticket types</h2>
          <p className="muted small" style={{ marginTop: 2 }}>
            {event.ticketTypes.length === 0
              ? 'Add at least one ticket type before submitting.'
              : `${event.ticketTypes.length} type${event.ticketTypes.length === 1 ? '' : 's'} · ${capacity.toLocaleString('en-IN')} total capacity`}
          </p>
        </div>
        {event.editable && editing === null && (
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing('new')}>
            <Plus size={16} /> Add ticket type
          </button>
        )}
      </div>

      {error && (
        <div className="card-body" style={{ paddingBottom: 0 }}>
          <Alert>{error}</Alert>
        </div>
      )}

      {event.ticketTypes.length === 0 && editing !== 'new' && (
        <div className="ticket-empty">
          <Ticket size={22} />
          <span>No ticket types yet{event.editable ? ' — e.g. General, VIP or Early Bird.' : '.'}</span>
        </div>
      )}

      <ul className="tt-list">
        {event.ticketTypes.map((t) =>
          editing === t.id ? (
            <li key={t.id} className="tt-edit">
              <TicketTypeForm
                initial={t}
                onCancel={() => setEditing(null)}
                onSave={async (input) => {
                  onChange(await updateTicketType(event.id, t.id, input))
                  setEditing(null)
                }}
              />
            </li>
          ) : (
            <li key={t.id} className="tt-row">
              <div className="tt-main">
                <strong>{t.name}</strong>
                <span className="muted small">
                  {t.soldQuantity > 0 ? `${t.soldQuantity} of ${t.totalQuantity.toLocaleString('en-IN')} booked` : `${t.totalQuantity.toLocaleString('en-IN')} available`}
                  {t.salesEndAt && ` · sales end ${new Date(t.salesEndAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}`}
                  {t.salesStartAt && ` · from ${new Date(t.salesStartAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}`}
                </span>
              </div>
              <span className={`tt-price${t.price === 0 ? ' is-free' : ''}`}>
                {t.price === 0 ? 'Free' : formatMoney(t.price, t.currency)}
              </span>
              {event.editable && editing === null && (
                <div className="tt-actions">
                  <button type="button" className="btn btn-ghost btn-icon btn-sm" aria-label={`Edit ${t.name}`} onClick={() => setEditing(t.id)}>
                    <Pencil size={15} />
                  </button>
                  <button type="button" className="btn btn-ghost btn-icon btn-sm danger-hover" aria-label={`Delete ${t.name}`} onClick={() => setToDelete(t)}>
                    <Trash2 size={15} />
                  </button>
                </div>
              )}
            </li>
          ),
        )}
        {editing === 'new' && (
          <li className="tt-edit">
            <TicketTypeForm
              onCancel={() => setEditing(null)}
              onSave={async (input) => {
                onChange(await addTicketType(event.id, input))
                setEditing(null)
              }}
            />
          </li>
        )}
      </ul>

      <ConfirmDialog
        open={toDelete !== null}
        title={`Delete "${toDelete?.name}"?`}
        confirmLabel="Delete ticket type"
        tone="danger"
        busy={deleting}
        onCancel={() => setToDelete(null)}
        onConfirm={remove}
      >
        This removes the ticket type from the event.
      </ConfirmDialog>
    </div>
  )
}

function TicketTypeForm({
  initial,
  onSave,
  onCancel,
}: {
  initial?: OrganizerTicketType
  onSave: (input: TicketTypeInput) => Promise<void>
  onCancel: () => void
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [price, setPrice] = useState(initial ? String(initial.price) : '0')
  const [quantity, setQuantity] = useState(initial ? String(initial.totalQuantity) : '100')
  const [showWindow, setShowWindow] = useState(!!(initial?.salesStartAt || initial?.salesEndAt))
  const [salesStart, setSalesStart] = useState(toLocalInput(initial?.salesStartAt))
  const [salesEnd, setSalesEnd] = useState(toLocalInput(initial?.salesEndAt))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const errs: Record<string, string> = {}
    const priceNum = Number(price)
    const qtyNum = Number(quantity)
    if (!name.trim()) errs.name = 'Enter a name'
    if (!/^\d+(\.\d{1,2})?$/.test(price.trim())) errs.price = 'Enter 0 or more, with at most 2 decimals'
    if (!Number.isInteger(qtyNum) || qtyNum < 1) errs.totalQuantity = 'Enter a whole number, 1 or more'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      await onSave({
        name: name.trim(),
        price: priceNum,
        totalQuantity: qtyNum,
        salesStartAt: showWindow ? fromLocalInput(salesStart) : null,
        salesEndAt: showWindow ? fromLocalInput(salesEnd) : null,
      })
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors)
      setError(errorMessage(err, 'Could not save the ticket type'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate className="stack" style={{ gap: 14 }}>
      {error && <Alert>{error}</Alert>}
      <div className="tt-form-grid">
        <FormField label="Name" name="tt-name" placeholder="General" maxLength={80} value={name}
          onChange={(e) => setName(e.target.value)} error={errors.name} autoFocus />
        <FormField label="Price (₹)" name="tt-price" type="number" min={0} step="0.01" inputMode="decimal"
          value={price} onChange={(e) => setPrice(e.target.value)} error={errors.price} hint="0 for free" />
        <FormField label="Quantity" name="tt-qty" type="number" min={1} step={1} inputMode="numeric"
          value={quantity} onChange={(e) => setQuantity(e.target.value)} error={errors.totalQuantity} />
      </div>
      <label className="check">
        <input type="checkbox" checked={showWindow} onChange={(e) => setShowWindow(e.target.checked)} />
        Limit when this ticket is on sale
      </label>
      {showWindow && (
        <div className="tt-form-grid tt-form-grid-2">
          <FormField label="Sales start" name="tt-start" type="datetime-local" value={salesStart}
            onChange={(e) => setSalesStart(e.target.value)} hint="Empty: on sale immediately" />
          <FormField label="Sales end" name="tt-end" type="datetime-local" value={salesEnd}
            onChange={(e) => setSalesEnd(e.target.value)} hint="Empty: until the event starts" />
        </div>
      )}
      <div className="row" style={{ gap: 8 }}>
        <button type="submit" className="btn btn-primary btn-sm" disabled={busy}>
          {busy && <Spinner small />} {initial ? 'Save ticket type' : 'Add ticket type'}
        </button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  )
}
