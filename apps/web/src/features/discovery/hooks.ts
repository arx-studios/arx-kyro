'use client';

import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query';
import {
  unwrap,
  type FeedPage,
  type PublicRequestDetail,
  type SavedPage,
  type SwipeAction,
} from '@kyro/api-client';
import { api } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';

const PAGE_SIZE = 20;

export function useFeed() {
  return useInfiniteQuery({
    queryKey: queryKeys.discovery.feed,
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }): Promise<FeedPage> =>
      unwrap(
        await api.GET('/api/v1/discovery/feed', {
          params: { query: { cursor: pageParam, limit: PAGE_SIZE } },
        }),
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}

export function usePublicRequest(id: string) {
  return useQuery({
    queryKey: queryKeys.discovery.detail(id),
    retry: false,
    queryFn: async (): Promise<PublicRequestDetail> =>
      unwrap(
        await api.GET('/api/v1/discovery/feed/{requestId}', { params: { path: { requestId: id } } }),
      ),
  });
}

export async function postSwipe(requestId: string, action: SwipeAction) {
  return unwrap(
    await api.POST('/api/v1/discovery/requests/{requestId}/swipes', {
      params: { path: { requestId } },
      body: { action },
    }),
  );
}

/** Swipe mutation. Callers do their own optimistic UI; this keeps detail and saved caches fresh. */
export function useSwipe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, action }: { requestId: string; action: SwipeAction }) =>
      postSwipe(requestId, action),
    onSuccess: (_res, { requestId, action }) => {
      qc.setQueryData<PublicRequestDetail>(queryKeys.discovery.detail(requestId), (old) =>
        old ? { ...old, viewerAction: action } : old,
      );
      return qc.invalidateQueries({ queryKey: queryKeys.discovery.saved });
    },
  });
}

export function useSaved() {
  return useInfiniteQuery({
    queryKey: queryKeys.discovery.saved,
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }): Promise<SavedPage> =>
      unwrap(
        await api.GET('/api/v1/discovery/saved', {
          params: { query: { cursor: pageParam, limit: PAGE_SIZE } },
        }),
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}

/** Removes a saved item with optimistic removal and rollback. */
export function useRemoveSaved() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (requestId: string) => {
      unwrap(
        await api.DELETE('/api/v1/discovery/saved/{requestId}', { params: { path: { requestId } } }),
      );
    },
    onMutate: async (requestId) => {
      await qc.cancelQueries({ queryKey: queryKeys.discovery.saved });
      const previous = qc.getQueryData<InfiniteData<SavedPage>>(queryKeys.discovery.saved);
      qc.setQueryData<InfiniteData<SavedPage>>(queryKeys.discovery.saved, (old) =>
        old
          ? {
              ...old,
              pages: old.pages.map((p) => ({
                ...p,
                items: p.items.filter((i) => i.request.id !== requestId),
              })),
            }
          : old,
      );
      return { previous };
    },
    onError: (_e, _id, ctx) => {
      if (ctx?.previous) qc.setQueryData(queryKeys.discovery.saved, ctx.previous);
    },
    onSuccess: (_r, requestId) => {
      qc.setQueryData<PublicRequestDetail>(queryKeys.discovery.detail(requestId), (old) =>
        old ? { ...old, viewerAction: null } : old,
      );
      // The removed request may reappear in the feed.
      return qc.invalidateQueries({ queryKey: queryKeys.discovery.feed });
    },
  });
}
