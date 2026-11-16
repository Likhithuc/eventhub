import { ChevronDown, LayoutDashboard, LogOut, ShieldCheck, Ticket, UserRound } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import type { User } from '../../api/types'
import { useAuth } from '../../features/auth/useAuth'
import { Avatar } from '../ui/Avatar'
import { RoleBadges } from '../ui/Badges'

export function UserMenu({ user, onLogout }: { user: User; onLogout: () => void }) {
  const { hasRole } = useAuth()
  const { pathname } = useLocation()
  // Store the path the menu was opened on, so any navigation closes it without an effect.
  const [openOn, setOpenOn] = useState<string | null>(null)
  const open = openOn === pathname
  const close = () => setOpenOn(null)
  const toggle = () => setOpenOn(open ? null : pathname)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpenOn(null)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpenOn(null)
    }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="menu" ref={ref}>
      <button
        type="button"
        className="menu-trigger"
        onClick={toggle}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <Avatar name={user.name} seed={user.id} size="sm" />
        <span className="menu-trigger-name">{user.name.split(' ')[0]}</span>
        <ChevronDown size={15} />
      </button>
      {open && (
        <div className="menu-panel" role="menu">
          <div className="menu-head">
            <Avatar name={user.name} seed={user.id} />
            <div className="menu-head-text">
              <strong>{user.name}</strong>
              <span>{user.email}</span>
            </div>
          </div>
          <div style={{ padding: '0 10px 8px' }}>
            <RoleBadges roles={user.roles} />
          </div>
          <div className="menu-sep" />
          <Link to="/account" className="menu-item" role="menuitem" onClick={close}>
            <UserRound size={16} /> My account
          </Link>
          <Link to="/bookings" className="menu-item" role="menuitem" onClick={close}>
            <Ticket size={16} /> My tickets
          </Link>
          {hasRole('ORGANIZER', 'ADMIN') && (
            <Link to="/organizer" className="menu-item" role="menuitem" onClick={close}>
              <LayoutDashboard size={16} /> Organizer dashboard
            </Link>
          )}
          {hasRole('ADMIN') && (
            <Link to="/admin" className="menu-item" role="menuitem" onClick={close}>
              <ShieldCheck size={16} /> Admin console
            </Link>
          )}
          <div className="menu-sep" />
          <button
            type="button"
            className="menu-item danger"
            role="menuitem"
            onClick={() => {
              close()
              onLogout()
            }}
          >
            <LogOut size={16} /> Log out
          </button>
        </div>
      )}
    </div>
  )
}
