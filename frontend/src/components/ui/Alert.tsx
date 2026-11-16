import { AlertCircle, Info } from 'lucide-react'
import type { ReactNode } from 'react'

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'info'; children: ReactNode }) {
  return (
    <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {tone === 'error' ? <AlertCircle size={16} /> : <Info size={16} />}
      <div>{children}</div>
    </div>
  )
}
