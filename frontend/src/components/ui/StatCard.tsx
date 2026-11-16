import type { ReactNode } from 'react'

interface Props {
  icon: ReactNode
  label: string
  value: ReactNode
  tone?: 'violet' | 'pink' | 'green' | 'amber'
}

export function StatCard({ icon, label, value, tone = 'violet' }: Props) {
  return (
    <div className="card stat">
      <div className={`stat-icon${tone === 'violet' ? '' : ` ${tone}`}`}>{icon}</div>
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
    </div>
  )
}
