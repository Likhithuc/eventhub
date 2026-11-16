import { CalendarDays, ChevronLeft, ChevronRight, MapPin, Search, SearchX, X } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listCategories, listCities, searchEvents, type Category, type EventSummary } from '../api/eventApi'
import { errorMessage } from '../api/http'
import type { Page } from '../api/types'
import { Alert } from '../components/ui/Alert'
import { EmptyState } from '../components/ui/EmptyState'
import { EventCard, EventCardSkeleton } from '../features/events/EventCard'
import { categoryStyle } from '../features/events/categoryStyle'
import { addDays, toIsoDate } from '../lib/format'

const PAGE_SIZE = 12

const WHEN_OPTIONS = [
  { value: '', label: 'Any date' },
  { value: 'today', label: 'Today' },
  { value: 'weekend', label: 'This weekend' },
  { value: 'week', label: 'Next 7 days' },
  { value: 'month', label: 'Next 30 days' },
  { value: 'custom', label: 'Pick dates…' },
] as const

/** Turns a date preset into an inclusive local from/to range. */
function rangeFor(when: string, from: string, to: string): { from?: string; to?: string } {
  const today = new Date()
  switch (when) {
    case 'today':
      return { from: toIsoDate(today), to: toIsoDate(today) }
    case 'weekend': {
      const dow = today.getDay() // 0 Sun … 6 Sat
      if (dow === 0) return { from: toIsoDate(today), to: toIsoDate(today) }
      const saturday = addDays(today, dow === 6 ? 0 : 6 - dow)
      return { from: toIsoDate(saturday), to: toIsoDate(addDays(saturday, 1)) }
    }
    case 'week':
      return { from: toIsoDate(today), to: toIsoDate(addDays(today, 6)) }
    case 'month':
      return { from: toIsoDate(today), to: toIsoDate(addDays(today, 29)) }
    case 'custom':
      return { from: from || undefined, to: to || undefined }
    default:
      return {}
  }
}

interface Result {
  key: string
  data?: Page<EventSummary>
  error?: string
}

