import { createBrowserRouter } from 'react-router-dom'
import { ProtectedRoute } from '../components/ProtectedRoute/ProtectedRoute'
import { PublicLayout } from '../layouts/PublicLayout'
import { AccountPage } from '../pages/AccountPage'
import { BookingDetailPage } from '../pages/bookings/BookingDetailPage'
import { MyBookingsPage } from '../pages/bookings/MyBookingsPage'
import { AdminLayout } from '../features/admin/AdminLayout'
import { AdminActivityPage } from '../pages/admin/AdminActivityPage'
import { AdminEventReviewPage } from '../pages/admin/AdminEventReviewPage'
import { AdminEventsPage } from '../pages/admin/AdminEventsPage'
import { AdminOrganizersPage } from '../pages/admin/AdminOrganizersPage'
import { AdminOverviewPage } from '../pages/admin/AdminOverviewPage'
import { AdminUsersPage } from '../pages/admin/AdminUsersPage'
import { EventDetailsPage } from '../pages/EventDetailsPage'
import { EventsPage } from '../pages/EventsPage'
import { HomePage } from '../pages/HomePage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { BecomeOrganizerPage } from '../pages/organizer/BecomeOrganizerPage'
import { CheckInPage } from '../pages/organizer/CheckInPage'
import { EventEditorPage } from '../pages/organizer/EventEditorPage'
import { OrganizerDashboardPage } from '../pages/organizer/OrganizerDashboardPage'
import { OrganizerProfilePage } from '../pages/organizer/OrganizerProfilePage'
import { RegisterPage } from '../pages/RegisterPage'

export const router = createBrowserRouter([
  {
    element: <PublicLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/events', element: <EventsPage /> },
      { path: '/events/:eventId', element: <EventDetailsPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      {
        path: '/bookings',
        element: (
          <ProtectedRoute>
            <MyBookingsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/bookings/:bookingId',
        element: (
          <ProtectedRoute>
            <BookingDetailPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/account',
        element: (
          <ProtectedRoute>
            <AccountPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/organizer/onboarding',
        element: (
          <ProtectedRoute>
            <BecomeOrganizerPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/organizer',
        element: (
          <ProtectedRoute roles={['ORGANIZER', 'ADMIN']}>
            <OrganizerDashboardPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/organizer/profile',
        element: (
          <ProtectedRoute roles={['ORGANIZER', 'ADMIN']}>
            <OrganizerProfilePage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/organizer/events/new',
        element: (
          <ProtectedRoute roles={['ORGANIZER', 'ADMIN']}>
            <EventEditorPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/organizer/events/:eventId/check-in',
        element: (
          <ProtectedRoute roles={['ORGANIZER', 'ADMIN']}>
            <CheckInPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/organizer/events/:eventId',
        element: (
          <ProtectedRoute roles={['ORGANIZER', 'ADMIN']}>
            <EventEditorPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/admin',
        element: (
          <ProtectedRoute roles={['ADMIN']}>
            <AdminLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <AdminOverviewPage /> },
          { path: 'events', element: <AdminEventsPage /> },
          { path: 'events/:eventId', element: <AdminEventReviewPage /> },
          { path: 'users', element: <AdminUsersPage /> },
          { path: 'organizers', element: <AdminOrganizersPage /> },
          { path: 'activity', element: <AdminActivityPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
