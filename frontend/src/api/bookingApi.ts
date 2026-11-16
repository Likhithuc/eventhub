import type { CategoryRef } from './eventApi'
import { http } from './http'
import type { EventStatus } from './organizerApi'
import type { Page } from './types'

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED' | 'REFUNDED'
export type BookingScope = 'UPCOMING' | 'PAST' | 'CANCELLED'

export interface BookingEventInfo {
  id: string
  title: string
  category: CategoryRef
  venueName: string
  address: string
  city: string
  startsAt: string
  endsAt: string
  status: EventStatus
  organizerName: string
}

export interface Pass {
  id: string
  /** manual-entry code, e.g. 7K3M-9QX2 */
  code: string
  /** QR payload; null when the pass is cancelled */
  qr: string | null
  ticketTypeName: string
  status: 'VALID' | 'CHECKED_IN' | 'CANCELLED'
  checkedInAt: string | null
}

export interface Booking {
  id: string
  reference: string
  status: BookingStatus
  ticketCount: number
  totalAmount: number
  currency: string
  createdAt: string
  cancelledAt: string | null
  cancellationReason: string | null
  event: BookingEventInfo
  items: { ticketTypeId: string; name: string; quantity: number; unitPrice: number }[]
  tickets: Pass[]
  cancellationDeadline: string
  canCancel: boolean
}

export interface BookingSummary {
  id: string
  reference: string
  status: BookingStatus
  ticketCount: number
  totalAmount: number
  currency: string
  createdAt: string
  event: BookingEventInfo
}

export interface BookingRequest {
  eventId: string
  items: { ticketTypeId: string; quantity: number }[]
}

/**
 * Creates a booking. Pass the same idempotencyKey when retrying the same request,
 * so a network retry can never create a second booking.
 */
export function createBooking(req: BookingRequest, idempotencyKey: string) {
  return http<Booking>('/api/v1/bookings', {
    method: 'POST',
    body: req,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export const getBooking = (id: string, signal?: AbortSignal) => http<Booking>(`/api/v1/bookings/${id}`, { signal })
export const cancelBooking = (id: string) => http<Booking>(`/api/v1/bookings/${id}/cancel`, { method: 'POST' })
export const listMyBookings = (scope: BookingScope, page = 0, signal?: AbortSignal) =>
  http<Page<BookingSummary>>(`/api/v1/bookings/me?scope=${scope}&page=${page}&size=12`, { signal })

// Organizer side
export interface OrganizerBookings {
  summary: {
    confirmedBookings: number
    ticketsSold: number
    capacity: number
    ticketTypes: { id: string; name: string; price: number; totalQuantity: number; sold: number }[]
  }
  bookings: Page<{
    id: string
    reference: string
    status: BookingStatus
    attendeeName: string
    attendeeEmail: string
    ticketCount: number
    checkedIn: number
    items: string[]
    createdAt: string
    cancelledAt: string | null
  }>
}

export const getEventBookings = (eventId: string, page = 0, signal?: AbortSignal) =>
  http<OrganizerBookings>(`/api/v1/organizer/events/${eventId}/bookings?page=${page}&size=25`, { signal })
