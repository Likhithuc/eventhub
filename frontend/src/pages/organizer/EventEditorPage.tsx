import {
  ArrowLeft,
  Ban,
  CheckCircle2,
  Circle,
  ExternalLink,
  ScanLine,
  Send,
  Trash2,
  Undo2,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { listCategories, type Category } from '../../api/eventApi'
import { ApiError, errorMessage } from '../../api/http'
import {
  cancelEvent,
  createEvent,
  deleteEvent,
  getMyEvent,
  submitEvent,
  updateEvent,
  withdrawEvent,
  type OrganizerEvent,
} from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock, Spinner } from '../../components/ui/Spinner'
import { EventDetailsForm } from '../../features/organizer/EventDetailsForm'
import { EventSalesSection } from '../../features/organizer/EventSalesSection'
import { EventStatusBadge } from '../../features/organizer/EventStatusBadge'
import { TicketTypesSection } from '../../features/organizer/TicketTypesSection'

type Action = 'submit' | 'withdraw' | 'cancel' | 'delete'

const STATUS_HELP: Record<OrganizerEvent['status'], string> = {
  DRAFT: 'Only you can see this draft. Add details and tickets, then submit it for approval.',
  PENDING_APPROVAL: 'Our team is reviewing your event. Withdraw it if you need to make changes.',
  PUBLISHED: 'Your event is live and visible to everyone.',
  REJECTED: 'This event was not approved. Update it and submit it again.',
  CANCELLED: 'This event was cancelled and is no longer visible.',
  COMPLETED: 'This event has finished.',
}

/**
 * Keyed by event id so moving between routes (e.g. /new -> /:id after saving,
 * or from one event to another) starts with fresh state instead of reusing it.
 */
export function EventEditorPage() {
  const { eventId } = useParams()
  return <EventEditor key={eventId ?? 'new'} eventId={eventId} />
}

