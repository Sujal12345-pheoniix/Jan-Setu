import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api/client';

/** Retry only transient failures (network, 5xx); never 4xx, which are deterministic. Mutations never retry. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (error instanceof ApiError && (error.isNetwork || error.status >= 500)) return failureCount < 2;
          return false;
        },
      },
      mutations: { retry: false },
    },
  });
}
