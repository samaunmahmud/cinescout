import { describe, expect, it, vi } from 'vitest'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ApiError, basicAuthorization, request } from './client'
import { createApi } from './endpoints'

describe('basicAuthorization', () => {
  it('encodes the credentials as UTF-8, as the server expects', () => {
    const header = basicAuthorization({ email: 'zoë@example.com', password: 'pässwörd' })
    const decoded = new TextDecoder().decode(Uint8Array.from(atob(header.slice('Basic '.length)), (c) => c.charCodeAt(0)))
    expect(decoded).toBe('zoë@example.com:pässwörd')
  })
})

describe('request', () => {
  it('sends JSON with credentials and asks the server not to trigger the browser login dialog', async () => {
    const { requests } = fakeServer({ 'POST /api/things': () => json({ ok: true }, 201) })

    const result = await request('/api/things', { method: 'POST', body: { a: 1 }, credentials: { email: 'a@b.c', password: 'secret12' } })

    expect(result).toEqual({ ok: true })
    expect(requests[0].headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      Authorization: basicAuthorization({ email: 'a@b.c', password: 'secret12' }),
    })
    expect(requests[0].body).toEqual({ a: 1 })
  })

  it('returns nothing for 204 No Content', async () => {
    fakeServer({ 'DELETE /api/things/1': () => new Response(null, { status: 204 }) })
    await expect(request('/api/things/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('turns an RFC 9457 problem into an ApiError with its field errors', async () => {
    fakeServer({
      'POST /api/things': () =>
        problem(400, 'Validation failed', 'The request is invalid', { errors: [{ field: 'title', message: 'must not be blank' }] }),
    })

    const error: ApiError = await request('/api/things', { method: 'POST', body: {} }).then(
      () => { throw new Error('expected a failure') },
      (e: ApiError) => e,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, title: 'Validation failed', detail: 'The request is invalid' })
    expect(error.fieldErrors).toEqual([{ field: 'title', message: 'must not be blank' }])
  })

  it('copes with an error body that is not JSON', async () => {
    fakeServer({ 'GET /api/things': () => new Response('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }) })
    await expect(request('/api/things')).rejects.toMatchObject({ status: 502, title: 'Bad Gateway', detail: null })
  })

  it('reports an unreachable server as a retryable network error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(request('/api/things')).rejects.toMatchObject({ status: 0, retryable: true })
  })
})

describe('createApi', () => {
  it('reports a rejected login so the app can log out', async () => {
    fakeServer({ 'GET /api/projects': () => problem(401, 'Unauthorized', 'Valid credentials are required') })
    const onUnauthorized = vi.fn()

    await expect(createApi({ email: 'a@b.c', password: 'x' }, onUnauthorized).projects.list()).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not log out on other errors', async () => {
    fakeServer({ 'GET /api/projects/p1': () => problem(404, 'Not Found', 'Project not found') })
    const onUnauthorized = vi.fn()

    await expect(createApi({ email: 'a@b.c', password: 'x' }, onUnauthorized).projects.get('p1')).rejects.toMatchObject({ status: 404 })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
