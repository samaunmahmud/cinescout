import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './client'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Retrying a 4xx never helps; retry network blips and 5xx once.
        retry: (count, error) => count < 1 && (!(error instanceof ApiError) || error.status === 0 || error.status >= 500),
      },
    },
  })
}