function EventEditor({ eventId }: { eventId?: string }) {
  const isNew = !eventId
  const navigate = useNavigate()
  const location = useLocation()
  const justCreated = (location.state as { created?: boolean } | null)?.created === true

  const [categories, setCategories] = useState<Category[]>([])
  const [loaded, setLoaded] = useState<{ id?: string; event?: OrganizerEvent; notFound?: boolean; error?: string }>({})
  const [notice, setNotice] = useState<string | null>(justCreated ? 'Draft saved. Now add your ticket types.' : null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<Action | null>(null)
  const [busy, setBusy] = useState<Action | null>(null)
  // Captured once per page view; used for 'starts in the future' / 'not ended' hints.
  const [now] = useState(() => Date.now())

  useEffect(() => {
    listCategories().then(setCategories).catch(() => {})
  }, [])

  useEffect(() => {
    if (!eventId) return
    const ctrl = new AbortController()
    getMyEvent(eventId, ctrl.signal)
      .then((event) => setLoaded({ id: eventId, event }))
      .catch((err) => {
        if (ctrl.signal.aborted) return
        const notFound = err instanceof ApiError && (err.status === 404 || err.status === 400)
        setLoaded({ id: eventId, notFound, error: notFound ? undefined : errorMessage(err) })
      })
    return () => ctrl.abort()
  }, [eventId])

  if (isNew) {
    return (
      <div className="container page narrow-page">
        <Link to="/organizer" className="text-link">
          <ArrowLeft size={16} /> Dashboard
        </Link>
        <div className="page-header" style={{ marginTop: 14 }}>
          <div>
            <h1>Create an event</h1>
            <p>Start with the basics. You'll add ticket types next. Nothing is public until it's approved.</p>
          </div>
        </div>
        <div className="card card-body">
          <EventDetailsForm
            categories={categories}
            submitLabel="Save draft"
            onSave={async (input) => {
              const created = await createEvent(input)
              navigate(`/organizer/events/${created.id}`, { replace: true, state: { created: true } })
            }}
          />
        </div>
      </div>
    )
  }

  if (loaded.id !== eventId) return <LoadingBlock label="Loading event…" />
  if (loaded.notFound) {
    return (
      <div className="container page">
        <div className="card">
          <EmptyState
            icon={<Ban size={28} />}
            title="Event not found"
            actions={
              <Link to="/organizer" className="btn btn-primary">
                Back to dashboard
              </Link>
            }
          >
            This event doesn't exist or doesn't belong to your organizer account.
          </EmptyState>
        </div>
      </div>
    )
  }
  if (loaded.error || !loaded.event) {
    return (
      <div className="container page">
        <Alert>{loaded.error ?? 'Could not load the event'}</Alert>
      </div>
    )
  }

  const event = loaded.event
  const setEvent = (e: OrganizerEvent) => setLoaded({ id: eventId, event: e })
  const hasTickets = event.ticketTypes.some((t) => t.status === 'ACTIVE')
  const startsInFuture = new Date(event.startsAt).getTime() > now
  const canSubmit = event.editable && hasTickets && startsInFuture
  const canCancel = !['CANCELLED', 'COMPLETED'].includes(event.status) && new Date(event.endsAt).getTime() > now

  const run = async (action: Action) => {
    setBusy(action)
    setActionError(null)
    setNotice(null)
    try {
      if (action === 'delete') {
        await deleteEvent(event.id)
        navigate('/organizer', { replace: true })
        return
      }
      const fn = { submit: submitEvent, withdraw: withdrawEvent, cancel: cancelEvent }[action]
      setEvent(await fn(event.id))
      setNotice(
        { submit: 'Submitted for approval.', withdraw: 'Withdrawn. Your event is a draft again.', cancel: 'Event cancelled.' }[
          action
        ],
      )
    } catch (err) {
      setActionError(errorMessage(err, 'Something went wrong'))
    } finally {
      setBusy(null)
      setConfirm(null)
    }
  }

  return (
    <div className="container page">
      <Link to="/organizer" className="text-link">
        <ArrowLeft size={16} /> Dashboard
      </Link>
      <div className="page-header" style={{ marginTop: 14 }}>
        <div style={{ minWidth: 0 }}>
          <div className="row" style={{ gap: 10, marginBottom: 8 }}>
            <EventStatusBadge status={event.status} />
            <span className="muted small">{event.category.name}</span>
          </div>
          <h1 className="break">{event.title}</h1>
        </div>
        {event.status === 'PUBLISHED' && (
          <div className="row" style={{ gap: 8 }}>
            <Link to={`/events/${event.id}`} className="btn btn-secondary">
              <ExternalLink size={16} /> View public page
            </Link>
            <Link to={`/organizer/events/${event.id}/check-in`} className="btn btn-primary">
              <ScanLine size={16} /> Check-in
            </Link>
          </div>
        )}
      </div>

      {event.status === 'REJECTED' && event.rejectionReason && (
        <div className="reason-box reason-box-lg" style={{ marginBottom: 18 }}>
          <strong>Changes requested by the EventHub team</strong>
          <p>{event.rejectionReason}</p>
          <span className="muted small">Update your event below, then submit it again.</span>
        </div>
      )}
      {notice && (
        <div style={{ marginBottom: 18 }}>
          <Alert tone="info">{notice}</Alert>
        </div>
      )}
      {actionError && (
        <div style={{ marginBottom: 18 }}>
          <Alert>{actionError}</Alert>
        </div>
      )}

      <div className="editor-layout">
        <div className="stack" style={{ gap: 20 }}>
          <div className="card">
            <div className="card-header">
              <h2>Event details</h2>
              {!event.editable && <span className="muted small">Locked</span>}
            </div>
            <div className="card-body">
              <EventDetailsForm
                key={`${event.id}-${event.status}`}
                event={event}
                categories={categories}
                disabled={!event.editable}
                submitLabel="Save changes"
                onSave={async (input) => {
                  setEvent(await updateEvent(event.id, input))
                  setNotice('Changes saved.')
                  setActionError(null)
                }}
              />
            </div>
          </div>
          <TicketTypesSection event={event} onChange={(e) => { setEvent(e); setNotice(null) }} />
          {['PUBLISHED', 'CANCELLED', 'COMPLETED', 'REJECTED'].includes(event.status) && <EventSalesSection eventId={event.id} />}
        </div>

        <aside className="card status-panel">
          <div className="card-body stack" style={{ gap: 16 }}>
            <div>
              <span className="muted small">Status</span>
              <div style={{ marginTop: 6 }}>
                <EventStatusBadge status={event.status} />
              </div>
              <p className="muted small" style={{ marginTop: 10 }}>
                {STATUS_HELP[event.status]}
              </p>
            </div>

            {event.editable && (
              <ul className="checklist">
                <Check ok>Event details saved</Check>
                <Check ok={hasTickets}>At least one ticket type</Check>
                <Check ok={startsInFuture}>Starts in the future</Check>
              </ul>
            )}

            <div className="stack" style={{ gap: 8 }}>
              {event.editable && (
                <button type="button" className="btn btn-gradient btn-block" disabled={!canSubmit || busy !== null}
                  onClick={() => setConfirm('submit')}>
                  {busy === 'submit' ? <Spinner small /> : <Send size={16} />} Submit for approval
                </button>
              )}
              {event.status === 'PENDING_APPROVAL' && (
                <button type="button" className="btn btn-secondary btn-block" disabled={busy !== null}
                  onClick={() => run('withdraw')}>
                  {busy === 'withdraw' ? <Spinner small /> : <Undo2 size={16} />} Withdraw to edit
                </button>
              )}
              {event.status === 'DRAFT' && (
                <button type="button" className="btn btn-ghost btn-block danger-hover" disabled={busy !== null}
                  onClick={() => setConfirm('delete')}>
                  <Trash2 size={16} /> Delete draft
                </button>
              )}
              {canCancel && event.status !== 'DRAFT' && (
                <button type="button" className="btn btn-ghost btn-block danger-hover" disabled={busy !== null}
                  onClick={() => setConfirm('cancel')}>
                  <Ban size={16} /> Cancel event
                </button>
              )}
            </div>
          </div>
        </aside>
      </div>

      <ConfirmDialog
        open={confirm === 'submit'}
        title="Submit for approval?"
        confirmLabel="Submit"
        busy={busy === 'submit'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => run('submit')}
      >
        An admin will review your event before it goes live. You won't be able to edit it while it's being reviewed,
        unless you withdraw it.
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === 'delete'}
        title="Delete this draft?"
        confirmLabel="Delete draft"
        tone="danger"
        busy={busy === 'delete'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => run('delete')}
      >
        "{event.title}" and its ticket types will be permanently deleted.
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === 'cancel'}
        title="Cancel this event?"
        confirmLabel="Cancel event"
        tone="danger"
        busy={busy === 'cancel'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => run('cancel')}
      >
        The event will be removed from public listings. This can't be undone.
      </ConfirmDialog>
    </div>
  )
}

function Check({ ok, children }: { ok: boolean; children: React.ReactNode }) {
  return (
    <li className={ok ? 'ok' : ''}>
      {ok ? <CheckCircle2 size={16} /> : <Circle size={16} />} {children}
    </li>
  )
}
