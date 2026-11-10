'use client';

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { unwrap, type FundingSummary, type Pledge, type PledgePage } from '@kyro/api-client';
import { api } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';

const PAGE_SIZE = 20;

export function useFundingSummary(requestId: string) {
  return useQuery({
    queryKey: queryKeys.funding.summary(requestId),
    retry: false,
    queryFn: async (): Promise<FundingSummary> =>
      unwrap(await api.GET('/api/v1/funding/requests/{requestId}/summary', { params: { path: { requestId } } })),
  });
}

export async function postPledge(args: {
  requestId: string;
  amountMinor: number;
  currency: string;
  idempotencyKey: string;
}): Promise<Pledge> {
  return unwrap(
    await api.POST('/api/v1/funding/requests/{requestId}/pledges', {
      params: { path: { requestId: args.requestId }, header: { 'Idempotency-Key': args.idempotencyKey } },
      body: { amount: { amountMinor: args.amountMinor, currency: args.currency }, acknowledgeNonBinding: true },
    }),
  );
}

function useInvalidateFunding() {
  const qc = useQueryClient();
  return (requestId: string | undefined) =>
    Promise.all([
      requestId ? qc.invalidateQueries({ queryKey: queryKeys.funding.summary(requestId) }) : undefined,
      requestId ? qc.invalidateQueries({ queryKey: queryKeys.discovery.detail(requestId) }) : undefined,
      qc.invalidateQueries({ queryKey: queryKeys.funding.myPledges }),
      qc.invalidateQueries({ queryKey: queryKeys.discovery.feed }),
    ]);
}

/** Creates a pledge. The caller owns the Idempotency-Key so a retry reuses it. */
export function useCreatePledge() {
  const invalidate = useInvalidateFunding();
  return useMutation({
    mutationFn: postPledge,
    // Also refresh after a failure: 409/422 mean the server state differs from what the donor saw.
    onSettled: (_d, _e, vars) => invalidate(vars.requestId),
  });
}

export function useCancelPledge() {
  const invalidate = useInvalidateFunding();
  return useMutation({
    mutationFn: async (args: { pledgeId: string; requestId?: string }): Promise<Pledge> =>
      unwrap(
        await api.POST('/api/v1/funding/pledges/{pledgeId}/cancel', {
          params: { path: { pledgeId: args.pledgeId } },
        }),
      ),
    onSettled: (_d, _e, vars) => invalidate(vars.requestId),
  });
}

export function useMyPledges() {
  return useInfiniteQuery({
    queryKey: queryKeys.funding.myPledges,
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }): Promise<PledgePage> =>
      unwrap(
        await api.GET('/api/v1/me/funding/pledges', {
          params: { query: { cursor: pageParam, limit: PAGE_SIZE } },
        }),
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}
