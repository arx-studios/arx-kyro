'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

/** Ends the session, then drops every cached (possibly private) query. */
export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      // Best effort: even if the call fails (e.g. session already gone) we leave the console.
      await api.DELETE('/api/v1/auth/session').catch(() => undefined);
    },
    onSettled: () => {
      queryClient.clear();
      window.location.assign('/login');
    },
  });
}
