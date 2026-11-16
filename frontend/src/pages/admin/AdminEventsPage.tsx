import { ChevronLeft, ChevronRight, ClipboardCheck, Search } from 'lucide-react'
import type { FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { listAdminEvents } from '../../api/adminApi'
import type { EventStatus } from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock } from '../../components/ui/Spinner'
import { categoryStyle } from '../../features/events/categoryStyle'
import { EventStatusBadge } from '../../features/organizer/EventStatusBadge'
import { VerificationBadge } from '../../features/organizer/VerificationBadge'
import { formatEventStart } from '../../lib/format'
import { useQuery } from '../../lib/useQuery'

const TABS: { status: EventStatus | 'ALL'; label: string }[] = [
  { status: 'PENDING_APPROVAL', label: 'Pending approval' },
  { status: 'PUBLISHED', label: 'Published' },
  { status: 'REJECTED', label: 'Rejected' },
  { status: 'CANCELLED', label: 'Cancelled' },
  { status: 'DRAFT', label: 'Drafts' },
  { status: 'ALL', label: 'All' },
]

export function AdminEventsPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = (params.get('status') ?? 'PENDING_APPROVAL') as EventStatus | 'ALL'
  const q = params.get('q') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const status = tab === 'ALL' ? '' : tab

  const { data, error, loading } = useQuery(`${tab}|${q}|${page}`, (signal) =>
    listAdminEvents({ status, q, page, size: 15 }, signal),
  )

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  const onSearch = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    update({ q: String(new FormData(e.currentTarget).get('q') ?? '').trim() })
  }

  return (
    <div className="card">
      <div className="tabs" role="tablist" aria-label="Event status">
        {TABS.map((t) => (
          <button key={t.status} type="button" role="tab" aria-selected={tab === t.status}
            className={`tab${tab === t.status ? ' active' : ''}`}
            onClick={() => update({ status: t.status === 'PENDING_APPROVAL' ? '' : t.status })}>
            {t.label}
          </button>
        ))}
      </div>
      <form className="table-toolbar" onSubmit={onSearch} key={q} role="search">
        <div className="input-wrap" style={{ flex: 1 }}>
          <Search size={16} className="input-icon" />
          <input name="q" className="input has-icon" defaultValue={q} placeholder="Search by event title" maxLength={100} />
        </div>
        <button type="submit" className="btn btn-secondary">Search</button>
      </form>

      {error && <div className="card-body"><Alert>{error}</Alert></div>}
      {!data && loading && <LoadingBlock />}

      {data && data.content.length === 0 && (
        <EmptyState icon={<ClipboardCheck size={28} />} title={tab === 'PENDING_APPROVAL' && !q ? 'All caught up' : 'No events found'}>
          {tab === 'PENDING_APPROVAL' && !q ? 'No events are waiting for approval.' : 'Try another status or search term.'}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.55 : 1 }}>
            <table className="table table-clickable">
              <thead>
                <tr>
                  <th>Event</th>
                  <th>Organizer</th>
                  <th>Starts</th>
                  <th>{tab === 'PENDING_APPROVAL' ? 'Submitted' : 'Status'}</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((e) => {
                  const style = categoryStyle(e.category.slug)
                  const Icon = style.icon
                  return (
                    <tr key={e.id} onClick={() => navigate(`/admin/events/${e.id}`)}>
                      <td>
                        <span className="event-cell">
                          <span className="event-cell-icon" style={{ background: style.gradient }}><Icon size={16} /></span>
                          <span>
                            <strong>{e.title}</strong>
                            <span>{e.category.name} · {e.city}</span>
                          </span>
                        </span>
                      </td>
                      <td>
                        <div className="stack" style={{ gap: 4 }}>
                          <span>{e.organizerName}</span>
                          {e.organizerVerification !== 'PENDING' && <VerificationBadge status={e.organizerVerification} />}
                        </div>
                      </td>
                      <td className="muted">{formatEventStart(e.startsAt)}</td>
                      <td>
                        {tab === 'PENDING_APPROVAL' ? (
                          <span className="muted">
                            {e.submittedAt ? new Date(e.submittedAt).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '—'}
                          </span>
                        ) : (
                          <EventStatusBadge status={e.status} />
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {data.totalPages > 1 && (
            <div className="pager">
              <span className="muted small">Page {data.page + 1} of {data.totalPages} · {data.totalElements} events</span>
              <div className="row" style={{ gap: 8 }}>
                <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
                  <ChevronLeft size={16} /> Previous
                </button>
                <button type="button" className="btn btn-secondary btn-sm" disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>
                  Next <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
