import { Activity, Building2, ClipboardCheck, LayoutGrid, ShieldCheck, Users } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'

const LINKS = [
  { to: '/admin', label: 'Overview', icon: LayoutGrid, end: true },
  { to: '/admin/events', label: 'Event approvals', icon: ClipboardCheck },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/organizers', label: 'Organizers', icon: Building2 },
  { to: '/admin/activity', label: 'Activity log', icon: Activity },
]

export function AdminLayout() {
  return (
    <div className="container page admin-shell">
      <div className="admin-top">
        <span className="eyebrow">
          <ShieldCheck size={14} /> Admin console
        </span>
        <nav className="admin-nav" aria-label="Admin sections">
          {LINKS.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className="admin-nav-link">
              <Icon size={16} /> {label}
            </NavLink>
          ))}
        </nav>
      </div>
      <Outlet />
    </div>
  )
}
