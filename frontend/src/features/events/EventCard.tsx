import { Clock, MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { EventSummary } from '../../api/eventApi'
import { dateBadge, formatEventStart, formatPriceRange } from '../../lib/format'
import { categoryStyle } from './categoryStyle'

export function EventCard({ event }: { event: EventSummary }) {
  const style = categoryStyle(event.category.slug)
  const Icon = style.icon
  const badge = dateBadge(event.startsAt)
  const price = formatPriceRange(event.price)

  return (
    <Link to={`/events/${event.id}`} className="event-card">
      <div className="event-card-art" style={{ background: style.gradient }}>
        <Icon className="event-card-icon" size={92} strokeWidth={1.4} aria-hidden="true" />
        <div className="date-badge" aria-hidden="true">
          <span>{badge.month}</span>
          <strong>{badge.day}</strong>
        </div>
        <span className="badge event-card-cat">{event.category.name}</span>
      </div>
      <div className="event-card-body">
        <h3 className="event-card-title">{event.title}</h3>
        <div className="event-card-meta">
          <span>
            <Clock size={14} /> {formatEventStart(event.startsAt)}
          </span>
          <span>
            <MapPin size={14} /> {event.venueName}, {event.city}
          </span>
        </div>
        <div className="event-card-foot">
          <span className={`event-card-price${event.price.free ? ' is-free' : ''}`}>{price}</span>
          <span className="event-card-org">by {event.organizerName}</span>
        </div>
      </div>
    </Link>
  )
}

export function EventCardSkeleton() {
  return (
    <div className="event-card" aria-hidden="true">
      <div className="skeleton" style={{ height: 150, borderRadius: 0 }} />
      <div className="event-card-body">
        <div className="skeleton" style={{ height: 18, width: '80%' }} />
        <div className="skeleton" style={{ height: 12, width: '60%', marginTop: 12 }} />
        <div className="skeleton" style={{ height: 12, width: '70%', marginTop: 8 }} />
        <div className="skeleton" style={{ height: 16, width: '35%', marginTop: 18 }} />
      </div>
    </div>
  )
}
