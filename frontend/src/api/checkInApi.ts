import { ApiError, http } from './http'

export type CheckInResult =
  | 'CHECKED_IN'
  | 'ALREADY_CHECKED_IN'
  | 'CANCELLED'
  | 'NOT_FOUND'
  | 'TOO_EARLY'
  | 'EVENT_ENDED'
  | 'NOT_PUBLISHED'

export interface CheckInResponse {
  result: CheckInResult
  message: string
  ticket: { code: string; ticketType: string; attendeeName: string; bookingReference: string } | null
  checkedInAt: string | null
  checkedInBy: string | null
}

export interface CheckInSummary {
  validTickets: number
  checkedIn: number
  opensAt: string
  closesAt: string
  open: boolean
  recent: { code: string; ticketType: string; attendeeName: string; checkedInAt: string }[]
}

/**
 * Validates a ticket at the door. The backend answers 200 / 404 / 409 with the same body
 * shape, so non-2xx responses are unwrapped into a normal result here.
 */
export async function checkIn(eventId: string, code: string): Promise<CheckInResponse> {
  try {
    return await http<CheckInResponse>(`/api/v1/organizer/events/${eventId}/check-ins`, { method: 'POST', body: { code } })
  } catch (err) {
    const body = err instanceof ApiError ? (err.body as Partial<CheckInResponse> | null) : null
    if (body && typeof body.result === 'string') return body as CheckInResponse
    throw err
  }
}

export const getCheckInSummary = (eventId: string, signal?: AbortSignal) =>
  http<CheckInSummary>(`/api/v1/organizer/events/${eventId}/check-ins/summary`, { signal })
