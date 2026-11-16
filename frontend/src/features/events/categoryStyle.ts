import {
  Code2,
  Hammer,
  Mic2,
  Music,
  Palette,
  PartyPopper,
  Sparkles,
  Trophy,
  UtensilsCrossed,
  type LucideIcon,
} from 'lucide-react'

export interface CategoryStyle {
  icon: LucideIcon
  gradient: string
  hint: string
}

/** Visual identity per category slug (until organizers upload banners). */
const STYLES: Record<string, CategoryStyle> = {
  music: { icon: Music, gradient: 'linear-gradient(135deg,#7c3aed,#ec4899)', hint: 'Gigs & concerts' },
  nightlife: { icon: PartyPopper, gradient: 'linear-gradient(135deg,#db2777,#f97316)', hint: 'Parties & DJ sets' },
  tech: { icon: Code2, gradient: 'linear-gradient(135deg,#0ea5e9,#6366f1)', hint: 'Meetups & hackathons' },
  comedy: { icon: Mic2, gradient: 'linear-gradient(135deg,#f59e0b,#ef4444)', hint: 'Stand-up & improv' },
  'food-drink': { icon: UtensilsCrossed, gradient: 'linear-gradient(135deg,#10b981,#0ea5e9)', hint: 'Tastings & pop-ups' },
  arts: { icon: Palette, gradient: 'linear-gradient(135deg,#a855f7,#6366f1)', hint: 'Exhibitions & shows' },
  sports: { icon: Trophy, gradient: 'linear-gradient(135deg,#22c55e,#0d9488)', hint: 'Runs & matches' },
  workshops: { icon: Hammer, gradient: 'linear-gradient(135deg,#f97316,#e11d48)', hint: 'Classes & hands-on' },
}

const FALLBACK: CategoryStyle = { icon: Sparkles, gradient: 'linear-gradient(135deg,#7c3aed,#c026d3)', hint: 'Events' }

export function categoryStyle(slug: string | undefined): CategoryStyle {
  return (slug && STYLES[slug]) || FALLBACK
}
