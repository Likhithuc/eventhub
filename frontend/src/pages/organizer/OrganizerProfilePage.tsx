import { ArrowLeft, CheckCircle2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../../api/http'
import { getMyOrganizerProfile, updateOrganizerProfile, type OrganizerProfile } from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { LoadingBlock } from '../../components/ui/Spinner'
import { OrganizerProfileForm } from '../../features/organizer/OrganizerProfileForm'
import { VerificationBadge } from '../../features/organizer/VerificationBadge'

export function OrganizerProfilePage() {
  const [profile, setProfile] = useState<OrganizerProfile | null>(null)
  const [missing, setMissing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    getMyOrganizerProfile()
      .then(setProfile)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setMissing(true)
        else setError(errorMessage(err))
      })
  }, [])

  if (missing) return <Navigate to="/organizer/onboarding" replace />
  if (error)
    return (
      <div className="container page">
        <Alert>{error}</Alert>
      </div>
    )
  if (!profile) return <LoadingBlock />

  return (
    <div className="container page narrow-page">
      <Link to="/organizer" className="text-link">
        <ArrowLeft size={16} /> Dashboard
      </Link>
      <div className="page-header" style={{ marginTop: 14 }}>
        <div>
          <h1>Organizer profile</h1>
          <p>This is how attendees see you on your events.</p>
        </div>
        <VerificationBadge status={profile.verificationStatus} />
      </div>
      <div className="card card-body">
        {saved && (
          <div style={{ marginBottom: 16 }}>
            <Alert tone="info">
              <span className="row" style={{ gap: 6 }}>
                <CheckCircle2 size={15} /> Profile saved
              </span>
            </Alert>
          </div>
        )}
        <OrganizerProfileForm
          initial={profile}
          submitLabel="Save changes"
          onSubmit={async (input) => {
            setSaved(false)
            setProfile(await updateOrganizerProfile(input))
            setSaved(true)
          }}
        />
      </div>
    </div>
  )
}
