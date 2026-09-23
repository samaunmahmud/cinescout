// A small fetch wrapper for the CineScout API: JSON in and out, HTTP Basic credentials, and RFC 9457
// problem responses turned into ApiError.

export interface Credentials {
  email: string
  password: string
}

/** One entry of a validation problem's `errors` list. */
export interface FieldProblem {
  field: string
  message: string
}

/** A failed request. `detail` is the server's safe, human-readable explanation when there is one. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string | null
  readonly fieldErrors: FieldProblem[]
  readonly retryable: boolean

  constructor(status: number, title: string, detail: string | null, fieldErrors: FieldProblem[] = [], retryable = false) {
    super(detail ?? title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.fieldErrors = fieldErrors
    this.retryable = retryable
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  credentials?: Credentials | null
  signal?: AbortSignal
}

/** Basic credentials are UTF-8 (the server's challenge says charset="UTF-8"); btoa alone only handles Latin-1. */
export function basicAuthorization({ email, password }: Credentials): string {
  const bytes = new TextEncoder().encode(`${email}:${password}`)
  let binary = ''
  bytes.forEach((b) => (binary += String.fromCharCode(b)))
  return `Basic ${btoa(binary)}`
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json, application/problem+json',
    // Tells the server not to send WWW-Authenticate on a 401, which would open the browser's own login dialog.
    'X-Requested-With': 'XMLHttpRequest',
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.credentials) headers.Authorization = basicAuthorization(options.credentials)

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'Network error', 'Could not reach the CineScout server.', [], true)
  }

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {}
  try {
    body = await response.json()
  } catch {
    // Not JSON (a proxy error page, say): fall back to the status line.
  }
  const title = typeof body.title === 'string' ? body.title : response.statusText || 'Request failed'
  const detail = typeof body.detail === 'string' ? body.detail : null
  const errors = Array.isArray(body.errors) ? (body.errors as FieldProblem[]) : []
  return new ApiError(response.status, title, detail, errors, body.retryable === true)
}
