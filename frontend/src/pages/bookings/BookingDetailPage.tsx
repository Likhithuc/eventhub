import { ArrowLeft, CalendarDays, CheckCircle2, Clock, Copy, MapPin, PartyPopper, QrCode, TicketX, XCircle } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { cancelBooking, getBooking, type Booking } from '../../api/bookingApi'
import { errorMessage } from '../../api/http'
import { Alert } from '../../components/ui/Alert'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock } from '../../components/ui/Spinner'
import { BookingStatusBadge } from '../../features/bookings/BookingStatusBadge'
import { TicketPass } from '../../features/bookings/TicketPass'
import { categoryStyle } from '../../features/events/categoryStyle'
import { formatEventWhen, formatMoney } from '../../lib/format'
import { useQuery } from '../../lib/useQuery'

/** Keyed by booking id so state never carries over between bookings. */
export function BookingDetailPage() {
  const { bookingId = '' } = useParams()
  return <BookingDetail key={bookingId} bookingId={bookingId} />
}

function BookingDetail({ bookingId }: { bookingId: string }) {
  const location = useLocation()
  const justBooked = (location.state as { justBooked?: boolean } | null)?.justBooked === true
  const query = useQuery(`booking-${bookingId}`, (signal) => getBooking(bookingId, signal))
  const [updated, setUpdated] = useState<Booking | null>(null)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  if (query.error) {
    return (
      <div className="container page">
        <div className="card">
          <EmptyState icon={<TicketX size={28} />} title="Booking not found"
            actions={<Link to="/bookings" className="btn btn-primary">My tickets</Link>}>
            This booking doesn't exist or belongs to another account.
          </EmptyState>
        </div>
      </div>
    )
  }
  const b = updated ?? query.data
  if (!b) return <LoadingBlock label="Loading your booking…" />

  const style = categoryStyle(b.event.category.slug)
  const Icon = style.icon
  const when = formatEventWhen(b.event.startsAt, b.event.endsAt)
  const active = b.status === 'CONFIRMED'
  const deadline = new Date(b.cancellationDeadline).toLocaleString('en-IN', {
    weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit',
  })

  const cancel = async () => {
    setBusy(true)
    setError(null)
    try {
      setUpdated(await cancelBooking(b.id))
    } catch (err) {
      setError(errorMessage(err, 'Could not cancel the booking'))
    } finally {
      setBusy(false)
      setConfirmCancel(false)
    }
  }

  const copyRef = async () => {
    try {
      await navigator.clipboard.writeText(b.reference)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* ignore */
    }
  }

  return (
    <div className="container page narrow-page">
      <Link to="/bookings" className="text-link"><ArrowLeft size={16} /> My tickets</Link>

      {justBooked && active && !updated && (
        <div className="booked-banner">
          <span className="booked-icon"><PartyPopper size={26} /></span>
          <div>
            <h1>You're going!</h1>
            <p>Your booking is confirmed and a confirmation email is on its way. Show your QR code at the entrance.</p>
          </div>
        </div>
      )}
      {error && <div style={{ marginTop: 16 }}><Alert>{error}</Alert></div>}

      <div className={`ticket-card${active ? '' : ' is-inactive'}`} style={{ marginTop: justBooked ? 0 : 18 }}>
        <div className="ticket-card-top" style={{ background: style.gradient }}>
          <Icon className="ticket-card-icon" size={120} strokeWidth={1.2} aria-hidden="true" />
          <BookingStatusBadge status={b.status} onDark />
          <h2>{b.event.title}</h2>
          <div className="ticket-card-meta">
            <span><CalendarDays size={15} /> {when.date}</span>
            <span><Clock size={15} /> {when.time}</span>
            <span><MapPin size={15} /> {b.event.venueName}, {b.event.address}, {b.event.city}</span>
          </div>
        </div>
        <div className="ticket-card-perf" aria-hidden="true" />
        <div className="ticket-card-body">
          <div className="ticket-ref">
            <div>
              <span className="muted small">Booking reference</span>
              <strong className="ref-code">{b.reference}</strong>
            </div>
            <button type="button" className="btn btn-ghost btn-sm" onClick={copyRef}>
              <Copy size={14} /> {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
          <div>
            <h3 className="passes-title">
              <QrCode size={17} /> Your tickets
            </h3>
            <p className="muted small" style={{ marginTop: 4 }}>
              Each person shows their own QR code (or types the code) at the entrance. Screenshots work too.
            </p>
            <div className="passes">
              {b.tickets.map((t, i) => (
                <TicketPass key={t.id} pass={t} index={i} total={b.tickets.length} />
              ))}
            </div>
          </div>

          <table className="receipt">
            <tbody>
              {b.items.map((i) => (
                <tr key={i.ticketTypeId}>
                  <td>{i.quantity} × {i.name}</td>
                  <td>{i.unitPrice === 0 ? 'Free' : formatMoney(i.unitPrice * i.quantity, b.currency)}</td>
                </tr>
              ))}
              <tr className="receipt-total">
                <td>Total · {b.ticketCount} ticket{b.ticketCount === 1 ? '' : 's'}</td>
                <td>{b.totalAmount === 0 ? 'Free' : formatMoney(b.totalAmount, b.currency)}</td>
              </tr>
            </tbody>
          </table>

          <p className="muted small">
            Booked {new Date(b.createdAt).toLocaleString('en-IN')} · organised by {b.event.organizerName}
          </p>

          {b.status === 'CANCELLED' && (
            <div className="reason-box">
              <strong><XCircle size={14} style={{ verticalAlign: '-2px' }} /> Cancelled</strong>
              <p>{b.cancellationReason ?? 'This booking was cancelled.'}{b.cancelledAt && ` · ${new Date(b.cancelledAt).toLocaleString('en-IN')}`}</p>
            </div>
          )}

          <div className="row" style={{ gap: 10 }}>
            <Link to={`/events/${b.event.id}`} className="btn btn-secondary">View event</Link>
            {b.canCancel && (
              <button type="button" className="btn btn-ghost danger-hover" onClick={() => setConfirmCancel(true)}>
                Cancel booking
              </button>
            )}
          </div>
          {active && (
            <p className="muted small policy-line">
              {b.canCancel ? (
                <><CheckCircle2 size={13} /> Free cancellation until {deadline}</>
              ) : (
                'The cancellation window for this event has closed.'
              )}
            </p>
          )}
        </div>
      </div>

      <ConfirmDialog open={confirmCancel} title="Cancel this booking?" confirmLabel="Cancel booking" tone="danger"
        busy={busy} onCancel={() => setConfirmCancel(false)} onConfirm={cancel}>
        Your {b.ticketCount} ticket{b.ticketCount === 1 ? '' : 's'} for <strong>{b.event.title}</strong> will be released
        for others to book. This can't be undone.
      </ConfirmDialog>
    </div>
  )
}
