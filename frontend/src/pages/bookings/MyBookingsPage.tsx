import { CalendarDays, ChevronLeft, ChevronRight, MapPin, Ticket } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { listMyBookings, type BookingScope } from '../../api/bookingApi'
import { Alert } from '../../components/ui/Alert'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock } from '../../components/ui/Spinner'
import { BookingStatusBadge } from '../../features/bookings/BookingStatusBadge'
import { categoryStyle } from '../../features/events/categoryStyle'
import { dateBadge, formatEventStart } from '../../lib/format'
import { useQuery } from '../../lib/useQuery'

const TABS: { scope: BookingScope; label: string }[] = [
  { scope: 'UPCOMING', label: 'Upcoming' },
  { scope: 'PAST', label: 'Past' },
  { scope: 'CANCELLED', label: 'Cancelled' },
]

export function MyBookingsPage() {
  const [params, setParams] = useSearchParams()
  const scope = (params.get('tab') ?? 'UPCOMING').toUpperCase() as BookingScope
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const { data, error, loading } = useQuery(`mine|${scope}|${page}`, (signal) => listMyBookings(scope, page, signal))

  const setTab = (s: BookingScope) => setParams(s === 'UPCOMING' ? {} : { tab: s.toLowerCase() })
  const setPage = (p: number) => setParams({ ...(scope !== 'UPCOMING' ? { tab: scope.toLowerCase() } : {}), ...(p ? { page: String(p) } : {}) })

  return (
    <div className="container page">
      <div className="page-header">
        <div>
          <span className="eyebrow">Your tickets</span>
          <h1 style={{ marginTop: 6 }}>My bookings</h1>
          <p>Everything you've booked on EventHub.</p>
        </div>
      </div>

      <div className="tabs tabs-plain" role="tablist" aria-label="Booking filter">
        {TABS.map((t) => (
          <button key={t.scope} type="button" role="tab" aria-selected={scope === t.scope}
            className={`tab${scope === t.scope ? ' active' : ''}`} onClick={() => setTab(t.scope)}>
            {t.label}
          </button>
        ))}
      </div>

      {error && <Alert>{error}</Alert>}
      {!data && loading && <LoadingBlock />}

      {data && data.content.length === 0 && (
        <div className="card" style={{ marginTop: 18 }}>
          <EmptyState icon={<Ticket size={28} />}
            title={scope === 'UPCOMING' ? 'No upcoming tickets' : scope === 'PAST' ? 'No past events yet' : 'No cancelled bookings'}
            actions={scope === 'UPCOMING' && <Link to="/events" className="btn btn-primary">Find events</Link>}>
            {scope === 'UPCOMING' ? 'When you book an event, your tickets will appear here.' : 'Nothing to show here.'}
          </EmptyState>
        </div>
      )}

      {data && data.content.length > 0 && (
        <>
          <ul className="booking-list" style={{ opacity: loading ? 0.55 : 1 }}>
            {data.content.map((b) => {
              const style = categoryStyle(b.event.category.slug)
              const badge = dateBadge(b.event.startsAt)
              return (
                <li key={b.id}>
                  <Link to={`/bookings/${b.id}`} className="booking-row card">
                    <span className="booking-date" style={{ background: style.gradient }}>
                      <span>{badge.month}</span>
                      <strong>{badge.day}</strong>
                    </span>
                    <span className="booking-main">
                      <strong>{b.event.title}</strong>
                      <span><CalendarDays size={13} /> {formatEventStart(b.event.startsAt)}</span>
                      <span><MapPin size={13} /> {b.event.venueName}, {b.event.city}</span>
                    </span>
                    <span className="booking-side">
                      <BookingStatusBadge status={b.status} />
                      <span className="small muted">{b.ticketCount} ticket{b.ticketCount === 1 ? '' : 's'} · {b.reference}</span>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
          {data.totalPages > 1 && (
            <div className="pager pager-plain">
              <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                <ChevronLeft size={16} /> Previous
              </button>
              <span className="muted small">Page {data.page + 1} of {data.totalPages}</span>
              <button type="button" className="btn btn-secondary btn-sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Next <ChevronRight size={16} />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
