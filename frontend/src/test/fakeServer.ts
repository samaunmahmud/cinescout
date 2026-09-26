import { afterEach, expect, vi } from 'vitest'

let unrouted: string[] = []

// The app turns a thrown fetch into a "network error" it can show, so an unrouted request would otherwise pass
// unnoticed. Fail the test instead.
afterEach(() => {
  const missed = unrouted
  unrouted = []
  expect(missed, 'requests the fake server had no route for').toEqual([])
})

export interface RecordedRequest {
  method: string
  path: string
  headers: Record<string, string>
  body: unknown
}

type Handler = (req: RecordedRequest) => Response | Promise<Response>

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

export const problem = (status: number, title: string, detail?: string, extra: Record<string, unknown> = {}) =>
  new Response(JSON.stringify({ type: 'about:blank', title, status, detail, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })

/**
 * Replaces fetch with routes keyed by "METHOD /path" (query string included). Every request is recorded, in
 * `authRequests` for `/api/auth/*` and in `requests` for everything else; an unrouted one fails the test.
 *
 * It also plays the session cookie: `GET /api/auth/me` answers 401 until a login succeeds (or from the start
 * with `loggedIn`), and then the test's own `GET /api/auth/me` route. Unless a test routes them itself,
 * `POST /api/auth/login` answers with that same route (so logging in yields the account `me` returns) and
 * `POST /api/auth/logout` ends the session.
 */
export function fakeServer(testRoutes: Record<string, Handler>, { loggedIn = false } = {}) {
  let session = loggedIn
  const noSession = () => problem(401, 'Unauthorized', 'Valid credentials are required')
  const routes: Record<string, Handler> = {
    'POST /api/auth/login': (req) => (testRoutes['GET /api/auth/me'] ?? noSession)(req),
    'POST /api/auth/logout': () => new Response(null, { status: 204 }),
    ...testRoutes,
  }
  const sessionRoutes: Record<string, Handler> = {
    'GET /api/auth/me': (req) => (session && testRoutes['GET /api/auth/me'] ? testRoutes['GET /api/auth/me'](req) : noSession()),
    'POST /api/auth/login': async (req) => {
      const response = await routes['POST /api/auth/login'](req)
      session = response.ok
      return response
    },
    'POST /api/auth/logout': (req) => {
      session = false
      return routes['POST /api/auth/logout'](req)
    },
  }
  const requests: RecordedRequest[] = []
  const authRequests: RecordedRequest[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost')
    const req: RecordedRequest = {
      method: init.method ?? 'GET',
      path: url.pathname + url.search,
      headers: Object.fromEntries(Object.entries((init.headers ?? {}) as Record<string, string>)),
      body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
    }
    ;(req.path.startsWith('/api/auth/') ? authRequests : requests).push(req)
    const key = `${req.method} ${req.path}`
    const handler = sessionRoutes[key] ?? routes[key]
    if (!handler) {
      unrouted.push(`${req.method} ${req.path}`)
      throw new Error(`Unexpected request: ${req.method} ${req.path}`)
    }
    return handler(req)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { requests, authRequests, fetchMock }
}
