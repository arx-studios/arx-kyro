'use client';

import { useQuery } from '@tanstack/react-query';
import { unwrap, type CurrentUser } from '@kyro/api-client';
import { api } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';

export async function fetchMe(): Promise<CurrentUser | null> {
  const res = await api.GET('/api/v1/me');
  if (res.response.status === 401) return null;
  return unwrap(res);
}

/** Current user; a 401 maps to `user: null`. */
export function useMe(): { user: CurrentUser | null; isLoading: boolean; isError: boolean } {
  const q = useQuery({
    queryKey: queryKeys.me,
    queryFn: fetchMe,
    retry: false,
    staleTime: 60_000,
  });
  return { user: q.data ?? null, isLoading: q.isPending, isError: q.isError };
}
