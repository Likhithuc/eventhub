import {
  BadgeCheck,
  CircleCheck,
  CircleSlash,
  EyeOff,
  RotateCcw,
  UserCheck,
  UserX,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import type { AuditAction, AuditEntry } from '../../api/adminApi'

const ACTIONS: Record<AuditAction, { label: string; icon: LucideIcon; tone: string }> = {
  EVENT_APPROVED: { label: 'approved event', icon: CircleCheck, tone: 'green' },
  EVENT_REJECTED: { label: 'rejected event', icon: XCircle, tone: 'red' },
  EVENT_UNPUBLISHED: { label: 'unpublished event', icon: EyeOff, tone: 'red' },
  USER_SUSPENDED: { label: 'suspended user', icon: UserX, tone: 'red' },
  USER_REACTIVATED: { label: 'reactivated user', icon: UserCheck, tone: 'green' },
  ORGANIZER_VERIFIED: { label: 'verified organizer', icon: BadgeCheck, tone: 'green' },
  ORGANIZER_VERIFICATION_REJECTED: { label: 'declined verification for', icon: CircleSlash, tone: 'red' },
  ORGANIZER_VERIFICATION_RESET: { label: 'reset verification for', icon: RotateCcw, tone: 'amber' },
}

function timeAgo(iso: string): string {
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'just now'
  const m = Math.round(s / 60)
  if (m < 60) return `${m} min ago`
  const h = Math.round(m / 60)
  if (h < 24) return `${h} h ago`
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
}

export function AuditList({ entries, compact }: { entries: AuditEntry[]; compact?: boolean }) {
  if (entries.length === 0) return <p className="muted small audit-empty">No admin activity yet.</p>
  return (
    <ul className={`audit${compact ? ' audit-compact' : ''}`}>
      {entries.map((e) => {
        const a = ACTIONS[e.action]
        const Icon = a.icon
        return (
          <li key={e.id}>
            <span className={`audit-icon tone-${a.tone}`}>
              <Icon size={15} />
            </span>
            <div className="audit-body">
              <div>
                <strong>{e.actorName ?? 'System'}</strong> {a.label}{' '}
                {e.targetType === 'EVENT' ? (
                  <Link to={`/admin/events/${e.targetId}`}>{e.summary}</Link>
                ) : (
                  <strong>{e.summary}</strong>
                )}
              </div>
              {e.details && <div className="audit-details">“{e.details}”</div>}
              <time className="muted small" dateTime={e.createdAt} title={new Date(e.createdAt).toLocaleString()}>
                {timeAgo(e.createdAt)}
              </time>
            </div>
          </li>
        )
      })}
    </ul>
  )
}
