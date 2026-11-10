import type { Metadata } from 'next';
import { Suspense } from 'react';
import { MfaGate } from '@/features/mfa/MfaGate';
import { MfaVerifyForm } from '@/features/mfa/MfaVerifyForm';

export const metadata: Metadata = { title: 'Verify two-step code' };

export default function MfaVerifyPage() {
  return (
    <MfaGate step="verify" title="Two-step verification">
      <Suspense fallback={null}>
        <MfaVerifyForm />
      </Suspense>
    </MfaGate>
  );
}
