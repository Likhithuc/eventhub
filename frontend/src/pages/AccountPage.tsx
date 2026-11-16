import { CalendarCheck, ChevronRight, LayoutDashboard, Megaphone, ShieldCheck, Ticket } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMe } from '../api/authApi'
import { errorMessage } from '../api/http'
import type { User } from '../api/types'
import { Alert } from '../components/ui/Alert'
import { Avatar } from '../components/ui/Avatar'
import { RoleBadges, StatusBadge } from '../components/ui/Badges'
import { LoadingBlock } from '../components/ui/Spinner'

export function AccountPage() {
  const [me, setMe] = useState<User | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // Loaded from the API (not only the token) so the page always reflects the server.
    getMe().then(setMe).catch((err) => setError(errorMessage(err)))
  }, [])

  if (error) {
    return (
      <div className="container page">
        <Alert>{error}</Alert>
      </div>
    )
  }
  if (!me) return <LoadingBlock label="Loading your account…" />

  const isOrganizer = me.roles.includes('ORGANIZER') || me.roles.includes('ADMIN')
  const joined = new Date(me.createdAt).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })

  return (
    <div className="container page">
      <div className="card">
        <div className="profile-cover" />
        <div className="profile-head">
          <Avatar name={me.name} seed={me.id} size="lg" />
          <div className="profile-head-text">
            <h1>{me.name}</h1>
            <p>{me.email}</p>
          </div>
        </div>
        <div style={{ padding: '16px 24px 22px' }}>
          <RoleBadges roles={me.roles} />
        </div>
      </div>

      <div className="profile-grid">
        <div className="card">
          <div className="card-header">
            <h2>Profile details</h2>
          </div>
          <div className="card-body" style={{ paddingBlock: 6 }}>
            <dl className="details">
              <dt>Full name</dt>
              <dd>{me.name}</dd>
              <dt>Email</dt>
              <dd>{me.email}</dd>
              <dt>Account status</dt>
              <dd>
                <StatusBadge status={me.status} />
              </dd>
              <dt>Member since</dt>
              <dd>{joined}</dd>
              <dt>Account ID</dt>
              <dd className="mono">{me.id}</dd>
            </dl>
          </div>
        </div>

        <div className="stack">
          <div className="card">
            <div className="card-header">
              <h2>Quick links</h2>
            </div>
            <div className="quick-links">
              <Link to="/bookings" className="quick-link">
                <Ticket size={18} />
                <div>
                  My bookings
                  <small>Your tickets and booking history</small>
                </div>
                <ChevronRight size={16} className="chev" />
              </Link>
              <Link to="/events" className="quick-link">
                <CalendarCheck size={18} />
                <div>
                  Explore events
                  <small>Find something to do</small>
                </div>
                <ChevronRight size={16} className="chev" />
              </Link>
              {isOrganizer && (
                <Link to="/organizer" className="quick-link">
                  <LayoutDashboard size={18} />
                  <div>
                    Organizer dashboard
                    <small>Manage your events</small>
                  </div>
                  <ChevronRight size={16} className="chev" />
                </Link>
              )}
              {me.roles.includes('ADMIN') && (
                <Link to="/admin" className="quick-link">
                  <ShieldCheck size={18} />
                  <div>
                    Admin console
                    <small>Users and moderation</small>
                  </div>
                  <ChevronRight size={16} className="chev" />
                </Link>
              )}
            </div>
          </div>

          {!isOrganizer && (
            <div className="card card-body">
              <div className="row" style={{ alignItems: 'flex-start', flexWrap: 'nowrap' }}>
                <div className="stat-icon pink">
                  <Megaphone size={20} />
                </div>
                <div>
                  <h3>Want to host events?</h3>
                  <p className="muted small" style={{ marginTop: 6 }}>
                    Create an organizer profile to publish events and sell tickets from this account.
                  </p>
                  <Link to="/organizer/onboarding" className="btn btn-primary btn-sm" style={{ marginTop: 12 }}>
                    Become an organizer
                  </Link>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
