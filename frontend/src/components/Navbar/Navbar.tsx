import { Compass, LayoutDashboard, LogOut, Menu, ShieldCheck, Ticket, UserRound, X } from 'lucide-react'
import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'
import { Logo } from '../ui/Logo'
import { UserMenu } from './UserMenu'

export function Navbar() {
  const { status, user, hasRole, logout } = useAuth()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const closeMobile = () => setMobileOpen(false)

  const onLogout = async () => {
    closeMobile()
    await logout()
    navigate('/')
  }

  const links = (
    <>
      <NavLink to="/events" className="nav-link" onClick={closeMobile}>
        <Compass size={16} /> Explore
      </NavLink>
      {status === 'authenticated' && (
        <NavLink to="/bookings" className="nav-link" onClick={closeMobile}>
          <Ticket size={16} /> My tickets
        </NavLink>
      )}
      {hasRole('ORGANIZER', 'ADMIN') && (
        <NavLink to="/organizer" className="nav-link" onClick={closeMobile}>
          <LayoutDashboard size={16} /> Organizer
        </NavLink>
      )}
      {hasRole('ADMIN') && (
        <NavLink to="/admin" className="nav-link" onClick={closeMobile}>
          <ShieldCheck size={16} /> Admin
        </NavLink>
      )}
    </>
  )

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Logo onClick={closeMobile} />
        <nav className="nav-links" aria-label="Main">
          {links}
        </nav>
        <div className="nav-actions">
          {status === 'authenticated' && user ? (
            <span className="hide-mobile">
              <UserMenu user={user} onLogout={onLogout} />
            </span>
          ) : status === 'anonymous' ? (
            <>
              <Link to="/login" className="btn btn-ghost hide-mobile">
                Log in
              </Link>
              <Link to="/register" className="btn btn-primary hide-mobile">
                Get started
              </Link>
            </>
          ) : null}
          <button
            type="button"
            className="btn btn-ghost btn-icon nav-burger"
            onClick={() => setMobileOpen((o) => !o)}
            aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <nav className="mobile-menu" aria-label="Mobile">
          {links}
          {status === 'authenticated' && user ? (
            <>
              <NavLink to="/account" className="nav-link" onClick={closeMobile}>
                <UserRound size={16} /> My account
              </NavLink>
              <button type="button" className="btn btn-secondary" onClick={onLogout}>
                <LogOut size={16} /> Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-secondary" onClick={closeMobile}>
                Log in
              </Link>
              <Link to="/register" className="btn btn-primary" onClick={closeMobile}>
                Get started
              </Link>
            </>
          )}
        </nav>
      )}
    </header>
  )
}
