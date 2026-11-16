import type { CategoryRef } from './eventApi'
import { http } from './http'
import type { EventStatus, OrganizerEvent, VerificationStatus } from './organizerApi'
import type { Page, Role, User, UserStatus } from './types'

export interface AdminOverview {
  users: { total: number; suspended: number; organizers: number; admins: number }
  organizers: { total: number; verified: number; pendingVerification: number }
  eventsByStatus: Record<EventStatus, number>
  pendingApproval: number
  upcomingPublished: number
  bookings: { confirmed: number; ticketsSold: number }
}

export type AuditAction =
  | 'EVENT_APPROVED'
  | 'EVENT_REJECTED'
  | 'EVENT_UNPUBLISHED'
  | 'USER_SUSPENDED'
  | 'USER_REACTIVATED'
  | 'ORGANIZER_VERIFIED'
  | 'ORGANIZER_VERIFICATION_REJECTED'
  | 'ORGANIZER_VERIFICATION_RESET'

export interface AuditEntry {
  id: string
  createdAt: string
  action: AuditAction
  targetType: 'EVENT' | 'USER' | 'ORGANIZER'
  targetId: string
  summary: string
  details: string | null
  actorId: string | null
  actorName: string | null
}

export interface AdminEventSummary {
  id: string
  title: string
  category: CategoryRef
  city: string
  startsAt: string
  status: EventStatus
  organizerId: string
  organizerName: string
  organizerVerification: VerificationStatus
  submittedAt: string | null
  reviewedAt: string | null
}

export interface AdminEventDetail {
  event: OrganizerEvent
  organizer: {
    id: string
    displayName: string
    description: string | null
    contactEmail: string | null
    verificationStatus: VerificationStatus
    userId: string
    userName: string
    userEmail: string
  }
  rejectionReason: string | null
  reviewedAt: string | null
  reviewedByName: string | null
  history: AuditEntry[]
}

export interface AdminOrganizerRow {
  id: string
  displayName: string
  description: string | null
  contactEmail: string | null
  verificationStatus: VerificationStatus
  createdAt: string
  userId: string
  userName: string
  userEmail: string
  events: number
  publishedEvents: number
}

const qs = (params: Record<string, string | number | undefined | null>) => {
  const s = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') s.set(k, String(v))
  return s.toString()
}

export const getAdminOverview = () => http<AdminOverview>('/api/v1/admin/overview')
export const getAuditLog = (page = 0, size = 20) => http<Page<AuditEntry>>(`/api/v1/admin/audit-log?${qs({ page, size })}`)

export const listAdminEvents = (p: { status?: EventStatus | ''; q?: string; page?: number; size?: number }, signal?: AbortSignal) =>
  http<Page<AdminEventSummary>>(`/api/v1/admin/events?${qs(p)}`, { signal })
export const getAdminEvent = (id: string, signal?: AbortSignal) =>
  http<AdminEventDetail>(`/api/v1/admin/events/${id}`, { signal })
export const approveEvent = (id: string) => http<AdminEventDetail>(`/api/v1/admin/events/${id}/approve`, { method: 'POST' })
export const rejectEvent = (id: string, reason: string) =>
  http<AdminEventDetail>(`/api/v1/admin/events/${id}/reject`, { method: 'POST', body: { reason } })
export const unpublishEvent = (id: string, reason: string) =>
  http<AdminEventDetail>(`/api/v1/admin/events/${id}/unpublish`, { method: 'POST', body: { reason } })

export const listUsers = (p: { q?: string; role?: Role | ''; status?: UserStatus | ''; page?: number; size?: number }, signal?: AbortSignal) =>
  http<Page<User>>(`/api/v1/admin/users?${qs(p)}`, { signal })
export const setUserStatus = (id: string, status: UserStatus) =>
  http<User>(`/api/v1/admin/users/${id}/status`, { method: 'PATCH', body: { status } })

export const listOrganizers = (p: { verification?: VerificationStatus | ''; page?: number; size?: number }, signal?: AbortSignal) =>
  http<Page<AdminOrganizerRow>>(`/api/v1/admin/organizers?${qs(p)}`, { signal })
export const setOrganizerVerification = (id: string, status: VerificationStatus) =>
  http<AdminOrganizerRow>(`/api/v1/admin/organizers/${id}/verification`, { method: 'PATCH', body: { status } })
