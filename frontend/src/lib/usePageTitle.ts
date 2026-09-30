import { useEffect } from 'react'

const APP = 'CineScout'

/**
 * Names the browser tab after the page: "Night Shift · CineScout". Pass null while the page's own name is
 * still loading, and the tab just says CineScout. The name is put back when the page is left.
 */
export function usePageTitle(title: string | null | undefined) {
  useEffect(() => {
    document.title = title ? `${title} · ${APP}` : APP
    return () => {
      document.title = APP
    }
  }, [title])
}
