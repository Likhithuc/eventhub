import {
  ArrowRight,
  BadgeCheck,
  CalendarDays,
  CheckCircle2,
  MapPin,
  QrCode,
  Search,
  Ticket,
  Users,
} from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listCategories, listCities, searchEvents, type Category, type EventSummary } from '../api/eventApi'
import { EventCard, EventCardSkeleton } from '../features/events/EventCard'
import { categoryStyle } from '../features/events/categoryStyle'
import { useAuth } from '../features/auth/useAuth'

function HeroVisual() {
  return (
    <div className="hero-visual" aria-hidden="true">
      <div className="ticket ticket-1">
        <div className="ticket-art">
          <span className="art-shape" style={{ width: 140, height: 140, right: -30, top: -40 }} />
          <span className="badge">Music</span>
        </div>
        <div className="ticket-body">
          <h3>Indie Nights Live</h3>
          <div className="ticket-meta">
            <span>
              <CalendarDays size={14} /> Sat · 7:30 PM
            </span>
            <span>
              <MapPin size={14} /> Bengaluru
            </span>
          </div>
          <div className="ticket-foot">
            <span className="ticket-price">₹499</span>
            <span className="badge badge-organizer">Selling fast</span>
          </div>
        </div>
      </div>
      <div className="ticket ticket-2">
        <div className="ticket-art">
          <span className="art-shape" style={{ width: 110, height: 110, left: -20, bottom: -50 }} />
          <span className="badge">Tech</span>
        </div>
        <div className="ticket-body">
          <h3>React & Spring Meetup</h3>
          <div className="ticket-meta">
            <span>
              <CalendarDays size={14} /> Thu · 6:30 PM
            </span>
            <span>
              <MapPin size={14} /> Hyderabad
            </span>
          </div>
          <div className="ticket-foot">
            <span className="ticket-price">Free</span>
            <span className="badge badge-attendee">Open</span>
          </div>
        </div>
      </div>
      <div className="ticket ticket-3">
        <div className="qr" />
        <div>
          <strong>Ticket confirmed</strong>
          <span>
            <CheckCircle2 size={13} /> Ready for check-in
          </span>
        </div>
      </div>
    </div>
  )
}

function HeroSearch({ cities }: { cities: string[] }) {
  const navigate = useNavigate()
  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const qs = new URLSearchParams()
    const q = String(form.get('q') ?? '').trim()
    const city = String(form.get('city') ?? '')
    if (q) qs.set('q', q)
    if (city) qs.set('city', city)
    navigate(`/events${qs.size ? `?${qs}` : ''}`)
  }
  return (
    <form className="hero-search" onSubmit={onSubmit} role="search">
      <label className="hero-search-field">
        <Search size={18} />
        <input name="q" placeholder="Search events, e.g. comedy" maxLength={100} aria-label="Search events" />
      </label>
      <span className="hero-search-sep" aria-hidden="true" />
      <label className="hero-search-field hero-search-city">
        <MapPin size={18} />
        <select name="city" defaultValue="" aria-label="City">
          <option value="">Any city</option>
          {cities.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </label>
      <button type="submit" className="btn btn-gradient btn-lg">
        Find events
      </button>
    </form>
  )
}

