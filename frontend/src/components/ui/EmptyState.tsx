import type { ReactNode } from 'react'

interface Props {
  icon: ReactNode
  title: string
  children?: ReactNode
  actions?: ReactNode
}

export function EmptyState({ icon, title, children, actions }: Props) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {actions && <div className="row">{actions}</div>}
    </div>
  )
}
