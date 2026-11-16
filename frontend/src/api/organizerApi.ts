import type { CategoryRef } from './eventApi'
import { http } from './http'
import type { Page } from './types'

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'
export type EventStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'PUBLISHED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED'

export interface OrganizerProfile {
  id: string
  displayName: string
  description: string | null
  contactEmail: string | null
  verificationStatus: VerificationStatus
  createdAt: string
}

export interface OrganizerProfileInput {
  displayName: string
  description?: string
  contactEmail?: string
}

export interface OrganizerStats {
  total: number
  draft: number
  pendingApproval: number
  published: number
  rejected: number
  cancelled: number
  completed: number
  upcoming: number
}

export interface OrganizerDashboard {
  /** null until the user creates an organizer profile */
  profile: OrganizerProfile | null
  stats: OrganizerStats
}

export interface OrganizerEventSummary {
  id: string
  title: string
  category: CategoryRef
  city: string
  startsAt: string
  endsAt: string
  status: EventStatus
  ticketTypes: number
  capacity: number
}

export interface OrganizerTicketType {
  id: string
  name: string
  price: number
  currency: string
  totalQuantity: number
  salesStartAt: string | null
  salesEndAt: string | null
  status: 'ACTIVE' | 'INACTIVE'
  soldQuantity: number
}

export interface OrganizerEvent {
  id: string
  title: string
  description: string
  category: CategoryRef
  venueName: string
  address: string
  city: string
  startsAt: string
  endsAt: string
  status: EventStatus
  editable: boolean
  ticketTypes: OrganizerTicketType[]
  createdAt: string
  submittedAt: string | null
  reviewedAt: string | null
  /** Admin's reason when the event was rejected or unpublished */
  rejectionReason: string | null
  cancellationCutoffHours: number
}

export interface EventInput {
  title: string
  description: string
  category: string
  venueName: string
  address: string
  city: string
  /** ISO instant */
  startsAt: string
  /** ISO instant */
  endsAt: string
  cancellationCutoffHours?: number
}

export interface TicketTypeInput {
  name: string
  price: number
  totalQuantity: number
  salesStartAt?: string | null
  salesEndAt?: string | null
}

const BASE = '/api/v1/organizer/events'

export const getOrganizerDashboard = () => http<OrganizerDashboard>('/api/v1/organizer/dashboard')

export const getMyOrganizerProfile = () => http<OrganizerProfile>('/api/v1/me/organizer-profile')
export const createOrganizerProfile = (input: OrganizerProfileInput) =>
  http<OrganizerProfile>('/api/v1/me/organizer-profile', { method: 'POST', body: input })
export const updateOrganizerProfile = (input: OrganizerProfileInput) =>
  http<OrganizerProfile>('/api/v1/me/organizer-profile', { method: 'PUT', body: input })

export function listMyEvents(status: EventStatus | '' = '', page = 0, size = 20, signal?: AbortSignal) {
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) qs.set('status', status)
  return http<Page<OrganizerEventSummary>>(`${BASE}?${qs}`, { signal })
}

export const getMyEvent = (id: string, signal?: AbortSignal) => http<OrganizerEvent>(`${BASE}/${id}`, { signal })
export const createEvent = (input: EventInput) => http<OrganizerEvent>(BASE, { method: 'POST', body: input })
export const updateEvent = (id: string, input: EventInput) =>
  http<OrganizerEvent>(`${BASE}/${id}`, { method: 'PUT', body: input })
export const deleteEvent = (id: string) => http<void>(`${BASE}/${id}`, { method: 'DELETE' })

export const submitEvent = (id: string) => http<OrganizerEvent>(`${BASE}/${id}/submit`, { method: 'POST' })
export const withdrawEvent = (id: string) => http<OrganizerEvent>(`${BASE}/${id}/withdraw`, { method: 'POST' })
export const cancelEvent = (id: string) => http<OrganizerEvent>(`${BASE}/${id}/cancel`, { method: 'POST' })

export const addTicketType = (eventId: string, input: TicketTypeInput) =>
  http<OrganizerEvent>(`${BASE}/${eventId}/ticket-types`, { method: 'POST', body: input })
export const updateTicketType = (eventId: string, ticketId: string, input: TicketTypeInput) =>
  http<OrganizerEvent>(`${BASE}/${eventId}/ticket-types/${ticketId}`, { method: 'PUT', body: input })
export const deleteTicketType = (eventId: string, ticketId: string) =>
  http<OrganizerEvent>(`${BASE}/${eventId}/ticket-types/${ticketId}`, { method: 'DELETE' })
