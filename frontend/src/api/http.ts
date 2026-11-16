import type { AuthResponse } from './types'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const REFRESH_PATH = '/api/v1/auth/refresh'

/** RFC 9457 problem details returned by the backend. */
export interface ProblemDetail {
  status?: number
  title?: string
  detail?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, message: string, body: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }

  get problem(): ProblemDetail | undefined {
    return typeof this.body === 'object' && this.body !== null ? (this.body as ProblemDetail) : undefined
  }

  /** Field -> message map for validation errors. */
  get fieldErrors(): Record<string, string> {
    return this.problem?.errors ?? {}
  }
}

/** Best human-readable message for any thrown value. */
export function errorMessage(err: unknown, fallback = 'Something went wrong'): string {
  if (err instanceof ApiError) return err.problem?.detail ?? fallback
  if (err instanceof TypeError) return 'Cannot reach the server'
  return fallback
}

// ---------------------------------------------------------------------------
// Session state. The access token lives only in memory (never localStorage),
// so XSS cannot read a long-lived credential. The refresh token is an
// HttpOnly cookie managed by the browser.
// ---------------------------------------------------------------------------

let accessToken: string | null = null
type SessionListener = (session: AuthResponse | null) => void
const listeners = new Set<SessionListener>()

export function setSession(session: AuthResponse | null): void {
  accessToken = session?.accessToken ?? null
  listeners.forEach((l) => l(session))
}

export function onSessionChange(listener: SessionListener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

let refreshInFlight: Promise<AuthResponse | null> | null = null

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers
 * share one request, because the backend rotates the refresh token on use.
 * Resolves to null when there is no valid session.
 */
export function refreshSession(): Promise<AuthResponse | null> {
  refreshInFlight ??= (async () => {
    try {
      const res = await fetch(`${API_BASE_URL}${REFRESH_PATH}`, {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'application/json' },
      })
      const session = res.ok ? ((await res.json()) as AuthResponse) : null
      setSession(session)
      return session
    } catch {
      // Network failure: keep current state, report no new session.
      return null
    } finally {
      refreshInFlight = null
    }
  })()
  return refreshInFlight
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

async function send(path: string, opts: RequestOptions): Promise<Response> {
  const headers = new Headers(opts.headers)
  headers.set('Accept', 'application/json')
  let body: BodyInit | undefined
  if (opts.body !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(opts.body)
  }
  // Never send a (possibly expired) bearer token to the auth endpoints.
  if (accessToken && !path.startsWith('/api/v1/auth/')) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return fetch(`${API_BASE_URL}${path}`, { ...opts, headers, body, credentials: 'include' })
}

async function parse(res: Response): Promise<unknown> {
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

/** JSON request helper. On 401 it refreshes the session once and retries. */
export async function http<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const hadToken = accessToken !== null
  let res = await send(path, opts)

  if (res.status === 401 && hadToken && !path.startsWith('/api/v1/auth/')) {
    const session = await refreshSession()
    if (session) res = await send(path, opts)
  }

  const body = await parse(res)
  if (!res.ok) {
    const detail = (body as ProblemDetail | null)?.detail
    throw new ApiError(res.status, detail ?? `Request failed: ${res.status}`, body)
  }
  return body as T
}
