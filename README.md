# EventHub

An event booking platform: organizers publish events, attendees discover and book tickets, admins moderate.

**Status:** Phase 7 (QR check-in and notifications) complete — every booked seat gets its own QR pass, organizers check people in at the door (each ticket admits once), and attendees get a confirmation email. Phases 1–6 are done. Paid tickets wait for online payments (Phase 9).

## Stack

| Layer     | Technology                                                       |
| --------- | ---------------------------------------------------------------- |
| Frontend  | React 19 + Vite + TypeScript                                     |
| UI        | Hand-written CSS design system (`frontend/src/styles/`, light + dark), lucide-react icons, self-hosted Inter & Plus Jakarta Sans |
| Backend   | Java 21, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator) |
| Database  | PostgreSQL 17 (Docker Compose locally)                           |
| Migrations| Flyway                                                           |
| Tests     | JUnit 5, MockMvc, Testcontainers                                 |

## Repository layout

```
.
├── backend/              Spring Boot app (Maven wrapper included)
│   └── src/main/resources/db/migration/   Flyway migrations (V1__, V2__, ...)
├── frontend/             React + Vite app
├── docker-compose.yml    Local PostgreSQL
└── .env.example          Local configuration template
```

## Prerequisites

- Java 21 (no Maven install needed — use `./mvnw`)
- Node.js 20+ and npm
- Docker Desktop (running)

## Run locally

### 1. Configure

```bash
cp .env.example .env
```

Then edit `.env`:

- **`JWT_SECRET`** (required) — the backend refuses to start without it. Generate one:
  - Git Bash: `openssl rand -base64 32`
  - PowerShell: `[Convert]::ToBase64String((1..32 | % { [byte](Get-Random -Max 256) }))`
