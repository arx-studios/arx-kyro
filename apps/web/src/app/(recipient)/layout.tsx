'use client';

import type { ReactNode } from 'react';
import { RequireAuth } from '@/components/RequireAuth';

export default function RecipientLayout({ children }: { children: ReactNode }) {
  return (
    <RequireAuth roles={['RECIPIENT']}>
      <div className="mx-auto w-full max-w-3xl px-4 py-8">{children}</div>
    </RequireAuth>
  );
}
