import type { PriceInfo } from '../api/eventApi'

export function formatMoney(amount: number, currency = 'INR'): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: amount % 1 === 0 ? 0 : 2,
  }).format(amount)
}

/** "Free", "From ₹499", "Free – ₹1,500", or "Tickets TBA". */
export function formatPriceRange(price: PriceInfo): string {
  if (price.min === null || price.max === null) return 'Tickets TBA'
  if (price.free) return 'Free'
  if (price.min === 0) return `Free – ${formatMoney(price.max, price.currency)}`
  if (price.min === price.max) return formatMoney(price.min, price.currency)
  return `From ${formatMoney(price.min, price.currency)}`
}

const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString()

/** "Sat, 12 Dec · 7:30 PM" */
export function formatEventStart(iso: string): string {
  const d = new Date(iso)
  const date = d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })
  const time = d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
  return `${date} · ${time}`
}

/** "Saturday, 12 December 2026" and "7:30 PM – 11:30 PM" (or a multi-day range). */
export function formatEventWhen(startIso: string, endIso: string): { date: string; time: string } {
  const s = new Date(startIso)
  const e = new Date(endIso)
  const longDate = (d: Date) =>
    d.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  const time = (d: Date) => d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
  if (sameDay(s, e)) return { date: longDate(s), time: `${time(s)} – ${time(e)}` }
  return {
    date: `${s.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })} – ${e.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    })}`,
    time: `Starts ${time(s)}`,
  }
}

/** Parts for the calendar-style date badge on event cards. */
export function dateBadge(iso: string): { month: string; day: string } {
  const d = new Date(iso)
  return {
    month: d.toLocaleDateString('en-IN', { month: 'short' }).toUpperCase(),
    day: String(d.getDate()),
  }
}

/** Local calendar date as yyyy-mm-dd (not UTC), for date filters. */
export function toIsoDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function addDays(d: Date, days: number): Date {
  const copy = new Date(d)
  copy.setDate(copy.getDate() + days)
  return copy
}

/** ISO instant -> value for <input type="datetime-local"> in the viewer's timezone. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** <input type="datetime-local"> value (viewer's timezone) -> ISO instant, or null when empty. */
export function fromLocalInput(value: string): string | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d.toISOString()
}
