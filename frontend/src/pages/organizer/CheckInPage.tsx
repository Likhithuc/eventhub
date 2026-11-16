import { AlertTriangle, ArrowLeft, CircleCheck, Clock, ScanLine, Users, XCircle } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { checkIn, getCheckInSummary, type CheckInResponse } from '../../api/checkInApi'
import { errorMessage } from '../../api/http'
import { getMyEvent } from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { LoadingBlock, Spinner } from '../../components/ui/Spinner'
import { formatEventWhen } from '../../lib/format'
import { useQuery } from '../../lib/useQuery'

const TONE: Record<CheckInResponse['result'], 'ok' | 'warn' | 'bad'> = {
  CHECKED_IN: 'ok',
  ALREADY_CHECKED_IN: 'warn',
  TOO_EARLY: 'warn',
  EVENT_ENDED: 'warn',
  NOT_PUBLISHED: 'warn',
  CANCELLED: 'bad',
  NOT_FOUND: 'bad',
}

const TITLE: Record<CheckInResponse['result'], string> = {
  CHECKED_IN: 'Checked in',
  ALREADY_CHECKED_IN: 'Already checked in',
  TOO_EARLY: 'Too early',
  EVENT_ENDED: 'Check-in closed',
  NOT_PUBLISHED: 'Event not live',
  CANCELLED: 'Ticket cancelled',
  NOT_FOUND: 'Ticket not found',
}

const time = (iso: string) => new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })

/** Door check-in. Works with typed codes and with keyboard-style QR scanners (scan + Enter). */
export function CheckInPage() {
  const { eventId = '' } = useParams()
  const event = useQuery(`ci-event-${eventId}`, (signal) => getMyEvent(eventId, signal))
  const summary = useQuery(`ci-summary-${eventId}`, (signal) => getCheckInSummary(eventId, signal))
  const inputRef = useRef<HTMLInputElement>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<CheckInResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  if (event.error) return <div className="container page"><Alert>{event.error}</Alert></div>
  if (!event.data) return <LoadingBlock />
  const e = event.data
  const s = summary.data
  const when = formatEventWhen(e.startsAt, e.endsAt)
  const pct = s && s.validTickets ? Math.round((s.checkedIn / s.validTickets) * 100) : 0

  const submit = async (ev: FormEvent) => {
    ev.preventDefault()
    const value = code.trim()
    if (!value || busy) return
    setBusy(true)
    setError(null)
    try {
      const r = await checkIn(eventId, value)
      setResult(r)
      if (r.result === 'CHECKED_IN') summary.reload()
      setCode('')
    } catch (err) {
      setError(errorMessage(err, 'Check-in failed. Try again.'))
    } finally {
      setBusy(false)
      inputRef.current?.focus()
    }
  }

  const tone = result ? TONE[result.result] : null
  const ResultIcon = tone === 'ok' ? CircleCheck : tone === 'warn' ? AlertTriangle : XCircle

  return (
    <div className="container page">
      <Link to={`/organizer/events/${eventId}`} className="text-link"><ArrowLeft size={16} /> Event</Link>
      <div className="page-header" style={{ marginTop: 14 }}>
        <div style={{ minWidth: 0 }}>
          <span className="eyebrow"><ScanLine size={14} /> Door check-in</span>
          <h1 className="break" style={{ marginTop: 6 }}>{e.title}</h1>
          <p>{when.date} · {when.time} · {e.venueName}</p>
        </div>
      </div>

      {s && !s.open && (
        <div style={{ marginBottom: 18 }}>
          <Alert tone="info">
            Check-in is open from {new Date(s.opensAt).toLocaleString('en-IN')} until {new Date(s.closesAt).toLocaleString('en-IN')}.
            Codes entered outside this window are refused.
          </Alert>
        </div>
      )}

      <div className="checkin-layout">
        <div className="stack" style={{ gap: 18 }}>
          <form className="card card-body checkin-form" onSubmit={submit}>
            <label className="field-label" htmlFor="checkin-code">Scan a QR code or type the ticket code</label>
            <div className="checkin-input-row">
              <input
                id="checkin-code"
                ref={inputRef}
                className="input checkin-input"
                value={code}
                onChange={(ev) => setCode(ev.target.value)}
                placeholder="e.g. 7K3M-9QX2"
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                maxLength={100}
              />
              <button type="submit" className="btn btn-primary btn-lg" disabled={busy || !code.trim()}>
                {busy ? <Spinner small /> : <ScanLine size={18} />} Check in
              </button>
            </div>
            <p className="muted small">USB and Bluetooth QR scanners work like a keyboard: scan straight into this box.</p>
          </form>

          {error && <Alert>{error}</Alert>}

          {result && tone && (
            <div className={`checkin-result tone-${tone}`} role="status" aria-live="assertive">
              <ResultIcon size={44} />
              <div className="checkin-result-body">
                <strong>{TITLE[result.result]}</strong>
                {result.ticket ? (
                  <>
                    <span className="checkin-name">{result.ticket.attendeeName}</span>
                    <span>{result.ticket.ticketType} · <span className="mono-plain">{result.ticket.code}</span> · booking {result.ticket.bookingReference}</span>
                  </>
                ) : (
                  <span>{result.message}</span>
                )}
                {result.result === 'ALREADY_CHECKED_IN' && result.checkedInAt && (
                  <span className="checkin-when">
                    <Clock size={14} /> First admitted at {time(result.checkedInAt)}{result.checkedInBy && ` by ${result.checkedInBy}`}
                  </span>
                )}
                {result.ticket && result.result !== 'CHECKED_IN' && result.result !== 'ALREADY_CHECKED_IN' && (
                  <span>{result.message}</span>
                )}
              </div>
            </div>
          )}
        </div>

        <aside className="stack" style={{ gap: 18 }}>
          <div className="card card-body">
            <span className="muted small"><Users size={14} style={{ verticalAlign: '-2px' }} /> Admitted</span>
            <div className="checkin-count">
              <strong>{s ? s.checkedIn : '–'}</strong>
              <span>/ {s ? s.validTickets : '–'}</span>
            </div>
            <div className="meter" aria-hidden="true"><span style={{ width: `${pct}%` }} /></div>
            <p className="muted small" style={{ marginTop: 8 }}>{pct}% of valid tickets</p>
          </div>
          <div className="card">
            <div className="card-header"><h2>Recent</h2></div>
            {s && s.recent.length === 0 ? (
              <p className="muted small" style={{ padding: '16px 24px' }}>No one checked in yet.</p>
            ) : (
              <ul className="recent-list">
                {s?.recent.map((r) => (
                  <li key={r.code + r.checkedInAt}>
                    <CircleCheck size={16} className="text-success" />
                    <span className="recent-main">
                      <strong>{r.attendeeName}</strong>
                      <span>{r.ticketType} · {r.code}</span>
                    </span>
                    <time className="muted small">{time(r.checkedInAt)}</time>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </aside>
      </div>
    </div>
  )
}
