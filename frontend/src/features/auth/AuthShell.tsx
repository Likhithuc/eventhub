import { CalendarHeart, QrCode, Sparkles, Ticket } from 'lucide-react'
import type { ReactNode } from 'react'

interface Props {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}

/** Split-screen frame for login and registration: brand panel left, form right. */
export function AuthShell({ title, subtitle, children, footer }: Props) {
  return (
    <div className="auth">
      <aside className="auth-aside">
        <div>
          <span className="eyebrow" style={{ color: 'rgba(255,255,255,0.85)' }}>
            <Sparkles size={14} /> EventHub
          </span>
          <h2 style={{ marginTop: 18 }}>
            Every great night out
            <br />
            starts with a ticket.
          </h2>
          <p>Concerts, meetups, parties and workshops, from organizers near you, booked in seconds.</p>
          <div className="auth-points">
            <div className="auth-point">
              <i>
                <CalendarHeart size={18} />
              </i>
              Discover events by city, date and vibe
            </div>
            <div className="auth-point">
              <i>
                <Ticket size={18} />
              </i>
              Reserve tickets with instant confirmation
            </div>
            <div className="auth-point">
              <i>
                <QrCode size={18} />
              </i>
              Skip the queue with a QR code at the door
            </div>
          </div>
        </div>
        <blockquote className="auth-quote">
          “We sold out our first meetup in two days, and check-in took minutes.”
          <footer>Example organizer story</footer>
        </blockquote>
      </aside>

      <section className="auth-main">
        <div className="auth-card">
          <h1>{title}</h1>
          <p>{subtitle}</p>
          {children}
          <div className="auth-switch">{footer}</div>
        </div>
      </section>
    </div>
  )
}
