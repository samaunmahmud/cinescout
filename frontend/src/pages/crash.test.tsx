import { render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '../routes'
import { CrashPage } from './CrashPage'

function Broken({ error }: { error: Error }): never {
  throw error
}

function renderBroken(error: Error) {
  const router = createMemoryRouter([{ path: '/', element: <Broken error={error} />, errorElement: <CrashPage /> }])
  render(<RouterProvider router={router} />)
}

describe('a page that fails to render', () => {
  // React reports the error on the console as well; that is expected here.
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
  afterEach(() => consoleError.mockClear())

  it('offers a reload and a way back, and never shows the error itself', () => {
    renderBroken(new Error('SECRET internal detail'))

    expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to your projects' })).toHaveAttribute('href', '/projects')
    expect(document.body).not.toHaveTextContent('SECRET')
    expect(screen.getByText(/could not be shown/)).toBeInTheDocument()
  })

  it('says a reload fetches the new version when the page’s code is gone after a deploy', () => {
    renderBroken(new TypeError('Failed to fetch dynamically imported module: /assets/LeafletMap-abc.js'))

    expect(screen.getByText(/has been updated since this page was opened/)).toBeInTheDocument()
  })

  it('guards every route of the app, pages inside the frame so its header stays', () => {
    expect(routes.every((route) => route.errorElement !== undefined)).toBe(true)
    const frame = routes.find((route) => route.children)!
    expect(frame.children).toHaveLength(1)
    expect(frame.children![0].errorElement).toBeDefined()
    expect(frame.children![0].children!.length).toBeGreaterThan(5)
  })
})
