import { BadgeCheck, Clock, XCircle } from 'lucide-react'
import type { VerificationStatus } from '../../api/organizerApi'

export function VerificationBadge({ status }: { status: VerificationStatus }) {
  if (status === 'VERIFIED')
    return (
      <span className="badge badge-organizer">
        <BadgeCheck size={13} /> Verified organizer
      </span>
    )
  if (status === 'REJECTED')
    return (
      <span className="badge badge-admin">
        <XCircle size={13} /> Verification declined
      </span>
    )
  return (
    <span className="badge badge-suspended" title="An admin will review your organizer profile">
      <Clock size={13} /> Verification pending
    </span>
  )
}
