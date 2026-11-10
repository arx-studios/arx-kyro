import type { Metadata } from 'next';
import { Suspense } from 'react';
import { VerifyEmail } from '@/features/authentication/VerifyEmail';

export const metadata: Metadata = { title: 'Verify your email' };

export default function VerifyEmailPage() {
  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-navy-900">Verify your email</h1>
      <Suspense fallback={null}>
        <VerifyEmail />
      </Suspense>
    </>
  );
}
