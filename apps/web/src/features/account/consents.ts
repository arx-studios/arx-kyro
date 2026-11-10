'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { unwrap, type ConsentStatus } from '@kyro/api-client';
import { api } from '@/lib/api';

/** Local query key (lib/queryKeys.ts belongs to WEB-3 in Phase 5). */
export const consentStatusKey = ['account', 'consents'] as const;

export function useConsentStatus(enabled: boolean) {
  return useQuery({
    queryKey: consentStatusKey,
    enabled,
    retry: false,
    staleTime: 60_000,
    queryFn: async () => unwrap(await api.GET('/api/v1/me/consents')),
  });
}

/** Accepts exactly the current versions the server reported (it rejects any other with CONSENT_VERSION_MISMATCH). */
export function useAcceptCurrentPolicies() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (status: ConsentStatus) =>
      unwrap(
        await api.POST('/api/v1/me/consents', {
          body: { termsVersion: status.current.termsVersion, privacyVersion: status.current.privacyVersion },
        }),
      ),
    onSuccess: (updated) => {
      qc.setQueryData(consentStatusKey, updated);
    },
  });
}
