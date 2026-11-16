import { BarChart3, CalendarPlus, QrCode, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { refreshSession } from '../../api/http'
import { createOrganizerProfile, getMyOrganizerProfile } from '../../api/organizerApi'
import { LoadingBlock } from '../../components/ui/Spinner'
import { OrganizerProfileForm } from '../../features/organizer/OrganizerProfileForm'

/** Onboarding: any signed-in user can create an organizer profile, which grants the ORGANIZER role. */
export function BecomeOrganizerPage() {
  const navigate = useNavigate()
  const [state, setState] = useState<'checking' | 'none' | 'exists'>('checking')

  useEffect(() => {
    getMyOrganizerProfile()
      .then(() => setState('exists'))
      .catch(() => setState('none'))
  }, [])

  if (state === 'checking') return <LoadingBlock />
  if (state === 'exists') return <Navigate to="/organizer" replace />

  return (
    <div className="container page">
      <div className="onboard">
        <div className="onboard-intro">
          <span className="eyebrow">
            <Sparkles size={14} /> For organizers
          </span>
          <h1>Start hosting events on EventHub</h1>
          <p className="muted">
            Create your organizer profile to publish events, sell tickets and check guests in. It takes less than a
            minute.
          </p>
          <ul className="onboard-points">
            <li>
              <span className="stat-icon">
                <CalendarPlus size={18} />
              </span>
              <div>
                <strong>Create events</strong>
                <span>Draft details, set ticket types and capacity.</span>
              </div>
            </li>
            <li>
              <span className="stat-icon pink">
                <BarChart3 size={18} />
              </span>
              <div>
                <strong>Get approved, go live</strong>
                <span>Our team reviews each event before it's published.</span>
              </div>
            </li>
            <li>
              <span className="stat-icon green">
                <QrCode size={18} />
              </span>
              <div>
                <strong>Check guests in</strong>
                <span>Scan tickets at the door (coming soon).</span>
              </div>
            </li>
          </ul>
        </div>
        <div className="card card-body">
          <h2 style={{ marginBottom: 18 }}>Your organizer profile</h2>
          <OrganizerProfileForm
            submitLabel="Create profile"
            onSubmit={async (input) => {
              await createOrganizerProfile(input)
              // Pick up the new ORGANIZER role in a fresh access token.
              await refreshSession()
              navigate('/organizer', { replace: true })
            }}
          />
        </div>
      </div>
    </div>
  )
}
