import { Outlet, useLocation } from 'react-router-dom'
import { Footer } from '../components/Footer/Footer'
import { Navbar } from '../components/Navbar/Navbar'

const NO_FOOTER = ['/login', '/register']

export function PublicLayout() {
  const { pathname } = useLocation()
  return (
    <div className="app-shell">
      <Navbar />
      <main className="app-main">
        <Outlet />
      </main>
      {!NO_FOOTER.includes(pathname) && <Footer />}
    </div>
  )
}
