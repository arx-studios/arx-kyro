'use client';

import { useState, type ReactNode } from 'react';
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { mfaTargetForError } from '@/lib/mfa';

/** A 403 MFA_REQUIRED / MFA_ENROLLMENT_REQUIRED from any call sends the user to the matching step. */
function redirectOnMfaError(error: unknown) {
  const target = mfaTargetForError(error);
  if (!target || typeof window === 'undefined') return;
  if (window.location.pathname.startsWith('/mfa/')) return;
  const next = encodeURIComponent(window.location.pathname + window.location.search);
  window.location.assign(`${target}?next=${next}`);
}

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        queryCache: new QueryCache({ onError: redirectOnMfaError }),
        mutationCache: new MutationCache({ onError: redirectOnMfaError }),
        defaultOptions: {
          // Private review data: keep nothing around once a view unmounts.
          queries: { refetchOnWindowFocus: false, gcTime: 0, retry: false },
          mutations: { gcTime: 0 },
        },
      }),
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
