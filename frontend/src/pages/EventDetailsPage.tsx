import {
  ArrowLeft,
  BadgeCheck,
  CalendarDays,
  Clock,
  MapPin,
  TicketX,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getEvent, type EventDetail } from '../api/eventApi'
import { ApiError, errorMessage } from '../api/http'
import { Alert } from '../components/ui/Alert'
import { Avatar } from '../components/ui/Avatar'
import { EmptyState } from '../components/ui/EmptyState'
import { LoadingBlock } from '../components/ui/Spinner'
import { categoryStyle } from '../features/events/categoryStyle'
import { BookingPanel } from '../features/bookings/BookingPanel'
import { formatEventWhen } from '../lib/format'

interface State {
  id: string
  event?: EventDetail
  notFound?: boolean
  error?: string
}

export function EventDetailsPage() {
  const { eventId = '' } = useParams()
  const [state, setState] = useState<State>({ id: '' })
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    const ctrl = new AbortController()
    getEvent(eventId, ctrl.signal)
      .then((event) => setState({ id: eventId, event }))
      .catch((err) => {
        if (ctrl.signal.aborted) return
        const notFound = err instanceof ApiError && (err.status === 404 || err.status === 400)
        setState({ id: eventId, notFound, error: notFound ? undefined : errorMessage(err, 'Could not load event') })
      })
    return () => ctrl.abort()
  }, [eventId])

  if (state.id !== eventId) return <LoadingBlock label="Loading event…" />

  if (state.notFound) {
    return (
      <div className="container page">
        <div className="card">
          <EmptyState
            icon={<TicketX size={28} />}
            title="Event not found"
            actions={
              <Link to="/events" className="btn btn-primary">
                Browse events
              </Link>
            }
          >
            This event doesn't exist, or it isn't available to the public.
          </EmptyState>
        </div>
      </div>
    )
  }
  if (state.error || !state.event) {
    return (
      <div className="container page">
        <Alert>{state.error ?? 'Could not load event'}</Alert>
      </div>
    )
  }

  const e = state.event
  const style = categoryStyle(e.category.slug)
  const Icon = style.icon
  const when = formatEventWhen(e.startsAt, e.endsAt)

  const share = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard blocked: ignore */
    }
  }

  return (
    <>
      <section className="event-hero" style={{ background: style.gradient }}>
        <Icon className="event-hero-icon" size={260} strokeWidth={1} aria-hidden="true" />
        <div className="container event-hero-inner">
          <Link to="/events" className="back-link">
            <ArrowLeft size={16} /> All events
          </Link>
          <Link to={`/events?category=${e.category.slug}`} className="badge event-hero-cat">
            <Icon size={13} /> {e.category.name}
          </Link>
          <h1>{e.title}</h1>
          <div className="event-hero-meta">
            <span>
              <CalendarDays size={17} /> {when.date}
            </span>
            <span>
              <MapPin size={17} /> {e.venueName}, {e.city}
            </span>
          </div>
        </div>
      </section>

      <div className="container page event-layout">
        <div className="stack" style={{ gap: 20 }}>
          {e.ended && <Alert tone="info">This event has ended.</Alert>}

          <div className="card card-body">
            <h2>About this event</h2>
            <p className="event-description">{e.description}</p>
          </div>

          <div className="card card-body">
            <h2>When and where</h2>
            <div className="info-rows">
              <div className="info-row">
                <div className="stat-icon">
                  <CalendarDays size={20} />
                </div>
                <div>
                  <strong>{when.date}</strong>
                  <span>
                    <Clock size={13} /> {when.time}
                  </span>
                </div>
              </div>
              <div className="info-row">
                <div className="stat-icon pink">
                  <MapPin size={20} />
                </div>
                <div>
                  <strong>{e.venueName}</strong>
                  <span>
                    {e.address}, {e.city}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="card card-body">
            <h2>Organizer</h2>
            <div className="organizer-row">
              <Avatar name={e.organizer.displayName} seed={e.organizer.id} size="lg" />
              <div>
                <strong className="organizer-name">
                  {e.organizer.displayName}
                  {e.organizer.verified && (
                    <span className="verified" title="Verified organizer">
                      <BadgeCheck size={17} /> Verified
                    </span>
                  )}
                </strong>
                {e.organizer.description && <p className="muted small">{e.organizer.description}</p>}
              </div>
            </div>
          </div>
        </div>

        <BookingPanel event={e} onShare={share} copied={copied} />
      </div>
    </>
  )
}
