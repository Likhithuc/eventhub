import { useCallback, useEffect, useState } from 'react'
import { getHealth, type HealthResponse } from '../../api/healthApi'
import { Logo } from '../ui/Logo'

type Health = 'checking' | 'up' | 'degraded' | 'down'

const LABEL: Record<Health, string> = {
  checking: 'Checking status…',
  up: 'All systems operational',
  degraded: 'Database unavailable',
  down: 'Server unreachable',
}

/** Small live status indicator; click to re-check. */
function StatusPill() {
  const [health, setHealth] = useState<Health>('checking')

  const check = useCallback(() => {
    getHealth()
      .then((h: HealthResponse) => setHealth(h.database === 'UP' ? 'up' : 'degraded'))
      .catch((err: unknown) => {
        const body = (err as { body?: HealthResponse }).body
        setHealth(body?.status === 'DEGRADED' ? 'degraded' : 'down')
      })
  }, [])

  useEffect(() => {
    check()
  }, [check])

  return (
    <button
      type="button"
      className="status-pill"
      onClick={() => {
        setHealth('checking')
        check()
      }}
      title="Click to re-check"
    >
      <span className={`status-dot ${health}`} />
      {LABEL[health]}
    </button>
  )
}

const YEAR = new Date().getFullYear()

export function Footer() {
  return (
    <footer className="footer">
      <div className="container footer-inner">
        <div className="row" style={{ gap: 20 }}>
          <Logo />
          <span className="muted small">© {YEAR} EventHub</span>
        </div>
        <div className="row" style={{ gap: 20 }}>
          <nav className="footer-links" aria-label="Footer">
            <a href="#">About</a>
            <a href="#">Terms</a>
            <a href="#">Privacy</a>
          </nav>
          <StatusPill />
        </div>
      </div>
    </footer>
  )
}
