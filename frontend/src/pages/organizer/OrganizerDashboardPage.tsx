import {
  CalendarClock,
  CalendarDays,
  CalendarPlus,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  FilePen,
  Megaphone,
  Settings,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../../api/http'
import {
  getOrganizerDashboard,
  listMyEvents,
  type EventStatus,
  type OrganizerDashboard,
  type OrganizerEventSummary,
} from '../../api/organizerApi'
import type { Page } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock } from '../../components/ui/Spinner'
import { StatCard } from '../../components/ui/StatCard'
import { categoryStyle } from '../../features/events/categoryStyle'
import { EventStatusBadge } from '../../features/organizer/EventStatusBadge'
import { VerificationBadge } from '../../features/organizer/VerificationBadge'
import { formatEventStart } from '../../lib/format'

const TABS: { status: EventStatus | ''; label: string; count?: keyof OrganizerDashboard['stats'] }[] = [
  { status: '', label: 'All', count: 'total' },
  { status: 'DRAFT', label: 'Drafts', count: 'draft' },
  { status: 'PENDING_APPROVAL', label: 'Pending', count: 'pendingApproval' },
  { status: 'PUBLISHED', label: 'Published', count: 'published' },
  { status: 'REJECTED', label: 'Rejected', count: 'rejected' },
  { status: 'CANCELLED', label: 'Cancelled', count: 'cancelled' },
]

interface ListState {
  key: string
  data?: Page<OrganizerEventSummary>
  error?: string
}

export function OrganizerDashboardPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? '') as EventStatus | ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)

  const [dash, setDash] = useState<OrganizerDashboard | null>(null)
  const [dashError, setDashError] = useState<string | null>(null)
  const [list, setList] = useState<ListState>({ key: '' })
  const key = `${status}|${page}`

  useEffect(() => {
    getOrganizerDashboard().then(setDash).catch((err) => setDashError(errorMessage(err)))
  }, [])

  const hasProfile = !!dash?.profile
  useEffect(() => {
    if (!hasProfile) return
    const ctrl = new AbortController()
    listMyEvents(status, page, 10, ctrl.signal)
      .then((data) => setList({ key, data }))
      .catch((err) => {
        if (!ctrl.signal.aborted) setList({ key, error: errorMessage(err) })
      })
    return () => ctrl.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, hasProfile])

  if (dashError)
    return (
      <div className="container page">
        <Alert>{dashError}</Alert>
      </div>
    )
  if (!dash) return <LoadingBlock />

  if (!dash.profile) {
    return (
      <div className="container page">
        <div className="card">
          <EmptyState
            icon={<Megaphone size={28} />}
            title="Set up your organizer profile"
            actions={
              <Link to="/organizer/onboarding" className="btn btn-primary">
                Create organizer profile
              </Link>
            }
          >
            You need an organizer profile before you can create events. It's how attendees will see you.
          </EmptyState>
        </div>
      </div>
    )
  }

  const { profile, stats } = dash
  const loading = list.key !== key
  const data = list.key === key ? list.data : undefined
  const setTab = (s: EventStatus | '') => setParams(s ? { status: s } : {})
  const setPage = (p: number) => setParams({ ...(status ? { status } : {}), ...(p ? { page: String(p) } : {}) })

  return (
    <div className="container page">
      <div className="page-header">
        <div>
          <span className="eyebrow">Organizer</span>
          <h1 style={{ marginTop: 6 }}>{profile.displayName}</h1>
          <div className="row" style={{ marginTop: 10, gap: 10 }}>
            <VerificationBadge status={profile.verificationStatus} />
            <Link to="/organizer/profile" className="text-link small">
              <Settings size={14} /> Edit profile
            </Link>
          </div>
        </div>
        <Link to="/organizer/events/new" className="btn btn-primary">
          <CalendarPlus size={18} /> Create event
        </Link>
      </div>

      <div className="stats">
        <StatCard icon={<CalendarDays size={20} />} label="Total events" value={stats.total} />
        <StatCard icon={<CalendarClock size={20} />} label="Live & upcoming" value={stats.upcoming} tone="green" />
        <StatCard icon={<ClipboardCheck size={20} />} label="Awaiting approval" value={stats.pendingApproval} tone="amber" />
        <StatCard icon={<FilePen size={20} />} label="Drafts" value={stats.draft} tone="pink" />
      </div>

      <div className="card">
        <div className="tabs" role="tablist" aria-label="Filter by status">
          {TABS.map((t) => (
            <button
              key={t.label}
              type="button"
              role="tab"
              aria-selected={status === t.status}
              className={`tab${status === t.status ? ' active' : ''}`}
              onClick={() => setTab(t.status)}
            >
              {t.label}
              {t.count && <span className="tab-count">{stats[t.count]}</span>}
            </button>
          ))}
        </div>

        {list.error && list.key === key && (
          <div className="card-body">
            <Alert>{list.error}</Alert>
          </div>
        )}
        {loading && !data && <LoadingBlock />}

        {data && data.content.length === 0 && (
          <EmptyState
            icon={<CalendarPlus size={28} />}
            title={status ? 'Nothing here' : 'Create your first event'}
            actions={
              !status && (
                <Link to="/organizer/events/new" className="btn btn-primary">
                  <CalendarPlus size={18} /> Create event
                </Link>
              )
            }
          >
            {status
              ? 'No events with this status.'
              : 'Start with a draft: add details and ticket types, then submit it for approval.'}
          </EmptyState>
        )}

        {data && data.content.length > 0 && (
          <>
            <div className="table-wrap" style={{ opacity: loading ? 0.55 : 1 }}>
              <table className="table table-clickable">
                <thead>
                  <tr>
                    <th>Event</th>
                    <th>Date</th>
                    <th>Status</th>
                    <th>Tickets</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((e) => {
                    const style = categoryStyle(e.category.slug)
                    const Icon = style.icon
                    return (
                      <tr key={e.id} onClick={() => navigate(`/organizer/events/${e.id}`)}>
                        <td>
                          <Link to={`/organizer/events/${e.id}`} className="event-cell" onClick={(ev) => ev.stopPropagation()}>
                            <span className="event-cell-icon" style={{ background: style.gradient }}>
                              <Icon size={16} />
                            </span>
                            <span>
                              <strong>{e.title}</strong>
                              <span>
                                {e.category.name} · {e.city}
                              </span>
                            </span>
                          </Link>
                        </td>
                        <td className="muted">{formatEventStart(e.startsAt)}</td>
                        <td>
                          <EventStatusBadge status={e.status} />
                        </td>
                        <td className="muted">
                          {e.ticketTypes === 0
                            ? 'None yet'
                            : `${e.ticketTypes} type${e.ticketTypes === 1 ? '' : 's'} · ${e.capacity.toLocaleString('en-IN')} seats`}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            {data.totalPages > 1 && (
              <div className="pager">
                <span className="muted small">
                  Page {data.page + 1} of {data.totalPages}
                </span>
                <div className="row" style={{ gap: 8 }}>
                  <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                    <ChevronLeft size={16} /> Previous
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    disabled={page + 1 >= data.totalPages}
                    onClick={() => setPage(page + 1)}
                  >
                    Next <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
