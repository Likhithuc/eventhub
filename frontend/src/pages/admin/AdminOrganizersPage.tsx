import { BadgeCheck, Building2, CircleSlash, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listOrganizers, setOrganizerVerification, type AdminOrganizerRow } from '../../api/adminApi'
import { errorMessage } from '../../api/http'
import type { VerificationStatus } from '../../api/organizerApi'
import { Alert } from '../../components/ui/Alert'
import { Avatar } from '../../components/ui/Avatar'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock, Spinner } from '../../components/ui/Spinner'
import { VerificationBadge } from '../../features/organizer/VerificationBadge'
import { useQuery } from '../../lib/useQuery'

const TABS: { value: VerificationStatus | ''; label: string }[] = [
  { value: 'PENDING', label: 'To verify' },
  { value: 'VERIFIED', label: 'Verified' },
  { value: 'REJECTED', label: 'Declined' },
  { value: '', label: 'All' },
]

export function AdminOrganizersPage() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('verification')
  const verification = (raw === null ? 'PENDING' : raw === 'ALL' ? '' : raw) as VerificationStatus | ''
  const { data, error, loading, reload } = useQuery(`organizers|${verification}`, (signal) =>
    listOrganizers({ verification, size: 50 }, signal),
  )
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const set = async (o: AdminOrganizerRow, status: VerificationStatus) => {
    setBusyId(o.id)
    setActionError(null)
    try {
      await setOrganizerVerification(o.id, status)
      reload()
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="card">
      <div className="tabs" role="tablist" aria-label="Verification status">
        {TABS.map((t) => (
          <button key={t.label} type="button" role="tab" aria-selected={verification === t.value}
            className={`tab${verification === t.value ? ' active' : ''}`}
            onClick={() => setParams(t.value === 'PENDING' ? {} : { verification: t.value || 'ALL' })}>
            {t.label}
          </button>
        ))}
      </div>

      {(error || actionError) && <div className="card-body"><Alert>{error ?? actionError}</Alert></div>}
      {!data && loading && <LoadingBlock />}
      {data && data.content.length === 0 && (
        <EmptyState icon={<Building2 size={28} />} title={verification === 'PENDING' ? 'No organizers to verify' : 'No organizers here'}>
          {verification === 'PENDING' ? 'New organizer profiles will appear here for verification.' : 'Try another tab.'}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <ul className="org-list" style={{ opacity: loading ? 0.55 : 1 }}>
          {data.content.map((o) => (
            <li key={o.id} className="org-row">
              <Avatar name={o.displayName} seed={o.id} />
              <div className="org-main">
                <div className="row" style={{ gap: 10 }}>
                  <strong>{o.displayName}</strong>
                  <VerificationBadge status={o.verificationStatus} />
                </div>
                <span className="muted small">
                  {o.userName} · {o.userEmail}
                  {o.contactEmail && o.contactEmail !== o.userEmail && ` · contact ${o.contactEmail}`}
                </span>
                {o.description && <p className="muted small org-desc">{o.description}</p>}
                <span className="small">
                  {o.events} event{o.events === 1 ? '' : 's'} · {o.publishedEvents} published · joined{' '}
                  {new Date(o.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
                </span>
              </div>
              <div className="org-actions">
                {busyId === o.id ? (
                  <Spinner />
                ) : (
                  <>
                    {o.verificationStatus !== 'VERIFIED' && (
                      <button type="button" className="btn btn-success btn-sm" onClick={() => set(o, 'VERIFIED')}>
                        <BadgeCheck size={15} /> Verify
                      </button>
                    )}
                    {o.verificationStatus !== 'REJECTED' && (
                      <button type="button" className="btn btn-ghost btn-sm danger-hover" onClick={() => set(o, 'REJECTED')}>
                        <CircleSlash size={15} /> Decline
                      </button>
                    )}
                    {o.verificationStatus !== 'PENDING' && (
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => set(o, 'PENDING')}>
                        <RotateCcw size={15} /> Reset
                      </button>
                    )}
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
