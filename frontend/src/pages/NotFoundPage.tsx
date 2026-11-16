import { ArrowLeft, Compass } from 'lucide-react'
import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section className="status-page">
      <div>
        <div className="status-code">404</div>
        <h1>This page left the party</h1>
        <p>The page you're looking for doesn't exist or has moved.</p>
        <div className="row">
          <Link to="/" className="btn btn-primary">
            <ArrowLeft size={18} /> Back to home
          </Link>
          <Link to="/events" className="btn btn-secondary">
            <Compass size={18} /> Explore events
          </Link>
        </div>
      </div>
    </section>
  )
}
