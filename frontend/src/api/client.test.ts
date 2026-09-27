import { describe, expect, it, vi } from 'vitest'
import { fakeServer, json, problem } from '../test/fakeServer'
import { ApiError, request } from './client'
import { createApi } from './endpoints'

describe('request', () => {
  it('sends JSON with the session cookie and the header that makes the cookie count', async () => {
    const { requests, fetchMock } = fakeServer({ 'POST /api/things': () => json({ ok: true }, 201) })

    const result = await request('/api/things', { method: 'POST', body: { a: 1 } })

    expect(result).toEqual({ ok: true })
    expect(requests[0].headers).toMatchObject({ 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' })
    expect(requests[0].headers).not.toHaveProperty('Authorization')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials: 'same-origin' })
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
    fakeServer({ 'GET /api/projects?status=ACTIVE&page=0&size=24': () => problem(401, 'Unauthorized', 'Valid credentials are required') })
    const onUnauthorized = vi.fn()

    await expect(createApi(onUnauthorized).projects.list('ACTIVE')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not log out on other errors', async () => {
    fakeServer({ 'GET /api/projects/p1': () => problem(404, 'Not Found', 'Project not found') })
    const onUnauthorized = vi.fn()

    await expect(createApi(onUnauthorized).projects.get('p1')).rejects.toMatchObject({ status: 404 })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
