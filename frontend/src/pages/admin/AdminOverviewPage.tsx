import { ArrowRight, BadgeCheck, CalendarCheck, ClipboardCheck, Ticket, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getAdminOverview, getAuditLog, listAdminEvents } from '../../api/adminApi'
import { Alert } from '../../components/ui/Alert'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock } from '../../components/ui/Spinner'
import { StatCard } from '../../components/ui/StatCard'
import { AuditList } from '../../features/admin/AuditList'
import { PendingEventRow } from '../../features/admin/PendingEventRow'
import { useQuery } from '../../lib/useQuery'

export function AdminOverviewPage() {
  const overview = useQuery('overview', () => getAdminOverview())
  const pending = useQuery('pending-preview', (signal) =>
    listAdminEvents({ status: 'PENDING_APPROVAL', size: 5 }, signal),
  )
  const activity = useQuery('activity-preview', () => getAuditLog(0, 8))

  if (overview.error) return <Alert>{overview.error}</Alert>
  if (!overview.data) return <LoadingBlock />
  const o = overview.data

  return (
    <>
      <div className="stats">
        <Link to="/admin/events" className="stat-link">
          <StatCard icon={<ClipboardCheck size={20} />} label="Awaiting approval" value={o.pendingApproval} tone="amber" />
        </Link>
        <StatCard icon={<CalendarCheck size={20} />} label="Live & upcoming events" value={o.upcomingPublished} tone="green" />
        <StatCard icon={<Ticket size={20} />} label={`Tickets booked · ${o.bookings.confirmed} bookings`} value={o.bookings.ticketsSold} />
        <Link to="/admin/users" className="stat-link">
          <StatCard
            icon={<Users size={20} />}
            label={`Users${o.users.suspended ? ` · ${o.users.suspended} suspended` : ''}`}
            value={o.users.total}
          />
        </Link>
        <Link to="/admin/organizers" className="stat-link">
          <StatCard
            icon={<BadgeCheck size={20} />}
            label={`Organizers · ${o.organizers.pendingVerification} to verify`}
            value={o.organizers.total}
            tone="pink"
          />
        </Link>
      </div>

      <div className="admin-grid">
        <div className="card">
          <div className="card-header">
            <h2>Approval queue</h2>
            {o.pendingApproval > 0 && (
              <Link to="/admin/events" className="text-link small">
                Review all {o.pendingApproval} <ArrowRight size={14} />
              </Link>
            )}
          </div>
          {pending.data && pending.data.content.length === 0 ? (
            <EmptyState icon={<ClipboardCheck size={28} />} title="All caught up">
              No events are waiting for approval.
            </EmptyState>
          ) : !pending.data ? (
            <LoadingBlock />
          ) : (
            <ul className="queue">
              {pending.data.content.map((e) => (
                <PendingEventRow key={e.id} event={e} />
              ))}
            </ul>
          )}
        </div>

        <div className="card">
          <div className="card-header">
            <h2>Recent activity</h2>
            <Link to="/admin/activity" className="text-link small">
              View all <ArrowRight size={14} />
            </Link>
          </div>
          <div className="card-body" style={{ paddingTop: 8 }}>
            {activity.data ? <AuditList entries={activity.data.content} compact /> : <LoadingBlock />}
          </div>
        </div>
      </div>
    </>
  )
}