- **`DEV_ADMIN_PASSWORD`** / **`DEV_ORGANIZER_PASSWORD`** — pick passwords (8+ chars) to get seeded local admin and organizer accounts (see [Dev accounts](#dev-accounts)). Leave blank to skip seeding.

The Docker PostgreSQL is exposed on host port **5433** (not 5432) so it does not clash with a locally installed PostgreSQL.

### 2. Start PostgreSQL and Mailpit

```bash
docker compose up -d
docker compose ps          # wait until "healthy"
```

**Mailpit** is a local mail catcher: the backend sends booking confirmation emails to it instead of real inboxes. Read them at http://localhost:8025.

### 3. Start the backend (http://localhost:8080)

```bash
cd backend
./mvnw spring-boot:run      # Windows cmd/PowerShell: .\mvnw.cmd spring-boot:run
```

On startup Flyway applies migrations from `backend/src/main/resources/db/migration`. The backend reads the repo-root `.env` automatically; real environment variables override it.

### 4. Start the frontend (http://localhost:5173)

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — the **System status** card should show both *Backend API* and *PostgreSQL* as **UP**.

In development Vite proxies `/api/*` to the backend, so no CORS setup is needed in the browser. For a separately hosted frontend, set `VITE_API_BASE_URL` (see `frontend/.env.example`) and add its origin to `CORS_ALLOWED_ORIGINS`.

## Useful endpoints

| Endpoint                     | Auth   | Purpose                                         |
| ---------------------------- | ------ | ----------------------------------------------- |
| `GET /api/v1/health`         | Public | App + DB check used by the React app (503 if DB down) |
| `GET /actuator/health`       | Public | Infrastructure probe (with `/liveness`, `/readiness`) |
| `POST /api/v1/auth/register` | Public | Create an attendee account; returns access token + sets refresh cookie |
| `POST /api/v1/auth/login`    | Public | Log in; same response as register              |
| `POST /api/v1/auth/refresh`  | Cookie | Rotate refresh cookie, get a new access token  |
| `POST /api/v1/auth/logout`   | Cookie | Revoke refresh token, clear cookie             |
| `GET /api/v1/categories`     | Public | Event categories (seeded reference data)        |
| `GET /api/v1/events`         | Public | Upcoming **published** events, soonest first. Filters: `q` (title), `city`, `category` (slug), `from`/`to` (inclusive `yyyy-mm-dd`, platform timezone), `page`, `size` (max 50) |
| `GET /api/v1/events/cities`  | Public | Cities with upcoming published events          |
| `GET /api/v1/events/{id}`    | Public | Event details with organizer and ticket types; 404 for anything not published |
| `POST /api/v1/bookings`      | Any signed-in user | Book tickets: `{"eventId", "items":[{"ticketTypeId","quantity"}]}`. Send an `Idempotency-Key` header; a retry with the same key returns the original booking (200) instead of a duplicate (201 = new). Free tickets only for now; max 10 per booking |
| `GET /api/v1/bookings/me?scope=UPCOMING\|PAST\|CANCELLED` | Any signed-in user | Your bookings |
| `GET /api/v1/bookings/{id}`  | Owner (or ADMIN) | Booking details; others get 404 |
| `POST /api/v1/bookings/{id}/cancel` | Owner (or ADMIN) | Cancel within the event's cancellation window; tickets return to inventory |
| `GET /api/v1/organizer/events/{id}/bookings` | Event owner (or ADMIN) | Sales per ticket type and attendee list (with checked-in counts) |
| `POST /api/v1/organizer/events/{id}/check-ins` | Event owner (or ADMIN) | `{"code": "7K3M-9QX2"}` or the QR payload `EH1.<token>`. 200 `CHECKED_IN`; 409 `ALREADY_CHECKED_IN` / `CANCELLED` / `TOO_EARLY` / `EVENT_ENDED` / `NOT_PUBLISHED`; 404 `NOT_FOUND`. Same JSON body shape for all |
| `GET /api/v1/organizer/events/{id}/check-ins/summary` | Event owner (or ADMIN) | Admitted / valid tickets, check-in window, recent check-ins |
| `GET /api/v1/users/me`       | Any signed-in user | Current user profile               |
| `GET/POST/PUT /api/v1/me/organizer-profile` | Any signed-in user | Read / create / update your organizer profile. Creating one grants the ORGANIZER role (refresh the token to pick it up) |
| `GET /api/v1/organizer/dashboard` | ORGANIZER or ADMIN | Your profile (or `null`) and event counts by status |
| `GET /api/v1/organizer/events?status=&page=&size=` | ORGANIZER or ADMIN | Your own events, any status |
| `POST /api/v1/organizer/events` | ORGANIZER or ADMIN | Create an event (starts as `DRAFT`) |
| `GET/PUT/DELETE /api/v1/organizer/events/{id}` | Owner (or ADMIN) | Read / edit (draft or rejected only) / delete (draft only) |
| `POST /api/v1/organizer/events/{id}/submit` | Owner (or ADMIN) | `DRAFT`/`REJECTED` → `PENDING_APPROVAL` (needs ≥1 ticket type, future start) |
| `POST /api/v1/organizer/events/{id}/withdraw` | Owner (or ADMIN) | `PENDING_APPROVAL` → `DRAFT` |
| `POST /api/v1/organizer/events/{id}/cancel` | Owner (or ADMIN) | Cancel an event that hasn't ended; hides it publicly |
| `POST /api/v1/organizer/events/{id}/ticket-types`, `PUT/DELETE …/ticket-types/{ticketTypeId}` | Owner (or ADMIN) | Manage ticket types while the event is editable |
| `GET /api/v1/admin/overview` | ADMIN  | Platform metrics: users, organizers, events by status, pending approvals |
| `GET /api/v1/admin/events?status=&q=&page=` | ADMIN | Moderation list (pending: oldest submission first) |
| `GET /api/v1/admin/events/{id}` | ADMIN | Full event + organizer + review info + decision history |
| `POST /api/v1/admin/events/{id}/approve` | ADMIN | `PENDING_APPROVAL` → `PUBLISHED` (must still start in the future) |
| `POST /api/v1/admin/events/{id}/reject` | ADMIN | `PENDING_APPROVAL` → `REJECTED`; body `{"reason": "..."}` (5–1000 chars, shown to the organizer) |
| `POST /api/v1/admin/events/{id}/unpublish` | ADMIN | `PUBLISHED` → `REJECTED` with a reason (takedown) |
| `GET /api/v1/admin/users?q=&role=&status=&page=` | ADMIN | Search users |
| `PATCH /api/v1/admin/users/{id}/status` | ADMIN | `{"status":"SUSPENDED"|"ACTIVE"}`; suspending revokes all refresh tokens. Admins can't change their own status |
| `GET /api/v1/admin/organizers?verification=` | ADMIN | Organizer profiles with event counts |
| `PATCH /api/v1/admin/organizers/{id}/verification` | ADMIN | `{"status":"VERIFIED"|"REJECTED"|"PENDING"}`; verified organizers get a badge on public events |
| `GET /api/v1/admin/audit-log?page=` | ADMIN | Admin activity, newest first |

Errors use RFC 9457 problem details: `{"status":409,"detail":"An account with this email already exists"}`; validation errors add an `errors` map of field → message.

## Authentication design

| Piece | Choice | Why |
|---|---|---|
| Passwords | BCrypt (`{bcrypt}` prefix via `DelegatingPasswordEncoder`), 8–72 chars | Slow hash; prefix allows algorithm upgrades |
| Access token | HS256 JWT, 15 min, claims `sub`=user id, `email`, `roles`; kept **in memory** by React | Short-lived; not readable from storage by XSS |
| Refresh token | Random 256-bit opaque value, 14 days, **HttpOnly** `SameSite=Strict` cookie scoped to `/api/v1/auth`; DB stores only its SHA-256 hash | JavaScript cannot read it; DB leak does not leak sessions |
| Rotation | Each refresh issues a new token and revokes the old one | Limits replay window |
| Reuse detection | Replaying an old token (outside a 30 s grace window for two-tab races) revokes **all** of that user's sessions | Detects stolen tokens |
| Roles | `ATTENDEE`, `ORGANIZER`, `ADMIN` in `user_roles`; a user may hold several. Registration always grants only `ATTENDEE` | Flexible role model; no self-promotion |
| Enforcement | URL rules in `SecurityConfig` **and** `@PreAuthorize` on controllers. React route guards are UX only | Hiding a button is not security |
| Ownership | Every organizer operation on an event loads it through one ownership check in `OrganizerEventService`. Another organizer's event answers **404** (same as missing), so IDs can't be probed. Admins have support access to any event | Roles alone don't stop organizer A editing organizer B's event |
| Suspended users | Cannot log in or refresh | Existing access tokens expire within 15 min |

In production set `REFRESH_COOKIE_SECURE=true` (HTTPS). If the frontend and API live on different sites, revisit `REFRESH_COOKIE_SAME_SITE` and add CSRF protection to the refresh endpoint.

## Dev accounts

With `APP_SEED_ENABLED=true` and passwords set in `.env`, startup creates (only if the email does not exist yet; existing users are never changed, passwords never logged):

| Email (default) | Roles |
|---|---|
| `admin@eventhub.local` | ADMIN, ATTENDEE |
| `organizer@eventhub.local` | ORGANIZER, ATTENDEE |

Anyone who registers through the UI is an ATTENDEE. Never enable seeding outside local development.

With `APP_SEED_DEMO_EVENTS=true` (the default when seeding is on) the dev organizer also gets an organizer profile ("Nightfall Collective") and 16 demo events across six cities: 12 published and upcoming, plus a draft, a pending, a rejected and a past event that must **not** appear publicly. Demo events are only created once (when the organizer has no profile yet); to regenerate them, reset the database.

## Moderation and audit

- Moderation decisions lock the event row (`SELECT … FOR UPDATE`), so two admins can't approve and reject the same event at once.
- Every admin action (approve, reject, unpublish, suspend, reactivate, verification changes) is written to `admin_audit_log` **in the same transaction** as the change, recording who did it, when, what and why. The log is append-only.
- Rejection and unpublish reasons are shown to the organizer in their event editor; resubmitting clears the old reason.
- Suspending a user revokes their refresh tokens; their current access token stops working when it expires (≤ 15 min).

## Booking and inventory (no overselling)

- `ticket_types.sold_quantity` is the inventory counter. A booking transaction locks the requested ticket-type rows with `SELECT … FOR UPDATE` (always in id order, so concurrent bookings can't deadlock), re-reads their counts, checks availability, increments `sold_quantity` and inserts the booking, all before committing. Concurrent bookings for the same tickets therefore queue up instead of racing.
- The database enforces `CHECK (sold_quantity <= total_quantity)` as a final safety net.
- **Idempotency:** clients send an `Idempotency-Key` per booking attempt (the React app generates one per ticket selection). Keys are unique per user (`UNIQUE (user_id, idempotency_key)`); the key is re-checked after taking the row locks, so simultaneous duplicates still produce a single booking.
- Prices are computed on the server and snapshotted in `booking_items.unit_price`; the client never sends prices.
- Cancelling a booking (allowed until the event's `cancellation_cutoff_hours` before the start, default 24) returns its tickets to inventory. Cancelling an event cancels all its bookings.
- Organizers can't lower a ticket type's quantity below what's sold, or delete a ticket type that has bookings.
- Tests prove it: 12 simultaneous buyers for the last 5 tickets → exactly 5 bookings; two simultaneous requests for 7 of the last 10 → one succeeds; 3 simultaneous identical requests with the same key → one booking.

## Tickets, QR check-in and email

- **One pass per seat** (`tickets` table): a booking for 3 people gets 3 passes, each checked in independently.
- Each pass has a random 128-bit **token** (the QR payload, `EH1.<32 hex chars>`) and an 8-character **code** for manual entry (`7K3M-9QX2`, no 0/O/1/I). Neither contains personal data. The token is only ever returned to the booking's owner.
- **Duplicate-scan protection:** check-in locks the ticket row (`SELECT … FOR UPDATE`) and `check_ins.ticket_id` is `UNIQUE`, so a ticket admits exactly once even if scanned at two gates simultaneously (tested). A second scan answers "Already checked in" with the time and the staff member.
- Check-in is open from 24 h before the start until 12 h after the end, only for published events, and only for the organizer's own events. Codes from other events look like unknown codes.
- Cancelled bookings void their passes; a booking can't be cancelled once any of its tickets has been checked in.
- The check-in page works with typed codes and with USB/Bluetooth QR scanners (they type the payload + Enter). Camera scanning is a possible next step.
- **Confirmation email** (`BookingMailer`) is sent after the booking transaction commits, on a background thread, so mail problems can never break or slow a booking. Failures are logged (a retrying outbox would be the production upgrade). Configure SMTP with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`; set `MAIL_ENABLED=false` to turn emails off. Links point to `FRONTEND_URL`.

## Event lifecycle

```
            submit                 approve (admin)
  DRAFT ────────────▶ PENDING_APPROVAL ─────────────▶ PUBLISHED
   ▲  ▲                 │        │ reject (admin,        │ unpublish (admin, with reason)
   │  │                 │        │  with reason)         ▼  → REJECTED
   │  └──── withdraw ───┘        ▼                       │
   │                          REJECTED ── edit + submit ─┘ (back to PENDING_APPROVAL)
   │
 delete (drafts only)        cancel: any not-yet-ended event → CANCELLED (hidden publicly)
```

Organizers can edit details and ticket types only in `DRAFT` and `REJECTED`. Only `PUBLISHED` events are visible publicly.

## Frontend routes

| Route | Access |
|---|---|
| `/`, `/events`, `/events/:id`, `/login`, `/register` | Public |
| `/bookings`, `/bookings/:id` | Signed in |
| `/organizer/events/:id/check-in` | Event owner (or ADMIN) |
| `/account`, `/organizer/onboarding` | Signed in |
| `/organizer`, `/organizer/profile`, `/organizer/events/new`, `/organizer/events/:id` | ORGANIZER or ADMIN |
| `/admin`, `/admin/events`, `/admin/events/:id`, `/admin/users`, `/admin/organizers`, `/admin/activity` | ADMIN |

## Tests

```bash
cd backend && ./mvnw test        # MockMvc + Testcontainers PostgreSQL — Docker Desktop must be running
cd frontend && npm run lint && npm run build
```

## Configuration reference

| Variable               | Default                                       | Used by  |
| ---------------------- | --------------------------------------------- | -------- |
| `POSTGRES_DB/USER/PASSWORD` | `eventhub`                               | Docker Compose |
| `POSTGRES_PORT`        | `5433`                                        | Docker Compose (host port) |
| `DB_URL`               | `jdbc:postgresql://localhost:5433/eventhub`   | Backend  |
| `DB_USERNAME` / `DB_PASSWORD` | `eventhub`                             | Backend  |
| `SERVER_PORT`          | `8080`                                        | Backend  |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (comma-separated)     | Backend  |
| `JWT_SECRET`           | *(none — required)* Base64, ≥ 32 bytes         | Backend  |
| `JWT_ISSUER`           | `eventhub`                                    | Backend  |
| `APP_TIMEZONE`         | `Asia/Kolkata` (how date filters are interpreted) | Backend |
| `APP_SEED_DEMO_EVENTS` | `true`                                        | Backend (dev only, needs `APP_SEED_ENABLED`) |
| `MAIL_ENABLED`         | `true`                                        | Backend |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` (Mailpit)             | Backend |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty                              | Backend |
| `MAIL_FROM`            | `EventHub <no-reply@eventhub.local>`          | Backend |
| `FRONTEND_URL`         | `http://localhost:5173` (links in emails)     | Backend |
| `ACCESS_TOKEN_TTL` / `REFRESH_TOKEN_TTL` | `15m` / `14d`               | Backend  |
| `REFRESH_COOKIE_SECURE`| `true` (set `false` for http://localhost)     | Backend  |
| `REFRESH_COOKIE_SAME_SITE` | `Strict`                                  | Backend  |
| `APP_SEED_ENABLED`     | `false`                                       | Backend (dev only) |
| `DEV_ADMIN_EMAIL/PASSWORD`, `DEV_ORGANIZER_EMAIL/PASSWORD` | empty     | Backend (dev only) |
| `VITE_API_BASE_URL`    | empty (use dev proxy)                         | Frontend |
| `VITE_DEV_PROXY_TARGET`| `http://localhost:8080`                       | Frontend dev server |

`.env` is git-ignored. Never commit real secrets.

## Troubleshooting

- **`FATAL: role "eventhub" does not exist`** — the backend reached a different PostgreSQL (e.g. a native install on 5432). Check `DB_URL` points at port 5433.
- **`JWT_SECRET ... is not set`** on startup — add `JWT_SECRET` to `.env` (see step 1).
- **Logged out after every page reload in dev** — check `REFRESH_COOKIE_SECURE=false` in `.env`; browsers drop Secure cookies on plain http.
- **`Port 5173 is already in use`** — another Vite dev server is running; stop it or reuse it.
- **Reset the database:** `docker compose down -v && docker compose up -d`.

## Roadmap

1. ~~Project foundation~~ ✅
2. ~~Authentication and roles~~ ✅
3. ~~Event discovery~~ ✅
4. ~~Organizer event management~~ ✅
5. ~~Admin moderation~~ ✅
6. ~~Booking and inventory~~ ✅ (free tickets, no overbooking)
7. ~~QR check-in and notifications~~ ✅
8. AWS deployment
9. Payments
