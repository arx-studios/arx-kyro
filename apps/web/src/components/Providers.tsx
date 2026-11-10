'use client';

import type { ReactNode } from 'react';
import { QueryProvider } from '@/lib/query';

/** All client-side providers. Add new ones here so every route gets them. */
export function Providers({ children }: { children: ReactNode }) {
  return <QueryProvider>{children}</QueryProvider>;
}
