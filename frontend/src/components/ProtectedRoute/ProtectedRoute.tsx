import { ArrowLeft, Lock } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import type { Role } from '../../api/types'
import { useAuth } from '../../features/auth/useAuth'
import { LoadingBlock } from '../ui/Spinner'

interface Props {
  children: ReactNode
  /** If given, the user needs at least one of these roles. */
  roles?: Role[]
}

/**
 * UX-only guard: hides pages the user cannot use. Real enforcement happens
 * in the backend, which rejects every unauthorised API call regardless.
 */
export function ProtectedRoute({ children, roles }: Props) {
  const { status, hasRole } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <LoadingBlock />
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (roles && !hasRole(...roles)) {
    return (
      <section className="status-page">
        <div>
          <div className="empty-icon" style={{ margin: '0 auto 18px' }}>
            <Lock size={28} />
          </div>
          <h1>You don't have access</h1>
          <p>Your account doesn't have permission to view this page.</p>
          <div className="row">
            <Link to="/" className="btn btn-primary">
              <ArrowLeft size={18} /> Back to home
            </Link>
          </div>
        </div>
      </section>
    )
  }
  return <>{children}</>
}
