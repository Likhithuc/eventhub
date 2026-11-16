import type { BookingStatus } from '../../api/bookingApi'

const LABEL: Record<BookingStatus, string> = {
  PENDING: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  REFUNDED: 'Refunded',
}

export function BookingStatusBadge({ status, onDark }: { status: BookingStatus; onDark?: boolean }) {
  const tone = status === 'CONFIRMED' ? 'status-published' : status === 'PENDING' ? 'status-pending-approval' : 'status-cancelled'
  return <span className={`badge badge-dot ${onDark ? 'badge-on-dark' : tone}`}>{LABEL[status]}</span>
}
