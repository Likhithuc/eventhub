import type { Role, UserStatus } from '../../api/types'

const ROLE_LABEL: Record<Role, string> = { ADMIN: 'Admin', ORGANIZER: 'Organizer', ATTENDEE: 'Attendee' }
const ROLE_ORDER: Role[] = ['ADMIN', 'ORGANIZER', 'ATTENDEE']

export function RoleBadges({ roles }: { roles: Role[] }) {
  const sorted = [...roles].sort((a, b) => ROLE_ORDER.indexOf(a) - ROLE_ORDER.indexOf(b))
  return (
    <span className="badges">
      {sorted.map((r) => (
        <span key={r} className={`badge badge-${r.toLowerCase()}`}>
          {ROLE_LABEL[r]}
        </span>
      ))}
    </span>
  )
}

export function StatusBadge({ status }: { status: UserStatus }) {
  return (
    <span className={`badge badge-dot badge-${status.toLowerCase()}`}>
      {status === 'ACTIVE' ? 'Active' : 'Suspended'}
    </span>
  )
}
