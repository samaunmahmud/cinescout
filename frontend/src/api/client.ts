// A small fetch wrapper for the CineScout API: JSON in and out, the session cookie, and RFC 9457 problem
// responses turned into ApiError.

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
  signal?: AbortSignal
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, 'application/json, application/problem+json', options)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** A file the server makes on request (an export, say), with the name it suggests for it. */
export interface DownloadedFile {
  blob: Blob
  /** From the Content-Disposition header; null when the server names none. */
  filename: string | null
}

/** Fetches a file as `request` fetches JSON: with the session, and failures as ApiError. */
export async function download(path: string, accept: string): Promise<DownloadedFile> {
  const response = await send(path, `${accept}, application/problem+json`)
  const named = /filename="([^"]+)"/.exec(response.headers.get('Content-Disposition') ?? '')
  return { blob: await response.blob(), filename: named ? named[1] : null }
}

async function send(path: string, accept: string, options: RequestOptions = {}): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: accept,
    // The session cookie only counts together with this header (the server's CSRF defence), and it tells the
    // server not to send WWW-Authenticate on a 401, which would open the browser's own login dialog.
    'X-Requested-With': 'XMLHttpRequest',
  }
  // A form (a file upload) sets its own multipart Content-Type, boundary included.
  const form = options.body instanceof FormData
  if (options.body !== undefined && !form) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers,
      // The HttpOnly session cookie: sent to this origin only, never readable by scripts.
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : form ? (options.body as FormData) : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'Network error', 'Could not reach the CineScout server.', [], true)
  }

  if (!response.ok) throw await toApiError(response)
  return response
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
