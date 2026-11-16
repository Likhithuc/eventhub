import { ChevronLeft, ChevronRight, Users } from 'lucide-react'
import { useState } from 'react'
import { getEventBookings } from '../../api/bookingApi'
import { Alert } from '../../components/ui/Alert'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock } from '../../components/ui/Spinner'
import { useQuery } from '../../lib/useQuery'
import { BookingStatusBadge } from '../bookings/BookingStatusBadge'

/** Ticket sales per type and the attendee list for an organizer's event. */
export function EventSalesSection({ eventId }: { eventId: string }) {
  const [page, setPage] = useState(0)
  const { data, error, loading } = useQuery(`sales|${eventId}|${page}`, (signal) => getEventBookings(eventId, page, signal))

  if (error) return <Alert>{error}</Alert>
  if (!data) return <div className="card"><LoadingBlock /></div>
  const { summary, bookings } = data
  const pct = summary.capacity ? Math.round((summary.ticketsSold / summary.capacity) * 100) : 0

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2>Sales & attendees</h2>
          <p className="muted small" style={{ marginTop: 2 }}>
            {summary.ticketsSold.toLocaleString('en-IN')} of {summary.capacity.toLocaleString('en-IN')} tickets booked ({pct}%) ·{' '}
            {summary.confirmedBookings} booking{summary.confirmedBookings === 1 ? '' : 's'}
          </p>
        </div>
      </div>
      <div className="card-body stack" style={{ gap: 12 }}>
        {summary.ticketTypes.map((t) => {
          const p = t.totalQuantity ? (t.sold / t.totalQuantity) * 100 : 0
          return (
            <div key={t.id} className="sales-row">
              <div className="sales-row-head">
                <strong>{t.name}</strong>
                <span className="muted small">{t.sold} / {t.totalQuantity}{t.sold >= t.totalQuantity ? ' · sold out' : ''}</span>
              </div>
              <div className="meter" role="progressbar" aria-valuenow={t.sold} aria-valuemin={0} aria-valuemax={t.totalQuantity}
                aria-label={`${t.name} sold`}>
                <span style={{ width: `${p}%` }} />
              </div>
            </div>
          )
        })}
      </div>

      {bookings.content.length === 0 ? (
        <EmptyState icon={<Users size={28} />} title="No bookings yet">Attendees will appear here as they book.</EmptyState>
      ) : (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.55 : 1 }}>
            <table className="table">
              <thead><tr><th>Attendee</th><th>Tickets</th><th>Checked in</th><th>Reference</th><th>Status</th><th>Booked</th></tr></thead>
              <tbody>
                {bookings.content.map((b) => (
                  <tr key={b.id}>
                    <td><div className="user-cell-text"><strong>{b.attendeeName}</strong><span>{b.attendeeEmail}</span></div></td>
                    <td>{b.items.join(', ')}</td>
                    <td className={b.checkedIn > 0 ? 'text-success' : 'muted'}>{b.status === 'CONFIRMED' ? `${b.checkedIn} / ${b.ticketCount}` : '—'}</td>
                    <td className="mono">{b.reference}</td>
                    <td><BookingStatusBadge status={b.status} /></td>
                    <td className="muted" title={new Date(b.createdAt).toLocaleString('en-IN')}>
                      {new Date(b.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {bookings.totalPages > 1 && (
            <div className="pager">
              <span className="muted small">{bookings.totalElements} bookings</span>
              <div className="row" style={{ gap: 8 }}>
                <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft size={16} /> Previous</button>
                <button type="button" className="btn btn-secondary btn-sm" disabled={page + 1 >= bookings.totalPages} onClick={() => setPage(page + 1)}>Next <ChevronRight size={16} /></button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
