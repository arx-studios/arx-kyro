import type { Metadata } from 'next';
import { Suspense } from 'react';
import { MfaGate } from '@/features/mfa/MfaGate';
import { MfaSetup } from '@/features/mfa/MfaSetup';

export const metadata: Metadata = { title: 'Set up two-step verification' };

export default function MfaSetupPage() {
  return (
    <MfaGate step="setup" title="Set up two-step verification">
      <Suspense fallback={null}>
        <MfaSetup />
      </Suspense>
    </MfaGate>
  );
}
