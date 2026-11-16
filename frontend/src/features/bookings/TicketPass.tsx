import { CircleCheck, CircleSlash } from 'lucide-react'
import QRCode from 'qrcode'
import { useEffect, useState } from 'react'
import type { Pass } from '../../api/bookingApi'

/** One entry pass: QR code (random token, no personal data) plus the manual code. */
export function TicketPass({ pass, index, total }: { pass: Pass; index: number; total: number }) {
  const [img, setImg] = useState<{ for: string; src: string } | null>(null)

  useEffect(() => {
    if (!pass.qr) return
    let cancelled = false
    QRCode.toDataURL(pass.qr, { errorCorrectionLevel: 'M', margin: 1, width: 220, color: { dark: '#17142b', light: '#ffffff' } })
      .then((src) => {
        if (!cancelled) setImg({ for: pass.qr!, src })
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [pass.qr])

  const checkedIn = pass.status === 'CHECKED_IN'
  const cancelled = pass.status === 'CANCELLED'

  return (
    <div className={`pass${checkedIn ? ' is-used' : ''}${cancelled ? ' is-void' : ''}`}>
      <div className="pass-head">
        <span>{pass.ticketTypeName}</span>
        <span className="muted">{index + 1} of {total}</span>
      </div>
      <div className="pass-qr">
        {cancelled ? (
          <div className="pass-overlay"><CircleSlash size={34} /><span>Cancelled</span></div>
        ) : img && img.for === pass.qr ? (
          <img src={img.src} alt={`QR code for ticket ${pass.code}`} width={180} height={180} />
        ) : (
          <div className="pass-qr-loading skeleton" />
        )}
        {checkedIn && (
          <div className="pass-overlay pass-overlay-used">
            <CircleCheck size={34} />
            <span>Checked in</span>
          </div>
        )}
      </div>
      <div className="pass-code" aria-label="Ticket code">{pass.code}</div>
      <div className="pass-foot muted small">
        {checkedIn && pass.checkedInAt
          ? `Admitted ${new Date(pass.checkedInAt).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })}`
          : cancelled ? 'Not valid for entry' : 'Show at the entrance'}
      </div>
    </div>
  )
}