export function EventsPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const city = params.get('city') ?? ''
  const category = params.get('category') ?? ''
  const when = params.get('when') ?? ''
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)

  const [categories, setCategories] = useState<Category[]>([])
  const [cities, setCities] = useState<string[]>([])
  const [result, setResult] = useState<Result>({ key: '' })

  useEffect(() => {
    const ctrl = new AbortController()
    listCategories(ctrl.signal).then(setCategories).catch(() => {})
    listCities(ctrl.signal).then(setCities).catch(() => {})
    return () => ctrl.abort()
  }, [])

  const range = useMemo(() => rangeFor(when, from, to), [when, from, to])
  const key = JSON.stringify({ q, city, category, ...range, page })

  useEffect(() => {
    const ctrl = new AbortController()
    searchEvents({ q, city, category, ...range, page, size: PAGE_SIZE }, ctrl.signal)
      .then((data) => setResult({ key, data }))
      .catch((err) => {
        if (!ctrl.signal.aborted) setResult({ key, error: errorMessage(err, 'Could not load events') })
      })
    return () => ctrl.abort()
    // `key` captures every input of the request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  const loading = result.key !== key
  const data = result.key === key ? result.data : undefined
  const error = result.key === key ? result.error : undefined

  /** Update some filters; any filter change returns to the first page. */
  const update = (changes: Record<string, string>, keepPage = false) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    if (!keepPage) next.delete('page')
    setParams(next)
  }

  const onSearch = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const value = new FormData(e.currentTarget).get('q')
    update({ q: typeof value === 'string' ? value.trim() : '' })
  }

  const hasFilters = !!(q || city || category || when)
  const activeCategory = categories.find((c) => c.slug === category)

  return (
    <div className="container page">
      <div className="page-header" style={{ marginBottom: 20 }}>
        <div>
          <span className="eyebrow">Explore</span>
          <h1 style={{ marginTop: 6 }}>{activeCategory ? `${activeCategory.name} events` : 'Upcoming events'}</h1>
          <p>Find concerts, meetups, parties and workshops near you.</p>
        </div>
      </div>

      <div className="filter-bar card">
        <form className="filter-search" onSubmit={onSearch} key={q} role="search">
          <Search size={18} className="filter-search-icon" />
          <input
            name="q"
            className="input"
            defaultValue={q}
            placeholder="Search events by name"
            maxLength={100}
            aria-label="Search events by name"
          />
          <button type="submit" className="btn btn-primary">
            Search
          </button>
        </form>
        <div className="filter-row">
          <label className="select-wrap">
            <MapPin size={16} />
            <select value={city} onChange={(e) => update({ city: e.target.value })} aria-label="City">
              <option value="">All cities</option>
              {city && !cities.includes(city) && <option value={city}>{city}</option>}
              {cities.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
          <label className="select-wrap">
            <CalendarDays size={16} />
            <select
              value={when}
              onChange={(e) => update({ when: e.target.value, from: '', to: '' })}
              aria-label="Date"
            >
              {WHEN_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          {when === 'custom' && (
            <div className="date-range">
              <input
                type="date"
                className="input"
                value={from}
                max={to || undefined}
                onChange={(e) => update({ from: e.target.value })}
                aria-label="From date"
              />
              <span className="muted">to</span>
              <input
                type="date"
                className="input"
                value={to}
                min={from || undefined}
                onChange={(e) => update({ to: e.target.value })}
                aria-label="To date"
              />
            </div>
          )}
          {hasFilters && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setParams(new URLSearchParams())}>
              <X size={15} /> Clear filters
            </button>
          )}
        </div>
      </div>

      <div className="chips" role="list" aria-label="Categories">
        <button
          type="button"
          role="listitem"
          className={`chip${category === '' ? ' active' : ''}`}
          onClick={() => update({ category: '' })}
        >
          All
        </button>
        {categories.map((c) => {
          const Icon = categoryStyle(c.slug).icon
          return (
            <button
              key={c.slug}
              type="button"
              role="listitem"
              className={`chip${category === c.slug ? ' active' : ''}`}
              onClick={() => update({ category: c.slug })}
            >
              <Icon size={15} /> {c.name}
            </button>
          )
        })}
      </div>

      {error && <Alert>{error}</Alert>}

      {!error && (
        <p className="results-count muted small" aria-live="polite">
          {loading
            ? 'Searching…'
            : data && `${data.totalElements} ${data.totalElements === 1 ? 'event' : 'events'} found`}
        </p>
      )}

      {loading && !data && (
        <div className="event-grid">
          {Array.from({ length: 6 }, (_, i) => (
            <EventCardSkeleton key={i} />
          ))}
        </div>
      )}

      {data && data.content.length === 0 && (
        <div className="card">
          <EmptyState
            icon={<SearchX size={28} />}
            title="No events match your search"
            actions={
              hasFilters ? (
                <button type="button" className="btn btn-secondary" onClick={() => setParams(new URLSearchParams())}>
                  Clear all filters
                </button>
              ) : undefined
            }
          >
            {hasFilters
              ? 'Try a different date, city or category, or search for something else.'
              : 'There are no upcoming events yet. Check back soon!'}
          </EmptyState>
        </div>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="event-grid">
            {data.content.map((e) => (
              <EventCard key={e.id} event={e} />
            ))}
          </div>
          {data.totalPages > 1 && (
            <div className="pager pager-plain">
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page === 0}
                onClick={() => update({ page: String(page - 1) }, true)}
              >
                <ChevronLeft size={16} /> Previous
              </button>
              <span className="muted small">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page + 1 >= data.totalPages}
                onClick={() => update({ page: String(page + 1) }, true)}
              >
                Next <ChevronRight size={16} />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
