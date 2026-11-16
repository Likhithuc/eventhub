import { http } from './http'
import type { Page } from './types'

export interface Category {
  id: string
  name: string
  slug: string
}

export interface CategoryRef {
  name: string
  slug: string
}

export interface PriceInfo {
  /** null when the event has no ticket types yet */
  min: number | null
  max: number | null
  currency: string
  /** every ticket is free */
  free: boolean
}

export interface EventSummary {
  id: string
  title: string
  category: CategoryRef
  venueName: string
  city: string
  startsAt: string
  endsAt: string
  organizerName: string
  price: PriceInfo
}

export interface TicketTypeInfo {
  id: string
  name: string
  price: number
  currency: string
  totalQuantity: number
  available: number
  salesStartAt: string | null
  salesEndAt: string | null
  /** bookable right now: sales window open and not sold out */
  onSale: boolean
  soldOut: boolean
}

export interface EventDetail {
  id: string
  title: string
  description: string
  category: CategoryRef
  venueName: string
  address: string
  city: string
  startsAt: string
  endsAt: string
  ended: boolean
  organizer: { id: string; displayName: string; description: string | null; verified: boolean }
  ticketTypes: TicketTypeInfo[]
  price: PriceInfo
  /** attendees may cancel until this many hours before the start */
  cancellationCutoffHours: number
}

export interface EventSearch {
  q?: string
  city?: string
  category?: string
  /** yyyy-mm-dd, inclusive */
  from?: string
  /** yyyy-mm-dd, inclusive */
  to?: string
  page?: number
  size?: number
}

export function searchEvents(params: EventSearch, signal?: AbortSignal): Promise<Page<EventSummary>> {
  const qs = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') qs.set(key, String(value))
  }
  return http<Page<EventSummary>>(`/api/v1/events?${qs}`, { signal })
}

export function getEvent(id: string, signal?: AbortSignal): Promise<EventDetail> {
  return http<EventDetail>(`/api/v1/events/${encodeURIComponent(id)}`, { signal })
}

export function listCategories(signal?: AbortSignal): Promise<Category[]> {
  return http<Category[]>('/api/v1/categories', { signal })
}

export function listCities(signal?: AbortSignal): Promise<string[]> {
  return http<string[]>('/api/v1/events/cities', { signal })
}
