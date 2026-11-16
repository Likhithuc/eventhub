import {
  ArrowLeft,
  CalendarDays,
  CircleCheck,
  Clock,
  ExternalLink,
  EyeOff,
  Mail,
  MapPin,
  TicketX,
  UserRound,
  XCircle,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { approveEvent, getAdminEvent, rejectEvent, unpublishEvent, type AdminEventDetail } from '../../api/adminApi'
import { ApiError, errorMessage } from '../../api/http'
import { Alert } from '../../components/ui/Alert'
import { Avatar } from '../../components/ui/Avatar'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock, Spinner } from '../../components/ui/Spinner'
import { AuditList } from '../../features/admin/AuditList'
import { categoryStyle } from '../../features/events/categoryStyle'
import { EventStatusBadge } from '../../features/organizer/EventStatusBadge'
import { VerificationBadge } from '../../features/organizer/VerificationBadge'
import { formatEventWhen, formatMoney } from '../../lib/format'
import { useQuery } from '../../lib/useQuery'

type Decision = 'approve' | 'reject' | 'unpublish'

const REASON_PRESETS = [
  'Event details are incomplete or unclear.',
  'Venue or address could not be verified.',
  'Ticket prices or capacity look incorrect.',
  'Content does not meet our community guidelines.',
]

/** Keyed by event id so messages and dialogs never carry over from one event to the next. */
export function AdminEventReviewPage() {
  const { eventId = '' } = useParams()
  return <EventReview key={eventId} eventId={eventId} />
}

