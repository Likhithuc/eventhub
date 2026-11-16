import { Ticket } from 'lucide-react'
import { Link } from 'react-router-dom'

export function Logo({ onClick }: { onClick?: () => void }) {
  return (
    <Link to="/" className="logo" aria-label="EventHub home" onClick={onClick}>
      <span className="logo-mark">
        <Ticket size={18} strokeWidth={2.4} />
      </span>
      <span className="logo-text">
        Event<span>Hub</span>
      </span>
    </Link>
  )
}
