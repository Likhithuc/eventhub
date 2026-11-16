import { Info, Minus, Plus, Share2, Ticket } from 'lucide-react'
import { useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { createBooking } from '../../api/bookingApi'
import type { EventDetail, TicketTypeInfo } from '../../api/eventApi'
import { errorMessage } from '../../api/http'
import { Alert } from '../../components/ui/Alert'
import { Spinner } from '../../components/ui/Spinner'
import { formatMoney, formatPriceRange } from '../../lib/format'
import { useAuth } from '../auth/useAuth'

const MAX_PER_BOOKING = 10

/** Free tickets can be booked now; paid ones wait for online payments. */
const bookable = (t: TicketTypeInfo) => t.onSale && t.price === 0

function policyText(hours: number): string {
  if (hours === 0) return 'Free cancellation until the event starts.'
  if (hours % 24 === 0) return `Free cancellation up to ${hours / 24} day${hours === 24 ? '' : 's'} before the event.`
  return `Free cancellation up to ${hours} hours before the event.`
}

function availabilityText(t: TicketTypeInfo, ended: boolean): string {
  if (ended) return 'Sales closed'
  if (t.soldOut) return 'Sold out'
  if (!t.onSale) return 'Not on sale right now'
  if (t.price > 0) return 'Online payment coming soon'
  if (t.available <= 10) return `Only ${t.available} left`
  return `${t.available.toLocaleString('en-IN')} available`
}

export function BookingPanel({ event: e, onShare, copied }: { event: EventDetail; onShare: () => void; copied: boolean }) {
  const { status } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [qty, setQty] = useState<Record<string, number>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const total = Object.values(qty).reduce((a, b) => a + b, 0)
  const anyBookable = e.ticketTypes.some(bookable)
  const anyPaidOnSale = e.ticketTypes.some((t) => t.onSale && t.price > 0)

  // One idempotency key per distinct selection: retries reuse it, a new selection gets a new one.
  const selectionKey = useMemo(() => JSON.stringify(Object.entries(qty).filter(([, n]) => n > 0).sort()), [qty])
  const keyRef = useRef<{ selection: string; key: string } | null>(null)
  const idempotencyKey = () => {
    if (keyRef.current?.selection !== selectionKey) {
      keyRef.current = { selection: selectionKey, key: crypto.randomUUID() }
    }
    return keyRef.current.key
  }

  const change = (t: TicketTypeInfo, delta: number) => {
    setError(null)
    setQty((q) => {
      const current = q[t.id] ?? 0
      const others = total - current
      const next = Math.max(0, Math.min(current + delta, t.available, MAX_PER_BOOKING - others))
      return { ...q, [t.id]: next }
    })
  }

  const book = async () => {
    if (status !== 'authenticated') {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    setBusy(true)
    setError(null)
    try {
      const booking = await createBooking(
        {
          eventId: e.id,
          items: Object.entries(qty)
            .filter(([, n]) => n > 0)
            .map(([ticketTypeId, quantity]) => ({ ticketTypeId, quantity })),
        },
        idempotencyKey(),
      )
      navigate(`/bookings/${booking.id}`, { state: { justBooked: true } })
    } catch (err) {
      setError(errorMessage(err, 'Booking failed. Please try again.'))
      setBusy(false)
    }
  }

  return (
    <aside className="ticket-panel card">
      <div className="ticket-panel-head">
        <span className="muted small">Tickets</span>
        <strong className="ticket-panel-price">{formatPriceRange(e.price)}</strong>
      </div>
      {e.ticketTypes.length === 0 ? (
        <p className="muted small" style={{ padding: '0 20px 4px' }}>
          Ticket details will be announced soon.
        </p>
      ) : (
        <ul className="ticket-list">
          {e.ticketTypes.map((t) => {
            const n = qty[t.id] ?? 0
            const canAdd = bookable(t) && n < t.available && total < MAX_PER_BOOKING
            return (
              <li key={t.id} className={`ticket-row${bookable(t) ? '' : ' is-disabled'}`}>
                <div>
                  <strong>{t.name}</strong>
                  <span className={`small ${t.soldOut ? 'text-danger' : t.available <= 10 && bookable(t) ? 'text-warning' : 'muted'}`}>
                    {availabilityText(t, e.ended)}
                  </span>
                </div>
                <div className="ticket-row-end">
                  <span className={`ticket-row-price${t.price === 0 ? ' is-free' : ''}`}>
                    {t.price === 0 ? 'Free' : formatMoney(t.price, t.currency)}
                  </span>
                  {bookable(t) && (
                    <div className="stepper" role="group" aria-label={`${t.name} quantity`}>
                      <button type="button" onClick={() => change(t, -1)} disabled={n === 0 || busy} aria-label={`Fewer ${t.name}`}>
                        <Minus size={14} />
                      </button>
                      <output aria-live="polite">{n}</output>
                      <button type="button" onClick={() => change(t, 1)} disabled={!canAdd || busy} aria-label={`More ${t.name}`}>
                        <Plus size={14} />
                      </button>
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
      <div className="ticket-panel-foot">
        {error && <Alert>{error}</Alert>}
        {anyBookable && (
          <div className="booking-total">
            <span>{total === 0 ? 'Select tickets' : `${total} ticket${total === 1 ? '' : 's'}`}</span>
            <strong>{total === 0 ? '' : 'Free'}</strong>
          </div>
        )}
        <button type="button" className="btn btn-gradient btn-lg btn-block" disabled={!anyBookable || total === 0 || busy}
          onClick={book}>
          {busy ? <Spinner small /> : <Ticket size={18} />}{' '}
          {e.ended ? 'Event ended'
            : !anyBookable ? (anyPaidOnSale ? 'Online payment coming soon' : 'Not available')
              : status !== 'authenticated' ? 'Log in to book'
                : busy ? 'Booking…' : 'Book now'}
        </button>
        {anyBookable && total >= MAX_PER_BOOKING && (
          <p className="muted small" style={{ textAlign: 'center' }}>Up to {MAX_PER_BOOKING} tickets per booking.</p>
        )}
        {!e.ended && e.ticketTypes.length > 0 && (
          <p className="policy">
            <Info size={14} /> {policyText(e.cancellationCutoffHours)}
          </p>
        )}
        <button type="button" className="btn btn-ghost btn-block" onClick={onShare}>
          <Share2 size={16} /> {copied ? 'Link copied!' : 'Share event'}
        </button>
      </div>
    </aside>
  )
}
