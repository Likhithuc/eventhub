import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { AdminEventSummary } from '../../api/adminApi'
import { formatEventStart } from '../../lib/format'
import { categoryStyle } from '../events/categoryStyle'

export function PendingEventRow({ event: e }: { event: AdminEventSummary }) {
  const style = categoryStyle(e.category.slug)
  const Icon = style.icon
  return (
    <li>
      <Link to={`/admin/events/${e.id}`} className="queue-row">
        <span className="event-cell-icon" style={{ background: style.gradient }}>
          <Icon size={16} />
        </span>
        <span className="queue-main">
          <strong>{e.title}</strong>
          <span>
            {e.organizerName} · {e.city} · {formatEventStart(e.startsAt)}
          </span>
        </span>
        {e.submittedAt && (
          <span className="muted small queue-when">
            Submitted {new Date(e.submittedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
          </span>
        )}
        <ChevronRight size={18} className="muted" />
      </Link>
    </li>
  )
}
