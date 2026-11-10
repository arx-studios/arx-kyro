'use client';

import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@kyro/api-client';
import { api } from '@/lib/api';

/** Local query key (features/funding belongs to WEB-3; lib/queryKeys.ts is not edited by WEB-4). */
export const recipientFundingKey = (requestId: string) => ['education', 'funding-summary', requestId] as const;

export function useFundingSummary(requestId: string, enabled: boolean) {
  return useQuery({
    queryKey: recipientFundingKey(requestId),
    enabled,
    queryFn: async () =>
      unwrap(await api.GET('/api/v1/funding/requests/{requestId}/summary', { params: { path: { requestId } } })),
  });
}