export function HomePage() {
  const { status, user, hasRole } = useAuth()
  const signedIn = status === 'authenticated' && user

  const [upcoming, setUpcoming] = useState<EventSummary[] | null>(null)
  const [total, setTotal] = useState(0)
  const [categories, setCategories] = useState<Category[]>([])
  const [cities, setCities] = useState<string[]>([])

  useEffect(() => {
    const ctrl = new AbortController()
    searchEvents({ size: 6 }, ctrl.signal)
      .then((page) => {
        setUpcoming(page.content)
        setTotal(page.totalElements)
      })
      .catch(() => {
        if (!ctrl.signal.aborted) setUpcoming([])
      })
    listCategories(ctrl.signal).then(setCategories).catch(() => {})
    listCities(ctrl.signal).then(setCities).catch(() => {})
    return () => ctrl.abort()
  }, [])

  return (
    <>
      <section className="hero">
        <div className="container hero-grid">
          <div>
            <span className="hero-chip">
              <b>New</b> Free events, booked in seconds
            </span>
            <h1>
              Find events that
              <br />
              <span className="gradient-text">move you.</span>
            </h1>
            <p className="hero-lead">
              {signedIn
                ? `Welcome back, ${user.name.split(' ')[0]}. Concerts, meetups and parties near you, all in one place.`
                : 'Concerts, meetups, parties and workshops from organizers near you. Discover, book and get in with a QR code.'}
            </p>
            <HeroSearch cities={cities} />
            <div className="hero-proof">
              <span>
                <BadgeCheck size={16} /> Verified organizers
              </span>
              <span>
                <BadgeCheck size={16} /> Instant confirmation
              </span>
              <span>
                <BadgeCheck size={16} /> No hidden fees
              </span>
            </div>
          </div>
          <HeroVisual />
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head-row">
            <div>
              <span className="eyebrow">Happening soon</span>
              <h2>Upcoming events</h2>
            </div>
            {total > 0 && (
              <Link to="/events" className="btn btn-secondary">
                View all {total} <ArrowRight size={16} />
              </Link>
            )}
          </div>
          {upcoming === null ? (
            <div className="event-grid">
              {Array.from({ length: 3 }, (_, i) => (
                <EventCardSkeleton key={i} />
              ))}
            </div>
          ) : upcoming.length === 0 ? (
            <div className="card card-body muted" style={{ textAlign: 'center' }}>
              No upcoming events yet. Check back soon!
            </div>
          ) : (
            <div className="event-grid">
              {upcoming.map((e) => (
                <EventCard key={e.id} event={e} />
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="section section-alt" id="categories">
        <div className="container">
          <div className="section-head">
            <span className="eyebrow">Browse</span>
            <h2>Something for every vibe</h2>
            <p>Pick a category to see what's on.</p>
          </div>
          <div className="categories">
            {categories.map((c) => {
              const style = categoryStyle(c.slug)
              const Icon = style.icon
              return (
                <Link key={c.slug} to={`/events?category=${c.slug}`} className="category">
                  <span className="category-icon" style={{ background: style.gradient }}>
                    <Icon size={22} />
                  </span>
                  <strong>{c.name}</strong>
                  <span className="category-hint">{style.hint}</span>
                </Link>
              )
            })}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <span className="eyebrow">How it works</span>
            <h2>From discovery to the door in three steps</h2>
          </div>
          <div className="steps">
            <div className="card step">
              <span className="step-num">01</span>
              <div className="stat-icon">
                <Search size={20} />
              </div>
              <h3>Discover</h3>
              <p>Search by city, category and date to find events worth leaving the house for.</p>
            </div>
            <div className="card step">
              <span className="step-num">02</span>
              <div className="stat-icon pink">
                <Ticket size={20} />
              </div>
              <h3>Book</h3>
              <p>Pick your tickets and get instant confirmation. Availability is always live and accurate.</p>
            </div>
            <div className="card step">
              <span className="step-num">03</span>
              <div className="stat-icon green">
                <QrCode size={20} />
              </div>
              <h3>Check in</h3>
              <p>Show your unique QR code at the entrance. No printing, no queues, no fuss.</p>
            </div>
          </div>
        </div>
      </section>

      <section className="section" style={{ paddingTop: 0 }}>
        <div className="container">
          <div className="cta-banner">
            <div>
              <h2>Hosting an event?</h2>
              <p>Publish your event, set ticket types and capacity, and check guests in from your phone.</p>
              <div className="cta-list">
                <span>
                  <CheckCircle2 size={16} /> Free to publish
                </span>
                <span>
                  <Users size={16} /> Attendee lists
                </span>
                <span>
                  <QrCode size={16} /> QR check-in
                </span>
              </div>
            </div>
            <div className="row">
              {hasRole('ORGANIZER', 'ADMIN') ? (
                <Link to="/organizer" className="btn btn-white btn-lg">
                  Go to dashboard <ArrowRight size={18} />
                </Link>
              ) : (
                <Link to={signedIn ? '/organizer/onboarding' : '/register'} className="btn btn-white btn-lg">
                  {signedIn ? 'Become an organizer' : 'Start organizing'} <ArrowRight size={18} />
                </Link>
              )}
            </div>
          </div>
        </div>
      </section>
    </>
  )
}
