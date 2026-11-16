import type { EventStatus } from '../../api/organizerApi'

const STATUS_LABEL: Record<EventStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  PUBLISHED: 'Published',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
}

export function EventStatusBadge({ status }: { status: EventStatus }) {
  return <span className={`badge badge-dot status-${status.toLowerCase().replace('_', '-')}`}>{STATUS_LABEL[status]}</span>
}
