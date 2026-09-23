import { vi } from 'vitest'

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
 * Replaces fetch with routes keyed by "METHOD /path" (query string included). Every request is recorded;
 * an unrouted one fails the test loudly instead of hanging.
 */
export function fakeServer(routes: Record<string, Handler>) {
  const requests: RecordedRequest[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost')
    const req: RecordedRequest = {
      method: init.method ?? 'GET',
      path: url.pathname + url.search,
      headers: Object.fromEntries(Object.entries((init.headers ?? {}) as Record<string, string>)),
      body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
    }
    requests.push(req)
    const handler = routes[`${req.method} ${req.path}`]
    if (!handler) throw new Error(`Unexpected request: ${req.method} ${req.path}`)
    return handler(req)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { requests, fetchMock }
}
