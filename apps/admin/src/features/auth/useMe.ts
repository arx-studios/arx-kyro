'use client';

import { useQuery } from '@tanstack/react-query';
import { ApiError, unwrap, type CurrentUser } from '@kyro/api-client';
import { api } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';

export async function fetchMe(): Promise<CurrentUser | null> {
  try {
    return unwrap(await api.GET('/api/v1/me'));
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}

/** `user` is null when signed out (401). */
export function useMe() {
  const query = useQuery({ queryKey: queryKeys.me, queryFn: fetchMe, retry: false, staleTime: 30_000 });
  return {
    user: query.data ?? null,
    isLoading: query.isPending,
    error: query.error,
    refetch: query.refetch,
  };
}
