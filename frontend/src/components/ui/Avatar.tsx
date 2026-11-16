const GRADIENTS = [
  'linear-gradient(135deg, #7c3aed, #ec4899)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #10b981, #0ea5e9)',
  'linear-gradient(135deg, #a855f7, #6366f1)',
  'linear-gradient(135deg, #ec4899, #f97316)',
]

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0][0] ?? ''
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? '') : ''
  return (first + last).toUpperCase()
}

/** Stable colour per user, derived from a seed such as the user id. */
function gradientFor(seed: string): string {
  let h = 0
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) | 0
  return GRADIENTS[Math.abs(h) % GRADIENTS.length]
}

interface Props {
  name: string
  seed?: string
  size?: 'sm' | 'md' | 'lg'
}

export function Avatar({ name, seed, size = 'md' }: Props) {
  return (
    <span
      className={`avatar${size === 'md' ? '' : ` avatar-${size}`}`}
      style={{ '--avatar-bg': gradientFor(seed ?? name) } as React.CSSProperties}
      aria-hidden="true"
    >
      {initials(name)}
    </span>
  )
}