function EventReview({ eventId }: { eventId: string }) {
  const query = useQuery(`admin-event-${eventId}`, (signal) => getAdminEvent(eventId, signal))
  const [override, setOverride] = useState<AdminEventDetail | null>(null)
  const [dialog, setDialog] = useState<Decision | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  if (query.error) {
    const notFound = query.error === 'Event not found'
    return notFound ? (
      <div className="card">
        <EmptyState icon={<TicketX size={28} />} title="Event not found"
          actions={<Link to="/admin/events" className="btn btn-primary">Back to approvals</Link>} />
      </div>
    ) : (
      <Alert>{query.error}</Alert>
    )
  }
  const detail = override?.event.id === eventId ? override : query.data
  if (!detail) return <LoadingBlock label="Loading event…" />

  const { event: e, organizer: org } = detail
  const style = categoryStyle(e.category.slug)
  const Icon = style.icon
  const when = formatEventWhen(e.startsAt, e.endsAt)
  const capacity = e.ticketTypes.reduce((n, t) => n + t.totalQuantity, 0)
  const reasonOk = reason.trim().length >= 5

  const open = (d: Decision) => {
    setReason('')
    setActionError(null)
    setDialog(d)
  }

  const decide = async () => {
    if (!dialog) return
    setBusy(true)
    setActionError(null)
    try {
      const result =
        dialog === 'approve' ? await approveEvent(e.id)
          : dialog === 'reject' ? await rejectEvent(e.id, reason.trim())
            : await unpublishEvent(e.id, reason.trim())
      setOverride(result)
      setNotice(
        dialog === 'approve' ? 'Approved. The event is now live on EventHub.'
          : dialog === 'reject' ? 'Rejected. The organizer can see your reason and resubmit.'
            : 'Unpublished. The event is no longer public.',
      )
      setDialog(null)
    } catch (err) {
      setActionError(err instanceof ApiError ? errorMessage(err) : 'Something went wrong')
      setDialog(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Link to="/admin/events" className="text-link">
        <ArrowLeft size={16} /> Event approvals
      </Link>

      {notice && <div style={{ marginTop: 16 }}><Alert tone="info">{notice}</Alert></div>}
      {actionError && <div style={{ marginTop: 16 }}><Alert>{actionError}</Alert></div>}

      <div className="editor-layout" style={{ marginTop: 16 }}>
        <div className="stack" style={{ gap: 20 }}>
          <div className="card review-preview">
            <div className="review-banner" style={{ background: style.gradient }}>
              <Icon className="review-banner-icon" size={150} strokeWidth={1.2} aria-hidden="true" />
              <span className="badge event-hero-cat" style={{ marginTop: 0 }}><Icon size={13} /> {e.category.name}</span>
              <h1>{e.title}</h1>
              <div className="event-hero-meta" style={{ marginTop: 10 }}>
                <span><CalendarDays size={16} /> {when.date}</span>
                <span><Clock size={16} /> {when.time}</span>
              </div>
            </div>
            <div className="card-body stack" style={{ gap: 22 }}>
              <div>
                <h3 className="review-label">Description</h3>
                <p className="event-description">{e.description}</p>
              </div>
              <div>
                <h3 className="review-label">Venue</h3>
                <p className="row" style={{ gap: 8, alignItems: 'flex-start', flexWrap: 'nowrap' }}>
                  <MapPin size={16} style={{ marginTop: 3, flex: 'none' }} />
                  <span><strong>{e.venueName}</strong><br /><span className="muted">{e.address}, {e.city}</span></span>
                </p>
              </div>
              <div>
                <h3 className="review-label">Tickets · {capacity.toLocaleString('en-IN')} total capacity</h3>
                {e.ticketTypes.length === 0 ? (
                  <p className="muted">No ticket types.</p>
                ) : (
                  <div className="table-wrap review-tickets">
                    <table className="table">
                      <thead><tr><th>Type</th><th>Price</th><th>Quantity</th><th>Sales window</th></tr></thead>
                      <tbody>
                        {e.ticketTypes.map((t) => (
                          <tr key={t.id}>
                            <td><strong>{t.name}</strong></td>
                            <td>{t.price === 0 ? 'Free' : formatMoney(t.price, t.currency)}</td>
                            <td>{t.totalQuantity.toLocaleString('en-IN')}</td>
                            <td className="muted">
                              {t.salesStartAt || t.salesEndAt
                                ? `${t.salesStartAt ? new Date(t.salesStartAt).toLocaleDateString('en-IN') : 'Now'} → ${t.salesEndAt ? new Date(t.salesEndAt).toLocaleDateString('en-IN') : 'event start'}`
                                : 'Until event starts'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="card card-body">
            <h2 style={{ marginBottom: 16 }}>Organizer</h2>
            <div className="organizer-row" style={{ alignItems: 'flex-start' }}>
              <Avatar name={org.displayName} seed={org.id} size="lg" />
              <div className="stack" style={{ gap: 8, minWidth: 0 }}>
                <div className="row" style={{ gap: 10 }}>
                  <strong className="organizer-name" style={{ margin: 0 }}>{org.displayName}</strong>
                  <VerificationBadge status={org.verificationStatus} />
                </div>
                {org.description && <p className="muted small">{org.description}</p>}
                <div className="review-contact">
                  <span><UserRound size={14} /> {org.userName} ({org.userEmail})</span>
                  {org.contactEmail && <span><Mail size={14} /> {org.contactEmail}</span>}
                </div>
                <Link to="/admin/organizers" className="text-link small">Manage organizer verification</Link>
              </div>
            </div>
          </div>
        </div>

        <aside className="stack status-panel" style={{ gap: 20 }}>
          <div className="card card-body stack" style={{ gap: 14 }}>
            <div>
              <span className="muted small">Status</span>
              <div style={{ marginTop: 6 }}><EventStatusBadge status={e.status} /></div>
            </div>
            <dl className="review-meta">
              {e.submittedAt && (<><dt>Submitted</dt><dd>{new Date(e.submittedAt).toLocaleString('en-IN')}</dd></>)}
              {detail.reviewedAt && (<><dt>Reviewed</dt><dd>{new Date(detail.reviewedAt).toLocaleString('en-IN')}{detail.reviewedByName && ` by ${detail.reviewedByName}`}</dd></>)}
            </dl>
            {detail.rejectionReason && (
              <div className="reason-box">
                <strong>Reason given</strong>
                <p>{detail.rejectionReason}</p>
              </div>
            )}

            {e.status === 'PENDING_APPROVAL' && (
              <div className="stack" style={{ gap: 8 }}>
                <button type="button" className="btn btn-success btn-lg btn-block" onClick={() => open('approve')}>
                  <CircleCheck size={18} /> Approve & publish
                </button>
                <button type="button" className="btn btn-secondary btn-block danger-hover" onClick={() => open('reject')}>
                  <XCircle size={17} /> Reject
                </button>
              </div>
            )}
            {e.status === 'PUBLISHED' && (
              <div className="stack" style={{ gap: 8 }}>
                <Link to={`/events/${e.id}`} className="btn btn-secondary btn-block"><ExternalLink size={16} /> View public page</Link>
                <button type="button" className="btn btn-ghost btn-block danger-hover" onClick={() => open('unpublish')}>
                  <EyeOff size={16} /> Unpublish
                </button>
              </div>
            )}
            {e.status !== 'PENDING_APPROVAL' && e.status !== 'PUBLISHED' && (
              <p className="muted small">No moderation action is available for this status.</p>
            )}
          </div>

          <div className="card">
            <div className="card-header"><h2>History</h2></div>
            <div className="card-body" style={{ paddingTop: 8 }}>
              <AuditList entries={detail.history} compact />
            </div>
          </div>
        </aside>
      </div>

      <ConfirmDialog open={dialog === 'approve'} title="Approve and publish?" confirmLabel="Approve & publish"
        cancelLabel="Cancel" busy={busy} onCancel={() => setDialog(null)} onConfirm={decide}>
        <strong>{e.title}</strong> will be visible to everyone on EventHub immediately.
      </ConfirmDialog>

      <ConfirmDialog open={dialog === 'reject' || dialog === 'unpublish'}
        title={dialog === 'unpublish' ? 'Unpublish this event?' : 'Reject this event?'}
        confirmLabel={dialog === 'unpublish' ? 'Unpublish' : 'Reject event'} tone="danger" cancelLabel="Cancel"
        busy={busy} confirmDisabled={!reasonOk} onCancel={() => setDialog(null)} onConfirm={decide}>
        <p style={{ marginBottom: 12 }}>
          {dialog === 'unpublish'
            ? 'The event will be removed from public listings and returned to the organizer to fix.'
            : 'The organizer will see your reason and can update the event and resubmit.'}
        </p>
        <div className="reason-presets">
          {REASON_PRESETS.map((r) => (
            <button key={r} type="button" className="chip" onClick={() => setReason(r)}>{r}</button>
          ))}
        </div>
        <label className="field-label" htmlFor="reject-reason" style={{ display: 'block', margin: '12px 0 6px' }}>
          Reason for the organizer
        </label>
        <textarea id="reject-reason" className="input textarea" rows={3} maxLength={1000} value={reason}
          onChange={(ev) => setReason(ev.target.value)} placeholder="Explain what needs to change…" />
        {!reasonOk && reason.length > 0 && <span className="field-error">At least 5 characters</span>}
        {busy && <span className="row muted small" style={{ marginTop: 8, gap: 6 }}><Spinner small /> Saving…</span>}
      </ConfirmDialog>
    </>
  )
}
