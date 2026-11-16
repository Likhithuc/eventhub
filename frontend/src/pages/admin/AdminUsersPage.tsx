import { ChevronLeft, ChevronRight, Search, UserCheck, UserX, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listUsers, setUserStatus } from '../../api/adminApi'
import { errorMessage } from '../../api/http'
import type { Role, User, UserStatus } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Avatar } from '../../components/ui/Avatar'
import { RoleBadges, StatusBadge } from '../../components/ui/Badges'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingBlock } from '../../components/ui/Spinner'
import { useAuth } from '../../features/auth/useAuth'
import { useQuery } from '../../lib/useQuery'

export function AdminUsersPage() {
  const { user: me } = useAuth()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const role = (params.get('role') ?? '') as Role | ''
  const status = (params.get('status') ?? '') as UserStatus | ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const { data, error, loading, reload } = useQuery(`${q}|${role}|${status}|${page}`, (signal) =>
    listUsers({ q, role, status, page, size: 15 }, signal),
  )

  const [target, setTarget] = useState<User | null>(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v)
      else next.delete(k)
    }
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }
  const onSearch = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    update({ q: String(new FormData(e.currentTarget).get('q') ?? '').trim() })
  }

  const toggle = async () => {
    if (!target) return
    setBusy(true)
    setActionError(null)
    try {
      await setUserStatus(target.id, target.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE')
      reload()
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setBusy(false)
      setTarget(null)
    }
  }

  return (
    <div className="card">
      <form className="table-toolbar" onSubmit={onSearch} key={q} role="search">
        <div className="input-wrap" style={{ flex: '1 1 260px' }}>
          <Search size={16} className="input-icon" />
          <input name="q" className="input has-icon" defaultValue={q} placeholder="Search name or email" maxLength={100} />
        </div>
        <select className="input toolbar-select" value={role} onChange={(e) => update({ role: e.target.value })} aria-label="Role">
          <option value="">All roles</option>
          <option value="ATTENDEE">Attendees</option>
          <option value="ORGANIZER">Organizers</option>
          <option value="ADMIN">Admins</option>
        </select>
        <select className="input toolbar-select" value={status} onChange={(e) => update({ status: e.target.value })} aria-label="Status">
          <option value="">Any status</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
        </select>
        <button type="submit" className="btn btn-secondary">Search</button>
      </form>

      {(error || actionError) && <div className="card-body"><Alert>{error ?? actionError}</Alert></div>}
      {!data && loading && <LoadingBlock />}
      {data && data.content.length === 0 && (
        <EmptyState icon={<Users size={28} />} title="No users found">Try a different search or filter.</EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.55 : 1 }}>
            <table className="table">
              <thead>
                <tr><th>User</th><th>Roles</th><th>Status</th><th>Joined</th><th aria-label="Actions" /></tr>
              </thead>
              <tbody>
                {data.content.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <div className="user-cell">
                        <Avatar name={u.name} seed={u.id} size="sm" />
                        <div className="user-cell-text"><strong>{u.name}</strong><span>{u.email}</span></div>
                      </div>
                    </td>
                    <td><RoleBadges roles={u.roles} /></td>
                    <td><StatusBadge status={u.status} /></td>
                    <td className="muted">{new Date(u.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</td>
                    <td className="cell-actions">
                      {u.id === me?.id ? (
                        <span className="muted small">You</span>
                      ) : u.status === 'ACTIVE' ? (
                        <button type="button" className="btn btn-ghost btn-sm danger-hover" onClick={() => setTarget(u)}>
                          <UserX size={15} /> Suspend
                        </button>
                      ) : (
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => setTarget(u)}>
                          <UserCheck size={15} /> Reactivate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pager">
            <span className="muted small">{data.totalElements} users · page {data.page + 1} of {Math.max(1, data.totalPages)}</span>
            <div className="row" style={{ gap: 8 }}>
              <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
                <ChevronLeft size={16} /> Previous
              </button>
              <button type="button" className="btn btn-secondary btn-sm" disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>
                Next <ChevronRight size={16} />
              </button>
            </div>
          </div>
        </>
      )}

      <ConfirmDialog
        open={target !== null}
        title={target?.status === 'ACTIVE' ? `Suspend ${target?.name}?` : `Reactivate ${target?.name}?`}
        confirmLabel={target?.status === 'ACTIVE' ? 'Suspend account' : 'Reactivate account'}
        tone={target?.status === 'ACTIVE' ? 'danger' : 'primary'}
        cancelLabel="Cancel"
        busy={busy}
        onCancel={() => setTarget(null)}
        onConfirm={toggle}
      >
        {target?.status === 'ACTIVE'
          ? 'They will be signed out on all devices within 15 minutes and won’t be able to log in until reactivated.'
          : 'They will be able to log in again.'}
      </ConfirmDialog>
    </div>
  )
}
